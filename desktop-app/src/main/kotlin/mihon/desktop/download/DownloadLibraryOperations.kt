package mihon.desktop.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryMutationPort
import java.nio.file.Files

/** Downloaded-chapter queries and deletions that keep the queue and offline database in sync. */
internal class DownloadLibraryOperations(
    private val diskProvider: DownloadDiskProvider,
    private val mutationPort: LibraryMutationPort?,
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val persistence: DownloadQueuePersistence,
    private val onError: (String) -> Unit,
) {

    fun isChapterDownloaded(
        sourceId: Long,
        mangaTitle: String,
        chapterId: Long,
        chapterName: String,
        mangaId: Long? = null,
    ): Boolean {
        val queued = queueState.value.firstOrNull { it.chapterId == chapterId }
        if (queued != null && queued.status != DownloadStatus.COMPLETED) return false
        return if (queued != null) {
            diskProvider.isChapterDownloaded(
                queued.sourceId,
                queued.mangaTitle,
                queued.chapterName,
                queued.mangaId,
                queued.chapterId,
            )
        } else {
            diskProvider.isChapterDownloaded(
                sourceId,
                mangaTitle,
                chapterName,
                mangaId,
                chapterId.takeIf {
                    mangaId !=
                        null
                },
            )
        }
    }

    fun deleteDownloadedChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val saved = queueState.value.firstOrNull {
            it.sourceId == sourceId && it.mangaTitle == mangaTitle && it.chapterName == chapterName
        }
        return deleteDownloadedChapter(
            sourceId = sourceId,
            mangaId = saved?.mangaId,
            mangaTitle = mangaTitle,
            chapterId = saved?.chapterId,
            chapterName = chapterName,
        )
    }

    fun deleteDownloadedChapter(
        sourceId: Long,
        mangaId: Long?,
        mangaTitle: String,
        chapterId: Long?,
        chapterName: String,
    ): Boolean {
        val saved = queueState.value.firstOrNull { item ->
            if (chapterId != null) {
                item.chapterId == chapterId
            } else {
                item.sourceId == sourceId && item.mangaTitle == mangaTitle && item.chapterName == chapterName
            }
        }
        val effectiveSourceId = saved?.sourceId ?: sourceId
        val effectiveMangaId = saved?.mangaId ?: mangaId
        val effectiveMangaTitle = saved?.mangaTitle ?: mangaTitle
        val effectiveChapterId = saved?.chapterId ?: chapterId
        val effectiveChapterName = saved?.chapterName ?: chapterName
        // Resolve the published path while the database registration still exists: a registered
        // download may live outside the title/id layout the provider would derive afterwards.
        val chapterEntry = diskProvider.findChapterEntry(
            effectiveSourceId,
            effectiveMangaTitle,
            effectiveChapterName,
            effectiveMangaId,
            effectiveChapterId,
        )
        val existed = chapterEntry != null && Files.exists(chapterEntry.path)
        // Remove the database registration first: failing that must leave the published files intact,
        // otherwise the queue would still claim a download that can no longer be read.
        if (effectiveMangaId != null && effectiveChapterId != null) {
            try {
                mutationPort?.deleteLocalChapterAsset(effectiveMangaId, effectiveChapterId)
            } catch (error: Exception) {
                onError("下载记录无法清理：${error.message}")
                return false
            }
        }
        if (existed && (chapterEntry == null || !diskProvider.deleteChapterEntry(chapterEntry))) {
            onError("下载文件无法清理：$effectiveChapterName")
            return false
        }
        var removed = false
        queueState.update { queue ->
            queue.filterNot { item ->
                val matches = if (effectiveChapterId != null) {
                    item.chapterId == effectiveChapterId
                } else {
                    item.sourceId == effectiveSourceId && item.mangaTitle == effectiveMangaTitle &&
                        item.chapterName == effectiveChapterName
                }
                (matches && item.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)).also {
                    if (it) removed = true
                }
            }
        }
        if (removed) persistence.persist()
        return existed || removed || (effectiveMangaId != null && effectiveChapterId != null)
    }

    fun deleteDownloadedChapter(manga: LibraryManga, chapter: LibraryChapter): Boolean =
        deleteDownloadedChapter(manga.sourceId, manga.id, manga.title, chapter.id, chapter.name)

    fun deleteDownloadedChapter(manga: MangaDetails, chapter: LibraryChapter): Boolean =
        deleteDownloadedChapter(manga.sourceId, manga.id, manga.title, chapter.id, chapter.name)
}
