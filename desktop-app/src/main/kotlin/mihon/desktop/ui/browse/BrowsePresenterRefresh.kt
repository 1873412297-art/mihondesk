package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.getSourcePreferenceDefinitions
import mihon.desktop.extension.getSourcePreferenceValue
import mihon.desktop.extension.getSourceStates
import mihon.desktop.extension.getSourceStatesForExtension
import mihon.desktop.extension.isExtensionIncognito
import mihon.extension.model.SourceDescriptor

internal fun BrowsePresenter.refresh() {
    scope.launch {
        stateUpdates.update { it.copy(isLoading = true, installFailure = null, errorMessage = null) }
        try {
            val repos = storeService.getRepositories()
            val installed = installer.getInstalledExtensions()
            val snapshot = buildSourceStateSnapshot(installed)

            // Update local state before the network round-trip so installed extensions and
            // sources remain usable when a repository is unreachable.
            stateUpdates.update {
                it.copy(
                    repositories = repos,
                    installedExtensions = installed,
                    sources = snapshot.sources,
                    sourceStates = snapshot.sourceStates,
                    extensionSourceStates = snapshot.extensionSourceStates,
                    incognitoExtensionPackages = snapshot.incognitoExtensionPackages,
                    sourcePreferenceDefinitions = snapshot.sourcePreferenceDefinitions,
                    sourcePreferenceValues = snapshot.sourcePreferenceValues,
                    isLoading = false,
                )
            }
            refreshMigrationCounts()

            val successfulItems = mutableListOf<ExtensionStoreItem>()
            val repositoryFailures = mutableListOf<String>()
            for (repo in repos) {
                try {
                    val items = storeService.fetchRepository(repo)
                    successfulItems.addAll(items)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    repositoryFailures.add(msg)
                }
            }

            val available = mihon.desktop.extension.selectAvailableStoreItems(installed, successfulItems)

            val repoErrorMessage = if (repositoryFailures.isNotEmpty()) {
                val count = repositoryFailures.size
                val noun = if (count == 1) "repository" else "repositories"
                "Failed to refresh $count $noun: ${repositoryFailures.first()}"
            } else {
                null
            }

            stateUpdates.update {
                it.copy(
                    availableExtensions = available,
                    errorMessage = repoErrorMessage,
                )
            }
            maybeBackfillIcons(successfulItems)
            val pendingUpdates = countPendingExtensionUpdates(installed, available)
            if (pendingUpdates > 0 && pendingUpdates != lastNotifiedPendingUpdates) {
                onExtensionUpdatesAvailable(pendingUpdates)
            }
            lastNotifiedPendingUpdates = pendingUpdates
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            stateUpdates.update {
                it.copy(
                    isLoading = false,
                    installFailure = null,
                    errorMessage = "Failed to refresh: ${e.message}",
                )
            }
        }
    }
}

/** Once per session: give already-installed extensions a saved icon.png (package first, then repo). */
internal fun BrowsePresenter.maybeBackfillIcons(storeItems: List<ExtensionStoreItem>) {
    if (iconBackfillStarted) return
    iconBackfillStarted = true
    val iconUrls = storeItems.filter { it.iconUrl.isNotBlank() }.associate { it.pkg to it.iconUrl }
    scope.launch {
        val fixed = runCatching {
            installer.backfillMissingIcons { ext ->
                val url = iconUrls[ext.pkg] ?: return@backfillMissingIcons null
                iconFetcher?.invoke(url)
            }
        }.getOrDefault(0)
        if (fixed > 0) {
            mihon.desktop.logging.DesktopLogger.info("BrowsePresenter", "Backfilled $fixed extension icons")
            refreshInstalledAndSources()
        }
    }
}

internal fun BrowsePresenter.buildSourceStateSnapshot(installed: List<InstalledExtension>): SourceStateSnapshot {
    val sourceStates = sourceManager.getSourceStates()
    val extensionSourceStates = installed.associate { extension ->
        extension.pkg to sourceManager.getSourceStatesForExtension(extension.pkg)
    }
    val allStates = (sourceStates + extensionSourceStates.values.flatten()).distinctBy { it.source.id }

    val definitions = allStates.associate { state ->
        state.source.id to sourceManager.getSourcePreferenceDefinitions(state.source.id)
    }
    val values = definitions.mapValues { (sourceId, definitionsForSource) ->
        definitionsForSource.associate { definition ->
            definition.key to (
                sourceManager.getSourcePreferenceValue(sourceId, definition.key)
                    ?: definition.defaultValue
                )
        }
    }

    return SourceStateSnapshot(
        sources = sourceStates.filter { it.isEnabled }.map { it.source },
        sourceStates = sourceStates,
        extensionSourceStates = extensionSourceStates,
        incognitoExtensionPackages = installed
            .filter { sourceManager.isExtensionIncognito(it.pkg) }
            .map { it.pkg }
            .toSet(),
        sourcePreferenceDefinitions = definitions,
        sourcePreferenceValues = values,
    )
}

internal fun BrowsePresenter.refreshInstalledAndSources() {
    val installed = installer.getInstalledExtensions()
    val snapshot = buildSourceStateSnapshot(installed)
    stateUpdates.update {
        it.copy(
            installedExtensions = installed,
            sources = snapshot.sources,
            sourceStates = snapshot.sourceStates,
            extensionSourceStates = snapshot.extensionSourceStates,
            incognitoExtensionPackages = snapshot.incognitoExtensionPackages,
            sourcePreferenceDefinitions = snapshot.sourcePreferenceDefinitions,
            sourcePreferenceValues = snapshot.sourcePreferenceValues,
        )
    }
}

internal data class SourceStateSnapshot(
    val sources: List<SourceDescriptor>,
    val sourceStates: List<SourceState>,
    val extensionSourceStates: Map<String, List<SourceState>>,
    val incognitoExtensionPackages: Set<String>,
    val sourcePreferenceDefinitions: Map<Long, List<SourcePreferenceDefinition>>,
    val sourcePreferenceValues: Map<Long, Map<String, String>>,
)
