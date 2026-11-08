package mihon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.extension.source.model.Page
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DesktopDownloader(
    val store: DownloadStore,
    val diskProvider: DownloadDiskProvider,
    val networkHelper: DesktopNetworkHelper,
    val processManager: WindowsExtensionProcessManager? = null,
    val sourceManager: mihon.desktop.extension.DesktopSourceManager? = null,
    val mutationPort: LibraryMutationPort? = null,
    val pageListFetcher: (suspend (sourceId: Long, chapterUrl: String) -> List<Page>)? = null,
    val downloadParallelism: () -> Int = { 1 },
    val pageParallelism: () -> Int = { 1 },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val onDownloadCompleted: ((DesktopDownload) -> Unit)? = null,
    val onDownloadFailed: ((DesktopDownload, String) -> Unit)? = null,
    val onDownloadProgress: ((DesktopDownload) -> Unit)? = null,
    val sourceParallelism: () -> Int = { 1 },
    val tallImageSplitter: TallImageSplitter? = null,
) : AutoCloseable {
    private val _queueState = MutableStateFlow<List<DesktopDownload>>(emptyList())
    val queueState: StateFlow<List<DesktopDownload>> = _queueState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _speedBytesPerSec = MutableStateFlow(0.0)
    val speedBytesPerSec: StateFlow<Double> = _speedBytesPerSec.asStateFlow()

    private val queueMutex = Mutex()
    private val persistenceLock = Any()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()
    private var lastClaimedSource: Long? = null

    private val persistence = DownloadQueuePersistence(
        store = store,
        scope = scope,
        queue = { _queueState.value },
        onSaved = { _storageError.value = null },
        onFailure = ::onPersistFailure,
    )

    private val editor = DownloadQueueEditor(_queueState, persistence, ::start, ::pause, ::resume, ::cancel)

    private fun onPersistFailure(error: Exception) {
        _storageError.value = "下载队列无法保存，已停止调度：${error.message}"
        if (::control.isInitialized) control.stopScheduling()
    }

    private lateinit var control: DownloadControl

    private val activeDownloadJobs = ConcurrentHashMap<Long, Job>()
    private val pendingCleanupJobs = ConcurrentHashMap<Long, Job>()
    private val sessionBytes = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)

    private val engine = DownloadEngine(
        diskProvider = diskProvider,
        networkHelper = networkHelper,
        sourceManager = sourceManager,
        processManager = processManager,
        mutationPort = mutationPort,
        pageListFetcher = pageListFetcher,
        pageParallelism = pageParallelism,
        tallImageSplitter = tallImageSplitter,
        queueState = _queueState,
        persistence = persistence,
        sessionBytes = sessionBytes,
        sessionStartedAt = { control.sessionStartedAtMillis() },
        onSpeed = { _speedBytesPerSec.value = it },
        onCompleted = onDownloadCompleted,
        onProgress = onDownloadProgress,
    )

    private val libraryOps = DownloadLibraryOperations(
        diskProvider = diskProvider,
        mutationPort = mutationPort,
        queueState = _queueState,
        persistence = persistence,
        onError = { message -> _storageError.value = message },
    )

    init {
        control = DownloadControl(
            scope = scope,
            diskProvider = diskProvider,
            queueState = _queueState,
            isRunning = _isRunning,
            speedBytesPerSec = _speedBytesPerSec,
            persistence = persistence,
            activeJobs = activeDownloadJobs,
            pendingCleanupJobs = pendingCleanupJobs,
            sessionBytes = sessionBytes,
            sessionGeneration = sessionGeneration,
            runLoop = { scheduler.runLoop() },
            onFatal = { message -> _storageError.value = message },
            onCleanupFailure = { message -> _storageError.value = message },
        )
    }

    private val scheduler = DownloadScheduler(
        diskProvider = diskProvider,
        engine = engine,
        queueState = _queueState,
        isRunning = _isRunning,
        persistence = persistence,
        activeJobs = activeDownloadJobs,
        pendingCleanupJobs = pendingCleanupJobs,
        downloadParallelism = downloadParallelism,
        sourceParallelism = sourceParallelism,
        onFailed = onDownloadFailed,
    )

    private val startupRecovery = DownloadStartupRecovery(
        scope = scope,
        store = store,
        diskProvider = diskProvider,
        mutationPort = mutationPort,
        queueState = _queueState,
        persistQueue = { persistence.persistImmediate() },
        startLoop = ::start,
    )

    init {
        startupRecovery.restorePersistedQueue()
    }

    suspend fun awaitStartupRecovery() = startupRecovery.await()

    fun restoreDownloads(items: List<DesktopDownload>) = startupRecovery.restore(items)

    suspend fun enqueue(
        sourceId: Long,
        mangaId: Long,
        mangaTitle: String,
        chapters: List<LibraryChapter>,
        autoStart: Boolean = true,
    ) {
        // Slow disk checks happen before the CAS so a concurrent progress update cannot make them run again.
        val knownChapterIds = _queueState.value.mapTo(HashSet()) { it.chapterId }
        val downloadable = chapters.filter { chapter ->
            chapter.id !in knownChapterIds &&
                !isChapterDownloaded(sourceId, mangaTitle, chapter.id, chapter.name, mangaId)
        }.associateBy { it.id }

        queueMutex.withLock {
            var changed = false
            _queueState.update { state ->
                val current = state.toMutableList()
                for (chapter in chapters) {
                    val existingIndex = current.indexOfFirst { it.chapterId == chapter.id }
                    if (existingIndex >= 0) {
                        val existing = current[existingIndex]
                        val recovered = startupRecovery.recoverMissing(existing)
                        if (recovered != existing || existing.status == DownloadStatus.ERROR) {
                            current[existingIndex] = recovered.copy(status = DownloadStatus.QUEUED, error = null)
                        }
                        continue
                    }
                    val chapterToAdd = downloadable[chapter.id] ?: continue
                    current.add(
                        DesktopDownload(
                            chapterId = chapterToAdd.id,
                            mangaId = mangaId,
                            sourceId = sourceId,
                            mangaTitle = mangaTitle,
                            chapterName = chapterToAdd.name,
                            chapterUrl = chapterToAdd.url,
                            status = DownloadStatus.QUEUED,
                            storageLayoutVersion = 1,
                        ),
                    )
                }
                if (current != state) {
                    changed = true
                    current.toList()
                } else {
                    state
                }
            }
            if (changed) persistence.persist()
        }

        if (autoStart) {
            start()
        }
    }

    suspend fun enqueue(manga: LibraryManga, chapters: List<LibraryChapter>, autoStart: Boolean = true) {
        enqueue(manga.sourceId, manga.id, manga.title, chapters, autoStart)
    }

    suspend fun enqueue(manga: MangaDetails, chapters: List<LibraryChapter>, autoStart: Boolean = true) {
        enqueue(manga.sourceId, manga.id, manga.title, chapters, autoStart)
    }

    suspend fun checkAndDownloadAhead(
        sourceId: Long,
        mangaId: Long,
        mangaTitle: String,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) {
        if (count <= 0) return
        val sorted = allChapters.sortedWith(compareBy<LibraryChapter> { it.chapterNumber }.thenBy { it.sourceOrder })
        val currentIndex = sorted.indexOfFirst { it.id == currentChapter.id }
        if (currentIndex < 0) return

        val nextChapters = sorted.drop(currentIndex + 1)
            .filter { !it.read && !isChapterDownloaded(sourceId, mangaTitle, it.id, it.name, mangaId) }
            .take(count)

        if (nextChapters.isNotEmpty()) {
            enqueue(sourceId, mangaId, mangaTitle, nextChapters, autoStart = autoStart)
        }
    }

    suspend fun checkAndDownloadAhead(
        manga: LibraryManga,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) = checkAndDownloadAhead(manga.sourceId, manga.id, manga.title, currentChapter, allChapters, count, autoStart)

    suspend fun checkAndDownloadAhead(
        manga: MangaDetails,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) = checkAndDownloadAhead(manga.sourceId, manga.id, manga.title, currentChapter, allChapters, count, autoStart)

    fun isChapterDownloaded(
        sourceId: Long,
        mangaTitle: String,
        chapterId: Long,
        chapterName: String,
        mangaId: Long? = null,
    ): Boolean = libraryOps.isChapterDownloaded(sourceId, mangaTitle, chapterId, chapterName, mangaId)

    fun deleteDownloadedChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean =
        libraryOps.deleteDownloadedChapter(sourceId, mangaTitle, chapterName)

    fun deleteDownloadedChapter(
        sourceId: Long,
        mangaId: Long?,
        mangaTitle: String,
        chapterId: Long?,
        chapterName: String,
    ): Boolean = libraryOps.deleteDownloadedChapter(sourceId, mangaId, mangaTitle, chapterId, chapterName)

    fun deleteDownloadedChapter(manga: LibraryManga, chapter: LibraryChapter): Boolean =
        libraryOps.deleteDownloadedChapter(manga, chapter)

    fun deleteDownloadedChapter(manga: MangaDetails, chapter: LibraryChapter): Boolean =
        libraryOps.deleteDownloadedChapter(manga, chapter)

    @Synchronized
    fun start(): Boolean = control.start()

    @Synchronized
    fun pause() = control.pause()

    @Synchronized
    fun resume() = control.resume()

    @Synchronized
    fun pause(chapterId: Long): Boolean = control.pause(chapterId)

    @Synchronized
    fun resume(chapterId: Long): Boolean = control.resume(chapterId)

    @Synchronized
    fun cancel(chapterId: Long): DesktopDownload? = control.cancel(chapterId)

    @Synchronized
    fun retry(chapterId: Long) = editor.retry(chapterId)

    @Synchronized
    fun retryAllFailed() = editor.retryAllFailed()

    @Synchronized
    fun clearCompleted(): List<DesktopDownload> = editor.clearCompleted()

    @Synchronized
    fun moveUp(chapterId: Long): Boolean = editor.moveUp(chapterId)

    @Synchronized
    fun moveDown(chapterId: Long): Boolean = editor.moveDown(chapterId)

    @Synchronized
    fun reorder(fromIndex: Int, toIndex: Int): Boolean = editor.reorder(fromIndex, toIndex)

    @Synchronized
    fun retryManga(mangaId: Long): Int = editor.retryManga(mangaId)

    @Synchronized
    fun pauseManga(mangaId: Long): Int = editor.pauseManga(mangaId)

    @Synchronized
    fun resumeManga(mangaId: Long): Int = editor.resumeManga(mangaId)

    @Synchronized
    fun cancelManga(mangaId: Long): List<DesktopDownload> = editor.cancelManga(mangaId)

    @Synchronized
    fun moveMangaToTop(mangaId: Long): Boolean = editor.moveMangaToTop(mangaId)

    internal suspend fun processDownload(download: DesktopDownload) = engine.processDownload(download)

    suspend fun shutdown() {
        close()
        scope.coroutineContext[Job]?.join()
    }

    override fun close() {
        control.close()
        scope.cancel()
    }
}
