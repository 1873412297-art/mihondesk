package mihon.desktop.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.extension.OnlineChapterSource
import mihon.desktop.extension.OnlineFetchPriority
import mihon.desktop.extension.OnlineImageCache
import mihon.desktop.extension.OnlinePageCacheFiles
import mihon.desktop.logging.DesktopLogger
import mihon.extension.ipc.RequestPriority
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Downloads neighboring chapters onto disk while a manga stays open.
 * Jobs live on the application scope, so closing the current chapter does not cancel them.
 */
class MangaOnlineCache(
    private val root: File,
    private val scope: CoroutineScope,
    private val catalog: ReaderChapterCatalog,
    private val sourceFactory: ChapterSourceFactory,
    private val pageListCache: mihon.desktop.extension.OnlinePageListCache =
        mihon.desktop.extension.OnlinePageListCache(),
    private val maxBytes: Long = OnlinePageCacheFiles.MAX_MANGA_BYTES,
) {
    private data class Window(
        val mangaId: Long,
        val current: Long,
        val previous: Long?,
        val next: Long?,
    ) {
        val ids: Set<Long> = setOfNotNull(current, previous, next)
    }

    private val windows = ConcurrentHashMap<Long, Window>()
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val tailOnly = ConcurrentHashMap<Long, Boolean>()
    private val seenAssets = ConcurrentHashMap<Long, ReaderChapterAsset>()

    init {
        OnlinePageCacheFiles.sweep(root)
    }

    fun chapterOpened(chapterId: Long) {
        val current = catalog.chapterAsset(chapterId) ?: return
        val next = catalog.adjacentReadableChapter(chapterId, ChapterDirection.NEXT)
        val previous = catalog.adjacentReadableChapter(chapterId, ChapterDirection.PREVIOUS)
        listOfNotNull(current, next, previous).forEach { seenAssets[it.chapterId] = it }
        val window = Window(current.mangaId, current.chapterId, previous?.chapterId, next?.chapterId)
        val prior = windows.put(current.mangaId, window)
        if (prior != null) {
            (prior.ids - window.ids).forEach { dropped ->
                tailOnly.remove(dropped)
                val job = jobs.remove(dropped)
                val droppedAsset = seenAssets.remove(dropped)
                job?.cancel()
                if (droppedAsset != null) {
                    scope.launch {
                        withContext(NonCancellable) {
                            job?.join()
                            deleteChapterFiles(droppedAsset)
                        }
                    }
                }
            }
        }
        next?.let { startPrefetch(it, tail = false) }
        previous?.let { startPrefetch(it, tail = true) }
    }

    suspend fun clear(mangaId: Long) {
        val ids = windows.remove(mangaId)?.ids.orEmpty()
        val running = ids.mapNotNull { chapterId ->
            tailOnly.remove(chapterId)
            seenAssets.remove(chapterId)
            jobs.remove(chapterId)
        }
        running.forEach { it.cancel() }
        withContext(NonCancellable) {
            running.forEach { job -> runCatching { job.join() } }
            OnlinePageCacheFiles.mangaDirectory(root, mangaId).deleteRecursively()
            pageListCache.discardManga(mangaId)
        }
    }

    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        tailOnly.clear()
        val mangaIds = windows.keys.toList()
        windows.clear()
        seenAssets.clear()
        OnlinePageCacheFiles.sweep(root)
        mangaIds.forEach(pageListCache::discardManga)
    }

    private fun startPrefetch(asset: ReaderChapterAsset, tail: Boolean) {
        if (asset.assetKind != ONLINE_CHAPTER_ASSET_KIND) return
        val active = jobs[asset.chapterId]
        val activeTail = tailOnly[asset.chapterId]
        if (active?.isActive == true) {
            // A full-chapter job already covers a later tail request. A tail job does not.
            if (activeTail == false || activeTail == tail) return
            active.cancel()
        }
        tailOnly[asset.chapterId] = tail
        jobs[asset.chapterId] = scope.launch(OnlineFetchPriority(RequestPriority.BACKGROUND)) {
            try {
                runPrefetch(asset, tail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DesktopLogger.warn(
                    "MangaOnlineCache",
                    "Stopped prefetching chapter ${asset.chapterId}",
                    error,
                )
            }
        }
    }

    private suspend fun runPrefetch(asset: ReaderChapterAsset, tail: Boolean) {
        val source = try {
            sourceFactory.create(asset)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        try {
            val pages = source.pages()
            val targets = if (tail) {
                pages.takeLast(OnlinePageCacheFiles.PREVIOUS_TAIL_PAGES).asReversed()
            } else {
                pages
            }
            targets.forEachIndexed { index, page ->
                coroutineContext.ensureActive()
                val window = windows[asset.mangaId] ?: return
                if (asset.chapterId !in window.ids) return
                trim(window)
                val budgetDue = index % BUDGET_CHECK_INTERVAL_PAGES == 0
                if (budgetDue && asset.chapterId == window.next && overBudget(asset.mangaId)) return
                source.open(page.id).close()
            }
        } finally {
            source.close()
        }
    }

    private fun trim(window: Window) {
        val previousId = window.previous ?: return
        val asset = seenAssets[previousId] ?: return
        if (asset.assetKind != ONLINE_CHAPTER_ASSET_KIND) return
        val source = runCatching { sourceFactory.create(asset) }.getOrNull() ?: return
        val cache = source as? OnlineImageCache
        if (cache == null) {
            source.close()
            return
        }
        try {
            val dir = OnlinePageCacheFiles.mangaDirectory(root, window.mangaId)
            var cachedBytes = OnlinePageCacheFiles.directoryBytes(dir)
            if (cachedBytes <= maxBytes) return
            val candidates = cache.cachedImageFiles()
                .sortedBy { file ->
                    file.name.substringAfterLast('_').substringBefore('.').toIntOrNull() ?: 0
                }
                .dropLast(OnlinePageCacheFiles.PREVIOUS_TAIL_PAGES)
                .iterator()
            while (cachedBytes > maxBytes && candidates.hasNext()) {
                val victim = candidates.next()
                val victimBytes = victim.length()
                if (victim.delete()) cachedBytes -= victimBytes
            }
        } finally {
            source.close()
        }
    }

    private fun overBudget(mangaId: Long): Boolean =
        OnlinePageCacheFiles.directoryBytes(OnlinePageCacheFiles.mangaDirectory(root, mangaId)) > maxBytes

    private fun deleteChapterFiles(asset: ReaderChapterAsset) {
        if (asset.assetKind != ONLINE_CHAPTER_ASSET_KIND) return
        val source = runCatching { sourceFactory.create(asset) }.getOrNull() ?: return
        try {
            (source as? OnlineImageCache)?.deleteCachedImages()
        } finally {
            source.close()
        }
    }
}

/** Budget directory walks are expensive; checking every page defeated the prefetch loop. */
private const val BUDGET_CHECK_INTERVAL_PAGES = 4
