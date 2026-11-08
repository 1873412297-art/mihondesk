package mihon.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.extension.DesktopNetworkSettingsStore
import mihon.desktop.extension.DesktopPolicyProxySelector
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.library.sync.SqlDelightSyncLocalRepository
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.sync.gdrive.GoogleDriveSyncFactory
import mihon.desktop.sync.gdrive.GoogleDriveSyncService
import java.nio.file.Path

internal class RuntimeContext(
    val command: DesktopCommand,
    val layout: mihon.desktop.platform.PortableLayout,
    val isPortable: Boolean,
    val directories: AppDirectories,
)

internal fun resolveRuntimeContext(
    args: Array<String>,
    environment: Map<String, String>,
    executableDirectory: Path,
): RuntimeContext {
    val command = DesktopCommandParser.parse(args, environment)
    val dataDirectoryArguments = args.filter { it.startsWith("--data-dir=") }
    if (dataDirectoryArguments.size > 1) {
        throw CommandLineException("--data-dir may be specified only once", "--data-dir")
    }
    val explicitRoot = dataDirectoryArguments.singleOrNull()?.let { argument ->
        val value = argument.substringAfter('=')
        if (value.isBlank()) throw CommandLineException("--data-dir requires a non-blank path", "--data-dir")
        commandPath(value, "--data-dir")
    }
    val layout = mihon.desktop.platform.PortableLayout.detect(args, executableDirectory)
    val appData = environment["APPDATA"]
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
    val directories = AppDirectoryResolver(appData, executableDirectory)
        .resolve(layout, explicitRoot)
        .create()
    if (!mihon.desktop.logging.DesktopLogger.isInitialized()) {
        mihon.desktop.logging.DesktopLogger.init(directories.logs)
    }
    return RuntimeContext(command, layout, layout.portable, directories)
}

private fun commandPath(value: String, option: String): Path = try {
    Path.of(value)
} catch (error: RuntimeException) {
    throw CommandLineException("$option contains an invalid path", option, error)
}

/** Published download layouts that still resolve to a chapter inside the download root. */
private val LOCAL_DOWNLOAD_ASSET_KINDS = setOf("DIRECTORY", "ARCHIVE")

internal fun buildDownloadDiskProvider(
    preferences: DesktopPreferenceStore,
    library: SqlDelightLibraryRepository,
    defaultDownloadsDir: Path,
    configuredDownloadsDir: Path,
): mihon.desktop.download.DownloadDiskProvider {
    val registeredDownloadDirectory: (Long, Long, Long) -> Path? = { sourceId, mangaId, chapterId ->
        library.chapterAsset(chapterId)?.takeIf {
            sourceId != 0L && it.mangaId == mangaId && it.assetKind in LOCAL_DOWNLOAD_ASSET_KINDS &&
                library.mangaSnapshot(mangaId)?.sourceId == sourceId
        }?.let { asset ->
            asset.storageRoot.resolve(asset.relativePath).normalize().takeIf {
                it != asset.storageRoot && it.startsWith(asset.storageRoot)
            }
        }
    }
    return runCatching {
        mihon.desktop.download.DownloadDiskProvider(
            downloadsDir = configuredDownloadsDir,
            legacyDownloadsDirs = listOf(defaultDownloadsDir).filterNot { it == configuredDownloadsDir },
            registeredChapterDirectory = registeredDownloadDirectory,
            saveAsCbz = { preferences.load().saveChapterAsCbz },
        )
    }.getOrElse { error ->
        System.err.println(
            "Configured download directory is unavailable ($configuredDownloadsDir): ${error.message}. " +
                "Using $defaultDownloadsDir.",
        )
        mihon.desktop.download.DownloadDiskProvider(
            defaultDownloadsDir,
            registeredChapterDirectory = registeredDownloadDirectory,
            saveAsCbz = { preferences.load().saveChapterAsCbz },
        )
    }
}

internal fun buildDeferredServices(
    launchUi: Boolean,
    cleanupOrphans: (LocalMangaImporter, Path) -> Unit,
    localImporter: LocalMangaImporter,
    localLibraryRoot: Path,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler,
    extensionUpdateChecker: mihon.desktop.extension.ExtensionUpdateChecker,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler,
    preferences: DesktopPreferenceStore,
    backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler?,
): () -> Unit = {
    if (launchUi) {
        runDeferredStep("local orphan scan") { cleanupOrphans(localImporter, localLibraryRoot) }
        runDeferredStep("backup scheduler") { backupScheduler.start() }
        runDeferredStep("extension update check") { extensionUpdateChecker.start() }
        runDeferredStep("sync scheduler") {
            syncScheduler.start()
            syncScheduler.googleDrive?.requestSync()
        }
        val launchPreferences = runCatching { preferences.load() }
            .onFailure { DesktopLogger.warn("DesktopRuntime", "Cannot read launch preferences", it) }
            .getOrNull()
        if (launchPreferences?.backgroundTasksEnabled == true) {
            runDeferredStep("background task reconcile") {
                backgroundScheduler?.reconcile(
                    true,
                    launchPreferences.libraryUpdateIntervalHours,
                    launchPreferences.backupIntervalHours,
                )?.let { results ->
                    val failed = results.filterValues { it.exitCode != 0 }
                    if (failed.isNotEmpty()) {
                        DesktopLogger.warn(
                            "DesktopRuntime",
                            "Background task reconcile failed: " + failed.values.joinToString("; ") { it.output },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Runs one deferred startup step without letting it abort the remaining services. The deferred
 * thread runs once per launch, so one unreadable folder or scheduler must not disable backups,
 * Drive sync, extension updates and the Windows task registration for the whole session.
 */
internal fun runDeferredStep(name: String, block: () -> Unit) {
    try {
        StartupTrace.phase(name, block)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DesktopLogger.error("DesktopRuntime", "Deferred startup step failed: $name", error)
    }
}

internal class DownloadDirectories(val defaultDir: Path, val configuredDir: Path)

internal fun resolveDownloadDirectories(
    directories: AppDirectories,
    preferences: DesktopPreferenceStore,
): DownloadDirectories {
    val defaultDir = directories.root.resolve("media").resolve("downloads")
        .toAbsolutePath()
        .normalize()
    val configuredDir = resolveDownloadStoragePath(
        dataRoot = directories.root,
        configuredPath = preferences.load().downloadStoragePath,
    ) ?: defaultDir
    return DownloadDirectories(defaultDir, configuredDir)
}

internal fun desktopAppExceptionHandler(): kotlinx.coroutines.CoroutineExceptionHandler =
    kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        if (throwable !is kotlinx.coroutines.CancellationException) {
            mihon.desktop.logging.DesktopLogger.error(
                "AppScope",
                "Uncaught coroutine exception: ${throwable.message}",
                throwable,
            )
        }
    }

internal fun createGoogleDriveSync(
    directories: AppDirectories,
    preferences: DesktopPreferenceStore,
    library: SqlDelightLibraryRepository,
    scope: CoroutineScope,
): GoogleDriveSyncService? {
    val deviceId = {
        preferences.load().syncDeviceId.ifBlank {
            val id = java.util.UUID.randomUUID().toString()
            preferences.updatePreferences { it.copy(syncDeviceId = id) }
            id
        }
    }
    return runCatching {
        GoogleDriveSyncFactory.create(
            profileRoot = directories.root,
            scope = scope,
            deviceId = deviceId,
            localRepository = { SqlDelightSyncLocalRepository(library.database, deviceId = deviceId) },
            proxySelector = DesktopPolicyProxySelector({ DesktopNetworkSettingsStore(preferences).load() }),
        )
    }.onFailure { error ->
        DesktopLogger.warn("DesktopRuntime", "Google Drive sync unavailable", error)
    }.getOrNull()
}

internal fun resolveBrowserResources(): Path? =
    System.getProperty("compose.application.resources.dir")?.let(Path::of)?.resolve("browser")

internal fun resolveBackgroundScheduler(
    directories: AppDirectories,
): mihon.desktop.platform.WindowsBackgroundScheduler? {
    val packagedExecutable = ProcessHandle.current().info().command().orElse(null)?.let(Path::of)
        ?.takeIf { it.fileName.toString().lowercase() in setOf("mihondesk.exe", "mihonw.exe") }
    return packagedExecutable?.let { mihon.desktop.platform.WindowsBackgroundScheduler(it, directories.root) }
}

internal class UpdateDelivery(
    val service: mihon.desktop.updates.DesktopAppUpdateService,
    val installer: mihon.desktop.updates.AppUpdateInstaller?,
)

internal fun resolveUpdateDelivery(
    layout: mihon.desktop.platform.PortableLayout,
    isPortable: Boolean,
    executableDirectory: Path,
    directories: AppDirectories,
): UpdateDelivery = UpdateDelivery(
    service = mihon.desktop.updates.DesktopAppUpdateService(distributionMode = layout.mode),
    installer = if (isPortable) {
        mihon.desktop.updates.PortableUpdateHandoff(executableDirectory, directories.root)
    } else {
        null
    },
)
