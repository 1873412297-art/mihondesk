package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreUnavailableException
import mihon.desktop.extension.clearExtensionCookies
import mihon.desktop.extension.clearSourceCookies
import mihon.desktop.extension.isExtensionIncognito
import mihon.desktop.extension.isSourceEnabled
import mihon.desktop.extension.isSourceIncognito
import mihon.desktop.extension.isStoreCandidateInstallable
import mihon.desktop.extension.setExtensionIncognito
import mihon.desktop.extension.setSourceEnabled
import mihon.desktop.extension.setSourceIncognito
import mihon.desktop.extension.unloadExtension
import java.io.File

internal fun BrowsePresenter.installExtension(item: ExtensionStoreItem) = installItems(listOf(item))

internal fun BrowsePresenter.installItems(items: List<ExtensionStoreItem>) {
    if (items.isEmpty()) return
    startInstallation(items.first().pkg, items.first().name, ExtensionInstallPhase.Downloading) {
        for (item in items) {
            currentCoroutineContext().ensureActive()
            synchronized(this) {
                if (installJob?.isActive != true) throw CancellationException("Extension installation cancelled")
                stateUpdates.update {
                    it.copy(
                        installingPkg = item.pkg,
                        installingName = item.name,
                        installPhase = ExtensionInstallPhase.Downloading,
                    )
                }
            }
            requireStoreInstallAvailable(item)
            installer.downloadAndInstall(
                item.downloadUrl,
                item.sha256,
                item.repoUrl,
                storeItem = item,
                onDownloadComplete = ::beginInstallation,
            )
            refreshInstalledAndSources()
        }
    }
}

internal fun BrowsePresenter.installFromFile(file: File) {
    startInstallation(file.name, file.name, ExtensionInstallPhase.Installing, fromLocalFile = true) {
        installer.installFromLocalFile(file)
        refreshInstalledAndSources()
    }
}

/**
 * Blocks a store candidate that the configured repositories no longer offer, so the user learns
 * that the listing is gone instead of seeing a failed download.
 */
internal fun BrowsePresenter.requireStoreInstallAvailable(item: ExtensionStoreItem) {
    if (
        isStoreCandidateInstallable(
            item = item,
            repositories = stateUpdates.value.repositories,
            available = stateUpdates.value.availableExtensions,
        )
    ) {
        return
    }
    throw ExtensionStoreUnavailableException(item.pkg, item.version)
}

@Synchronized
internal fun BrowsePresenter.beginInstallation() {
    if (installJob?.isActive != true || stateUpdates.value.installPhase != ExtensionInstallPhase.Downloading) {
        throw CancellationException("Extension download was cancelled")
    }
    stateUpdates.update { it.copy(installPhase = ExtensionInstallPhase.Installing) }
}

@Synchronized
internal fun BrowsePresenter.cancelInstallation() {
    if (stateUpdates.value.installPhase != ExtensionInstallPhase.Downloading) return
    stateUpdates.update { it.copy(installPhase = ExtensionInstallPhase.Cancelling) }
    installJob?.cancel()
}

@Synchronized
internal fun BrowsePresenter.startInstallation(
    pkg: String,
    name: String,
    phase: ExtensionInstallPhase,
    fromLocalFile: Boolean = false,
    action: suspend () -> Unit,
) {
    if (installJob?.isCompleted == false || !scope.isActive) return
    stateUpdates.update {
        it.copy(
            isInstalling = true,
            installingPkg = pkg,
            installingName = name,
            installPhase = phase,
            installationCancelled = false,
            installFailure = null,
            errorMessage = null,
        )
    }
    val activityId = "extension:$pkg"
    activityCenter?.begin(activityId, mihon.desktop.activity.ActivityKind.EXTENSION_INSTALL, subject = name)
    val job = scope.launch(start = CoroutineStart.LAZY) {
        try {
            action()
            activityCenter?.succeed(activityId)
        } catch (cancelled: CancellationException) {
            activityCenter?.remove(activityId)
            throw cancelled
        } catch (failure: Exception) {
            activityCenter?.fail(
                activityId,
                reason = failure.message ?: failure.javaClass.simpleName,
                retry = { startInstallation(pkg, name, phase, fromLocalFile, action) },
            )
            val reason = classifyInstallFailure(failure, fromLocalFile)
            stateUpdates.update {
                it.copy(
                    errorMessage = "Failed to install ${it.installingName}: ${failure.message}",
                    installFailure = reason,
                )
            }
        }
    }
    installJob = job
    job.invokeOnCompletion { cause ->
        synchronized(this) {
            if (installJob === job) {
                installJob = null
                stateUpdates.update {
                    it.copy(
                        isInstalling = false,
                        installingPkg = null,
                        installPhase = null,
                        installationCancelled = cause is CancellationException,
                    )
                }
            }
        }
    }
    job.start()
}

internal fun BrowsePresenter.uninstallExtension(pkg: String) {
    scope.launch {
        try {
            // Release the host before deleting the package: a loaded jar holds its files open.
            sourceManager.unloadExtension(pkg)
            installer.uninstall(pkg)
            refreshInstalledAndSources()
        } catch (e: Exception) {
            stateUpdates.update {
                it.copy(installFailure = null, errorMessage = "Failed to uninstall $pkg: ${e.message}")
            }
        }
    }
}

internal fun BrowsePresenter.toggleExtensionEnabled(pkg: String, enabled: Boolean) {
    scope.launch {
        try {
            installer.setExtensionEnabled(pkg, enabled)
            if (!enabled) {
                sourceManager.unloadExtension(pkg)
            }
            refreshInstalledAndSources()
        } catch (e: Exception) {
            stateUpdates.update { it.copy(installFailure = null, errorMessage = "Failed to toggle $pkg: ${e.message}") }
        }
    }
}

internal fun BrowsePresenter.setSourceEnabled(sourceId: Long, enabled: Boolean) {
    sourceManager.setSourceEnabled(sourceId, enabled)
    refreshInstalledAndSources()
}

internal fun BrowsePresenter.toggleSourceEnabled(sourceId: Long) {
    setSourceEnabled(sourceId, !sourceManager.isSourceEnabled(sourceId))
}

internal fun BrowsePresenter.setSourceIncognito(sourceId: Long, incognito: Boolean) {
    sourceManager.setSourceIncognito(sourceId, incognito)
    refreshInstalledAndSources()
}

internal fun BrowsePresenter.toggleSourceIncognito(sourceId: Long) {
    setSourceIncognito(sourceId, !sourceManager.isSourceIncognito(sourceId))
}

internal fun BrowsePresenter.setExtensionIncognito(pkg: String, incognito: Boolean) {
    sourceManager.setExtensionIncognito(pkg, incognito)
    stateUpdates.update { current ->
        val updated = if (incognito) {
            current.incognitoExtensionPackages + pkg
        } else {
            current.incognitoExtensionPackages - pkg
        }
        current.copy(incognitoExtensionPackages = updated)
    }
}

internal fun BrowsePresenter.toggleExtensionIncognito(pkg: String) {
    setExtensionIncognito(pkg, !sourceManager.isExtensionIncognito(pkg))
}

/** Android-style alias for toggling an extension-level incognito flag. */
internal fun BrowsePresenter.toggleIncognito(pkg: String) = toggleExtensionIncognito(pkg)

/** Android-style alias for toggling a source's enabled flag. */
internal fun BrowsePresenter.toggleSource(sourceId: Long) = toggleSourceEnabled(sourceId)

internal fun BrowsePresenter.clearExtensionCookies(pkg: String) {
    sourceManager.clearExtensionCookies(pkg)
}

internal fun BrowsePresenter.clearSourceCookies(sourceId: Long) {
    sourceManager.clearSourceCookies(sourceId)
}

internal fun BrowsePresenter.clearCookies(pkg: String) = clearExtensionCookies(pkg)

internal fun BrowsePresenter.clearCookies(sourceId: Long) = clearSourceCookies(sourceId)
