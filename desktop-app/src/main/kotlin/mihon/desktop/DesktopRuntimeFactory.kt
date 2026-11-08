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
import mihon.desktop.extension.fetchAvailableExtensions
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
import mihon.desktop.webview.handleExtensionRequest
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object DesktopRuntimeFactory {
    fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
    ): DesktopRuntime = create(args, environment, executableDirectory, LocalMangaImporter::cleanupOrphans)

    internal fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
        cleanupOrphans: (LocalMangaImporter, Path) -> Unit,
    ): DesktopRuntime {
        val context = resolveRuntimeContext(args, environment, executableDirectory)
        val command = context.command
        val layout = context.layout
        val isPortable = context.isPortable
        val directories = context.directories
        mihon.desktop.logging.DesktopLogger.info("DesktopRuntime", "Initializing desktop runtime for command: $command")

        val launchUi = command == DesktopCommand.LaunchUi
        val library = StartupTrace.phase("database open + migrations") {
            DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
        }
        try {
            val backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library)
            val localImporter = LocalMangaImporter(library)
            val localLibraryRoot = directories.root.resolve("media").resolve("local").toAbsolutePath().normalize()
            // The UI shows the library straight from the local database; the orphan scan of the
            // local-media folder runs after the first interactive frame.
            if (!launchUi) cleanupOrphans(localImporter, localLibraryRoot)
            val preferences = DesktopPreferenceStore(directories.root.resolve("preferences.properties"))
            val notificationService = mihon.desktop.notification.WindowsDesktopNotificationService(
                enabledProvider = { preferences.load().desktopNotificationsEnabled },
                hideContentProvider = { preferences.load().desktopNotificationsHideContent },
                stringsProvider = { mihon.desktop.i18n.DesktopStrings.resolve(preferences.load().language) },
                trayIconEnabled = command.needsNotificationTray,
            )
            val downloadsDirectories = resolveDownloadDirectories(directories, preferences)
            val defaultDownloadsDir = downloadsDirectories.defaultDir
            val configuredDownloadsDir = downloadsDirectories.configuredDir
            val downloadDiskProvider = buildDownloadDiskProvider(
                preferences = preferences,
                library = library,
                defaultDownloadsDir = defaultDownloadsDir,
                configuredDownloadsDir = configuredDownloadsDir,
            )
            val downloadStore = mihon.desktop.download.DownloadStore(directories.root.resolve("downloads.json"))
            val cookieStore = mihon.desktop.extension.DesktopCookieStore(directories.root.resolve("cookies.json"))
            val networkSettings = mihon.desktop.extension.DesktopNetworkSettingsStore(preferences)
            val extensionHostDir = directories.root.resolve("extension-host").toFile()
            val brokerTempRoot = mihon.desktop.extension.extensionBrokerTempRoot(extensionHostDir).apply { mkdirs() }
            val networkHelper = mihon.desktop.extension.DesktopNetworkHelper(
                cookieStore = cookieStore,
                policyProvider = networkSettings::load,
                brokerTempRoot = brokerTempRoot.toPath(),
            )
            val appExceptionHandler = desktopAppExceptionHandler()
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + appExceptionHandler)
            val browserResources = resolveBrowserResources()
            val webViews = mihon.desktop.webview.DesktopWebViewManager(
                browserRuntime = environment["MIHON_WEBVIEW_RUNTIME"]?.let(Path::of)
                    ?: browserResources?.resolve("runtime")
                    ?: Path.of("desktop-webview-host/build/browser-runtime").toAbsolutePath(),
                hostDistribution = environment["MIHON_WEBVIEW_HOST_DISTRIBUTION"]?.let(Path::of)
                    ?: browserResources?.resolve("app")
                    ?: Path.of("desktop-webview-host/build/install/desktop-webview-host").toAbsolutePath(),
                cacheDirectory = directories.cache.resolve("browser"),
                network = networkHelper,
                cookies = cookieStore,
            )

            val extensionDir = directories.root.resolve("extensions").toFile()
            val processManager = mihon.desktop.extension.WindowsExtensionProcessManager(
                workingDirectory = extensionHostDir,
                sourceSessionFile = directories.root.resolve("cookies.json").toFile(),
                networkHelper = networkHelper,
                onBrokerHttp = { request -> networkHelper.executeBrokeredRequest(request) },
                onWebView = webViews::handleExtensionRequest,
                scope = appScope,
            )
            val extensionInstaller = mihon.desktop.extension.DesktopExtensionInstaller(
                installRoot = extensionDir,
                preferenceStore = preferences,
                policyProvider = { DesktopNetworkSettingsStore(preferences).load() },
            )
            val installedExtensions = StartupTrace.phase("extension reconcile") {
                extensionInstaller.reconcileInstalledExtensions()
            }
            installedExtensions.filter { it.isEnabled }.forEach { ext ->
                networkHelper.registerExtensionDomains(ext.pkg, ext.manifest.declaredDomains)
            }

            val extensionStoreService = mihon.desktop.extension.ExtensionStoreService(
                preferenceStore = preferences,
                policyProvider = { DesktopNetworkSettingsStore(preferences).load() },
            )
            val sourceManager = mihon.desktop.extension.DesktopSourceManager(
                installer = extensionInstaller,
                processManager = processManager,
                cookieStore = cookieStore,
            )
            val onlineMangaSyncService = mihon.desktop.extension.OnlineMangaSyncService(
                libraryRepository = library,
                sourceManager = sourceManager,
            )
            val readerFactory = if (command == DesktopCommand.LaunchUi || command is DesktopCommand.VerifyReader) {
                DesktopReaderFactory(
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    library = library,
                    settings = DesktopReaderSettingsStore(preferences),
                    onlineChapters = library,
                    sourceManager = sourceManager,
                    networkHelper = networkHelper,
                    onlineCacheDir = directories.cache.resolve("online-pages").toFile(),
                )
            } else {
                null
            }

            val downloader = mihon.desktop.download.DesktopDownloader(
                store = downloadStore,
                diskProvider = downloadDiskProvider,
                networkHelper = networkHelper,
                processManager = processManager,
                sourceManager = sourceManager,
                mutationPort = library,
                downloadParallelism = { preferences.load().downloadParallelCount },
                pageParallelism = { preferences.load().downloadPageParallelCount },
                onDownloadCompleted = { download ->
                    notificationService.notifyDownloadComplete(download.mangaTitle, download.chapterName)
                },
                onDownloadFailed = { download, error ->
                    notificationService.notifyDownloadError(download.mangaTitle, download.chapterName, error)
                },
                onDownloadProgress = { download ->
                    notificationService.notifyDownloadProgress(
                        download.mangaTitle,
                        download.chapterName,
                        download.progress,
                    )
                },
                tallImageSplitter = mihon.desktop.download.TallImageSplitter(
                    diskProvider = downloadDiskProvider,
                    enabled = { preferences.load().splitTallImages },
                ),
            )
            val historyService = mihon.desktop.history.DesktopHistoryService(
                repository = library,
                mutationPort = library,
                scope = appScope,
            )
            val categoryService = mihon.desktop.category.DesktopCategoryService(
                repository = library,
                mutationPort = library,
                scope = appScope,
            )
            val trackerStore = DesktopTrackerStore(preferences)
            val trackerManager = DesktopTrackerManager(
                trackersList = DesktopTrackerManager.defaultTrackers(
                    httpClient = defaultTrackerHttpClient {
                        DesktopNetworkSettingsStore(preferences).load()
                    },
                ),
                store = trackerStore,
            )
            val trackingQueue = mihon.desktop.track.OfflineTrackingQueue(
                queueFile = directories.root.resolve("tracking-queue.json"),
            )
            val trackSyncService = mihon.desktop.track.TrackOnReadSyncService(
                repository = library,
                mutationPort = library,
                trackerManager = trackerManager,
                trackingQueue = trackingQueue,
            )
            val diagnosticService = mihon.desktop.diagnostics.DiagnosticBundleService(
                directories = directories,
                repository = library,
            )
            val statsService = mihon.desktop.stats.DesktopStatsService(library)
            val backupDir = directories.root.resolve("backups").toAbsolutePath().normalize()
            java.nio.file.Files.createDirectories(backupDir)
            val backupExporter = mihon.desktop.library.backup.AndroidBackupExporter(library)
            val backupScheduler = mihon.desktop.backup.DesktopBackupScheduler(
                backupExporter = backupExporter,
                preferenceStore = preferences,
                defaultBackupDir = backupDir,
                scope = appScope,
            )
            val activityCenter = mihon.desktop.activity.ActivityCenter()
            val missingSourceAlerts = mihon.desktop.activity.MissingSourceAlerts()
            val syncScheduler = mihon.desktop.sync.DesktopSyncScheduler(
                preferenceStore = preferences,
                library = library,
                scope = appScope,
                activityCenter = activityCenter,
            )
            syncScheduler.googleDrive = createGoogleDriveSync(directories, preferences, library, appScope)
            val desktopNotificationService = mihon.desktop.platform.DesktopNotificationService(
                enabledProvider = { preferences.load().desktopNotificationsEnabled },
                hideContentProvider = { preferences.load().desktopNotificationsHideContent },
                trayIconEnabled = command.needsNotificationTray,
            )
            val libraryUpdateAlerts = mihon.desktop.notification.LibraryUpdateAlerts(
                display = { title, message ->
                    if (command.needsNotificationTray) {
                        runCatching {
                            mihon.desktop.platform.SharedTrayIcon.acquire()
                                ?.displayMessage(title, message, java.awt.TrayIcon.MessageType.INFO)
                        }.onFailure {
                            mihon.desktop.logging.DesktopLogger.warn("Tray", "Failed to show tray notification", it)
                        }
                    }
                },
                badge = { count ->
                    val strings = mihon.desktop.i18n.DesktopStrings.resolve(preferences.load().language)
                    mihon.desktop.platform.SharedTrayIcon.setBadge(
                        count,
                        strings.text(UiText.TrayNewChapters, count),
                    )
                },
                strings = { mihon.desktop.i18n.DesktopStrings.resolve(preferences.load().language) },
                hideContent = { preferences.load().desktopNotificationsHideContent },
                enabled = { preferences.load().desktopNotificationsEnabled },
            )
            mihon.desktop.platform.SharedTrayIcon.onNotificationClick = { libraryUpdateAlerts.consumeClick() }
            val libraryUpdateService = mihon.desktop.library.update.LibraryUpdateService(
                repository = library,
                syncService = onlineMangaSyncService,
                downloader = downloader,
                notificationService = desktopNotificationService,
                onReport = { report ->
                    libraryUpdateAlerts.onReport(report)
                    if (launchUi) {
                        missingSourceAlerts.publish(
                            report = report,
                            center = activityCenter,
                            scope = appScope,
                            strings = { mihon.desktop.i18n.DesktopStrings.resolve(preferences.load().language) },
                            findInRepos = { sourceId ->
                                extensionStoreService.fetchAvailableExtensions()
                                    .firstOrNull { item -> item.sources.any { it.id == sourceId } }
                            },
                        )
                    }
                },
            )
            val libraryUpdateScheduler = mihon.desktop.library.update.LibraryUpdateScheduler(
                updateService = libraryUpdateService,
                preferenceStore = preferences,
                scope = appScope,
                recoveryFile = directories.root.resolve("library-update-state.properties"),
                startAutomatically = command == DesktopCommand.LaunchUi,
            )
            val extensionUpdateChecker = mihon.desktop.extension.ExtensionUpdateChecker(
                installer = extensionInstaller,
                storeService = extensionStoreService,
                onUpdatesAvailable = { count -> notificationService.notifyExtensionUpdatePending(count) },
                scope = appScope,
            )
            if (launchUi) trackSyncService.start(appScope)
            if (launchUi) {
                mihon.desktop.activity.bindActivitySources(activityCenter, appScope, downloader, libraryUpdateScheduler)
            }
            val backgroundScheduler = resolveBackgroundScheduler(directories)
            val deferredServices = buildDeferredServices(
                launchUi = launchUi,
                cleanupOrphans = cleanupOrphans,
                localImporter = localImporter,
                localLibraryRoot = localLibraryRoot,
                backupScheduler = backupScheduler,
                extensionUpdateChecker = extensionUpdateChecker,
                syncScheduler = syncScheduler,
                preferences = preferences,
                backgroundScheduler = backgroundScheduler,
            )
            val updateDelivery = resolveUpdateDelivery(layout, isPortable, executableDirectory, directories)
            return DesktopRuntime(
                directories = directories,
                preferences = preferences,
                command = command,
                library = library,
                backupImporter = backupImporter,
                localImporter = localImporter,
                localLibraryRoot = localLibraryRoot,
                readerFactory = readerFactory,
                appUpdateService = updateDelivery.service,
                appUpdateInstaller = updateDelivery.installer,
                downloader = downloader,
                downloadStore = downloadStore,
                notificationService = notificationService,
                historyService = historyService,
                categoryService = categoryService,
                trackerStore = trackerStore,
                trackerManager = trackerManager,
                trackingQueue = trackingQueue,
                trackSyncService = trackSyncService,
                diagnosticService = diagnosticService,
                statsService = statsService,
                backupScheduler = backupScheduler,
                syncScheduler = syncScheduler,
                cookieStore = cookieStore,
                desktopNotificationService = desktopNotificationService,
                libraryUpdateAlerts = libraryUpdateAlerts,
                libraryUpdateService = libraryUpdateService,
                libraryUpdateScheduler = libraryUpdateScheduler,
                extensionUpdateChecker = extensionUpdateChecker,
                backgroundScheduler = backgroundScheduler,
                webViews = webViews,
                activityCenter = activityCenter,
                missingSourceAlerts = missingSourceAlerts,
                extensionInstaller = extensionInstaller,
                extensionStoreService = extensionStoreService,
                processManager = processManager,
                sourceManager = sourceManager,
                onlineMangaSyncService = onlineMangaSyncService,
                deferredServices = deferredServices,
                closeReaderServices = {
                    mihon.desktop.logging.DesktopLogger.info("DesktopRuntime", "Shutting down desktop runtime services")
                    backupScheduler.stop()
                    syncScheduler.stop()
                    extensionUpdateChecker.stop()
                    libraryUpdateScheduler.stop()
                    trackSyncService.close()
                    runBlocking { downloader.shutdown() }
                    readerFactory?.closeServices()
                    sourceManager.close()
                    processManager.close()
                    webViews.close()
                    networkHelper.close()
                    runBlocking { appScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
                    mihon.desktop.logging.DesktopLogger.info(
                        "DesktopRuntime",
                        "Desktop runtime services shutdown complete",
                    )
                },
            )
        } catch (error: Throwable) {
            try {
                library.close()
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }
}
