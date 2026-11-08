package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.logging.DesktopLogger
import mihon.extension.ipc.IpcException
import mihon.extension.source.WindowsHttpSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

internal suspend fun DesktopSourceManager.ensureSourceLoaded(sourceId: Long) {
    if (builtinSources.containsKey(sourceId)) return
    if (installer != null) {
        installer.withLifecycleLock { sourceLoadMutex.withLock { loadSource(sourceId) } }
    } else {
        sourceLoadMutex.withLock { loadSource(sourceId) }
    }
}

internal suspend fun DesktopSourceManager.sourceWebPage(sourceId: Long): String? {
    (builtinSources[sourceId] as? WindowsHttpSource)?.let { return it.baseUrl }
    return withLoadedExtensionSource(sourceId) { proc ->
        val source = proc.getSources().firstOrNull { it.id == sourceId }
            ?: throw IpcException("Source with ID $sourceId not found")
        source.baseUrl?.takeIf { it.isNotBlank() }
    }
}

internal suspend fun DesktopSourceManager.loadSource(sourceId: Long) {
    val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")

    // Starting the host can advance its epoch after a crash. Do this before consulting the
    // package cache so a restarted, empty host never inherits the previous process' loaded set.
    proc.start()

    // Reset loaded packages if extension host process was restarted
    if (proc.epoch != lastKnownEpoch) {
        loadedPackages.clear()
        remoteSourcePreferences.clear()
        remotePreferenceSupport.clear()
        lastKnownEpoch = proc.epoch
    }

    val installed = installer?.getInstalledExtensions() ?: emptyList()
    val targetExt = installed.firstOrNull { ext ->
        ext.isEnabled &&
            isSourceEnabled(sourceId) &&
            ext.manifest.sources.any { s -> s.id == sourceId }
    } ?: return

    // Suwayomi resolves its source cache first and reloads the owning extension on a miss.
    // Validate the host registry as well as this process-local package hint so a live host with
    // a cleared/partially replaced registry can repair itself without an application restart.
    val hostHasSource = if (loadedPackages.contains(targetExt.pkg)) {
        runCatching { proc.getSources().any { it.id == sourceId } }.getOrDefault(false)
    } else {
        false
    }
    if (!hostHasSource) {
        loadedPackages.remove(targetExt.pkg)
        val packageFile = java.io.File(targetExt.packageFile)
        if (!packageFile.exists()) {
            throw IllegalStateException("扩展包文件缺失：${targetExt.packageFile}，请重新安装该扩展")
        }
        val loadedSources = proc.loadExtension(packageFile)
        check(loadedSources.any { it.id == sourceId }) {
            "Extension ${targetExt.pkg} loaded without requested source $sourceId " +
                "(registered=${loadedSources.map { it.id }})"
        }
        // Publish the package hint only after every saved source setting has been restored.
        // Call IPC directly while holding the load mutex; recursively ensuring here deadlocks.
        loadedSources.forEach { source -> restoreSourcePreferences(proc, source.id) }
        loadedPackages.add(targetExt.pkg)
    }
}

/** Unloads the package from the extension host and clears its local bookkeeping. */
internal suspend fun DesktopSourceManager.unloadExtension(pkg: String) {
    // The lifecycle lock nests the load lock elsewhere, so a caller that already holds it must not
    // take it again here.
    if (installer != null) {
        installer.withLifecycleLock { sourceLoadMutex.withLock { unloadExtensionLocked(pkg) } }
    } else {
        sourceLoadMutex.withLock { unloadExtensionLocked(pkg) }
    }
}

/** Same as [unloadExtension], for callers that already hold [sourceLoadMutex]. */
internal suspend fun DesktopSourceManager.unloadExtensionLocked(pkg: String) {
    processManager?.unloadExtension(pkg)
    clearLocalExtensionState(pkg)
}

private fun DesktopSourceManager.clearLocalExtensionState(pkg: String) {
    loadedPackages.remove(pkg)
    installer?.getInstalledExtensions()
        ?.firstOrNull { it.pkg == pkg }
        ?.manifest
        ?.sources
        ?.forEach { source ->
            remoteSourcePreferences.remove(source.id)
            remotePreferenceSupport.remove(source.id)
        }
}

internal suspend fun DesktopSourceManager.getPopular(sourceId: Long, page: Int): MangasPage = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getPopularManga(page)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.getPopular(sourceId, page) }
}

internal suspend fun DesktopSourceManager.getLatest(sourceId: Long, page: Int): MangasPage = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getLatestUpdates(page)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.getLatest(sourceId, page) }
}

internal fun DesktopSourceManager.getFilterList(sourceId: Long): FilterList {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return builtin.getFilterList()
    }
    return try {
        // Callers such as the desktop Compose source screen expect a synchronous filter list.
        // The process manager keeps the host/extension loaded, so subsequent calls are cheap.
        runBlocking { loadFilterList(sourceId) }
    } catch (_: Exception) {
        FilterList()
    }
}

/**
 * Asynchronously loads the filter list for a source. Builtin sources resolve immediately;
 * extension sources are loaded through the extension host and decoded from the IPC DTO.
 */
internal suspend fun DesktopSourceManager.loadFilterList(sourceId: Long): FilterList = withContext(Dispatchers.IO) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getFilterList()
    }
    if (processManager == null) return@withContext FilterList()
    try {
        withLoadedExtensionSource(sourceId) { proc -> proc.getFilterList(sourceId) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DesktopLogger.warn("SourceManager", "Filter load failed: $sourceId", error)
        FilterList()
    }
}

internal suspend fun DesktopSourceManager.searchManga(
    sourceId: Long,
    page: Int,
    query: String,
    filters: FilterList = FilterList(),
): MangasPage = withContext(Dispatchers.IO) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.searchManga(page, query, filters)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.searchManga(sourceId, page, query, filters) }
}

internal suspend fun DesktopSourceManager.getMangaDetails(sourceId: Long, manga: SManga): SManga = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getMangaDetails(manga)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.getMangaDetails(sourceId, manga) }
}

internal suspend fun DesktopSourceManager.getChapterList(sourceId: Long, manga: SManga): List<SChapter> = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getChapterList(manga)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.getChapterList(sourceId, manga) }
}

internal suspend fun DesktopSourceManager.getPageList(sourceId: Long, chapter: SChapter): List<Page> = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext builtin.getPageList(chapter)
    }
    if (processManager == null) return@withContext emptyList()
    withLoadedExtensionSource(sourceId) { proc -> proc.getPageList(sourceId, chapter) }
}

internal suspend fun DesktopSourceManager.getImage(sourceId: Long, page: Page): ByteArray? = withContext(
    Dispatchers.IO,
) {
    val builtin = builtinSources[sourceId]
    if (builtin != null) {
        return@withContext (builtin as? mihon.extension.source.WindowsImageSource)?.getImage(page)
    }
    withLoadedExtensionSource(sourceId) { proc -> proc.getImage(sourceId, page) }
}

// ---------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------
