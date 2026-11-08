package mihon.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.cli.needsNotificationTray
import mihon.desktop.download.DownloadCacheCleaner
import mihon.desktop.download.DownloadDiskProvider
import mihon.desktop.extension.DesktopNetworkSettingsStore
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.DesktopTrackerStore
import mihon.desktop.track.defaultTrackerHttpClient
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DesktopRuntime(
    val directories: AppDirectories,
    val preferences: DesktopPreferenceStore,
    val command: DesktopCommand,
    val library: SqlDelightLibraryRepository,
    val backupImporter: AndroidBackupImporter,
    val localImporter: LocalMangaImporter,
    val localLibraryRoot: Path,
    val readerFactory: DesktopReaderFactory? = null,
    val downloader: mihon.desktop.download.DesktopDownloader? = null,
    val downloadStore: mihon.desktop.download.DownloadStore? = null,
    val notificationService: mihon.desktop.notification.DesktopNotificationService? = null,
    val historyService: mihon.desktop.history.DesktopHistoryService? = null,
    val categoryService: mihon.desktop.category.DesktopCategoryService? = null,
    val trackerStore: mihon.desktop.track.DesktopTrackerStore? = null,
    val trackerManager: mihon.desktop.track.DesktopTrackerManager? = null,
    val trackingQueue: mihon.desktop.track.OfflineTrackingQueue? = null,
    val trackSyncService: mihon.desktop.track.TrackOnReadSyncService? = null,
    val diagnosticService: mihon.desktop.diagnostics.DiagnosticBundleService? = null,
    val statsService: mihon.desktop.stats.DesktopStatsService? = null,
    val backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    val syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    val cookieStore: mihon.desktop.extension.DesktopCookieStore? = null,
    val desktopNotificationService: mihon.desktop.platform.DesktopNotificationService? = null,
    val libraryUpdateAlerts: mihon.desktop.notification.LibraryUpdateAlerts? = null,
    val libraryUpdateService: mihon.desktop.library.update.LibraryUpdateService? = null,
    val libraryUpdateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    val extensionUpdateChecker: mihon.desktop.extension.ExtensionUpdateChecker? = null,
    val backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler? = null,
    val webViews: mihon.desktop.webview.DesktopWebViewManager? = null,
    val activityCenter: mihon.desktop.activity.ActivityCenter = mihon.desktop.activity.ActivityCenter(),
    val missingSourceAlerts: mihon.desktop.activity.MissingSourceAlerts =
        mihon.desktop.activity.MissingSourceAlerts(),
    val customCoverManager: mihon.desktop.image.CustomCoverManager =
        mihon.desktop.image.CustomCoverManager(directories.covers),
    val imageLoader: mihon.desktop.image.DesktopImageLoader =
        mihon.desktop.image.DesktopImageLoader(
            diskCacheDir = directories.cache.resolve("covers"),
            customCoverManager = customCoverManager,
            policyProvider = { DesktopNetworkSettingsStore(preferences).load() },
        ),
    val backupExporter: mihon.desktop.library.backup.AndroidBackupExporter =
        mihon.desktop.library.backup.AndroidBackupExporter(library),
    val extensionInstaller: mihon.desktop.extension.DesktopExtensionInstaller =
        mihon.desktop.extension.DesktopExtensionInstaller(
            installRoot = directories.root.resolve("extensions").toFile(),
            preferenceStore = preferences,
            policyProvider = { DesktopNetworkSettingsStore(preferences).load() },
        ),
    val extensionStoreService: mihon.desktop.extension.ExtensionStoreService =
        mihon.desktop.extension.ExtensionStoreService(
            preferenceStore = preferences,
            policyProvider = { DesktopNetworkSettingsStore(preferences).load() },
        ),
    val processManager: mihon.desktop.extension.WindowsExtensionProcessManager? = null,
    val sourceManager: mihon.desktop.extension.DesktopSourceManager =
        mihon.desktop.extension.DesktopSourceManager(
            installer = extensionInstaller,
            processManager = processManager,
        ),
    val onlineMangaSyncService: mihon.desktop.extension.OnlineMangaSyncService =
        mihon.desktop.extension.OnlineMangaSyncService(
            libraryRepository = library,
            sourceManager = sourceManager,
        ),
    val diskProvider: DownloadDiskProvider =
        downloader?.diskProvider ?: DownloadDiskProvider(directories.root.resolve("media").resolve("downloads")),
    val downloadCacheCleaner: DownloadCacheCleaner =
        DownloadCacheCleaner(
            repository = library,
            diskProvider = diskProvider,
            coordinatedDelete = downloader?.let { activeDownloader ->
                { manga, chapter ->
                    activeDownloader.deleteDownloadedChapter(
                        sourceId = manga.sourceId,
                        mangaId = manga.id,
                        mangaTitle = manga.title,
                        chapterId = chapter.id,
                        chapterName = chapter.name,
                    )
                }
            },
            activeMangaIds = { downloader?.queueState?.value?.mapTo(HashSet()) { it.mangaId }.orEmpty() },
        ),
    private val closeReaderSessions: suspend () -> Unit = readerFactory?.let { it::shutdown } ?: {},
    private val closeReaderServices: () -> Unit = readerFactory?.let { it::closeServices } ?: {},
    internal val closeLibrary: () -> Unit = library::close,
    private val blockingShutdown: ExecutorService = BLOCKING_SHUTDOWN_EXECUTOR,
    val appUpdateService: mihon.desktop.updates.DesktopAppUpdateService =
        mihon.desktop.updates.DesktopAppUpdateService(),
    val appUpdateInstaller: mihon.desktop.updates.AppUpdateInstaller? = null,
    private val deferredServices: () -> Unit = {},
) : AutoCloseable {
    private val deferredServicesStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Starts the services that are not needed for the first interactive frame (sync server, sync and
     * backup schedulers, extension update check, Windows background tasks, orphan cleanup) on a
     * background thread. Called by the UI once it is interactive; safe to call more than once.
     */
    fun startDeferredServices() {
        if (!deferredServicesStarted.compareAndSet(false, true)) return
        Thread({
            runCatching { StartupTrace.phase("deferred services") { deferredServices() } }
                .onFailure {
                    mihon.desktop.logging.DesktopLogger.error("DesktopRuntime", "Deferred startup failed", it)
                }
        }, "mihon-deferred-startup").apply { isDaemon = true }.start()
    }
    private val shutdownLock = Any()
    private var shutdownResult: CompletableDeferred<Result<Unit>>? = null
    private val shutdownHooks = mutableListOf<suspend () -> Unit>()

    fun onShutdown(hook: suspend () -> Unit) = synchronized(shutdownLock) {
        check(shutdownResult == null) { "Runtime is shutting down" }
        shutdownHooks.add(hook)
        Unit
    }

    /** Stops session admission, flushes sessions, then disposes runtime services and the database. */
    suspend fun shutdown() {
        val deferred: CompletableDeferred<Result<Unit>>
        val owner: Boolean
        synchronized(shutdownLock) {
            val existing = shutdownResult
            if (existing == null) {
                deferred = CompletableDeferred()
                shutdownResult = deferred
                owner = true
            } else {
                deferred = existing
                owner = false
            }
        }
        if (!owner) return deferred.await().getOrThrow()

        var failure: Throwable? = null
        try {
            shutdownHooks.forEach { hook ->
                try {
                    hook()
                } catch (error: Throwable) {
                    failure = failure.append(error)
                }
            }
            shutdownHooks.clear()
            try {
                closeReaderSessions()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            try {
                closeReaderServices()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            try {
                closeLibrary()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            val shutdownFailure = failure
            if (shutdownFailure == null) {
                deferred.complete(Result.success(Unit))
            } else {
                deferred.complete(Result.failure(shutdownFailure))
            }
        } catch (error: Throwable) {
            deferred.complete(Result.failure(error))
        }
        return deferred.await().getOrThrow()
    }

    override fun close() {
        check(!EventQueue.isDispatchThread()) { "DesktopRuntime.close() must not run on the EDT; use shutdown()" }
        try {
            blockingShutdown.submit<Unit> { runBlocking { shutdown() } }.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    companion object {
        private val BLOCKING_SHUTDOWN_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mihon-runtime-shutdown").apply { isDaemon = true }
        }

        internal fun forTesting(
            closeSessions: suspend () -> Unit = {},
            closeReaderServices: () -> Unit = {},
            closeLibrary: () -> Unit = {},
        ): DesktopRuntime {
            // Roots stay on disk until the JVM exits: tests read their files after shutdown() too.
            val root = TestRuntimeRoots.shared.create()
            val directories = AppDirectories(root).create()
            val library = DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
            return DesktopRuntime(
                directories = directories,
                preferences = DesktopPreferenceStore(root.resolve("preferences.properties")),
                command = DesktopCommand.FoundationSmoke,
                library = library,
                backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library),
                localImporter = LocalMangaImporter(library),
                localLibraryRoot = root.resolve("media").resolve("local"),
                closeReaderSessions = closeSessions,
                closeReaderServices = closeReaderServices,
                closeLibrary = {
                    closeLibrary()
                    library.close()
                },
            )
        }
    }
}

internal fun resolveDownloadStoragePath(dataRoot: Path, configuredPath: String): Path? {
    val trimmed = configuredPath.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val selected = Path.of(trimmed)
        (if (selected.isAbsolute) selected else dataRoot.resolve(selected))
            .toAbsolutePath()
            .normalize()
    }.getOrNull()
}

private fun Throwable?.append(error: Throwable): Throwable = this?.also { it.addSuppressed(error) } ?: error
