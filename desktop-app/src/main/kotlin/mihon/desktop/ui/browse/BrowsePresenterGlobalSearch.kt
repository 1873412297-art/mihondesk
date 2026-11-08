package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.extension.searchManga

internal fun BrowsePresenter.openGlobalSearch() {
    stateUpdates.update {
        it.copy(
            isGlobalSearchOpen = true,
            globalSearchQuery = it.searchQuery,
        )
    }
    if (stateUpdates.value.searchQuery.isNotBlank()) {
        performGlobalSearch()
    }
}

internal fun BrowsePresenter.closeGlobalSearch() {
    globalSearchGeneration.incrementAndGet()
    globalSearchJob?.cancel()
    stateUpdates.update {
        it.copy(
            isGlobalSearchOpen = false,
            isGlobalSearching = false,
            globalSearchResults = emptyList(),
        )
    }
}

internal fun BrowsePresenter.setGlobalSearchQuery(query: String) {
    stateUpdates.update { it.copy(globalSearchQuery = query) }
}

internal fun BrowsePresenter.performGlobalSearch() {
    val query = stateUpdates.value.globalSearchQuery.trim()
    if (query.isBlank()) return
    val generation = globalSearchGeneration.incrementAndGet()
    globalSearchJob?.cancel()

    val showNsfw = stateUpdates.value.showNsfw
    val hiddenIds = stateUpdates.value.hiddenSourceIds
    val onlyPinned = stateUpdates.value.globalSearchOnlyPinned
    val pinnedIds = stateUpdates.value.pinnedSourceIds
    val installed = stateUpdates.value.installedExtensions
    val available = stateUpdates.value.availableExtensions

    val sources = stateUpdates.value.sources.filter { source ->
        if (hiddenIds.contains(source.id)) return@filter false
        if (onlyPinned && !pinnedIds.contains(source.id)) return@filter false
        if (!showNsfw && isSourceNsfw(source.id, installed, available)) return@filter false
        true
    }

    stateUpdates.update {
        it.copy(
            isGlobalSearching = sources.isNotEmpty(),
            globalSearchResults = sources.map { s -> GlobalSearchSourceResult(source = s, isLoading = true) },
        )
    }

    globalSearchJob = scope.launch {
        coroutineScope {
            for (source in sources) {
                launch {
                    try {
                        val page = sourceManager.searchManga(source.id, 1, query)
                        currentCoroutineContext().ensureActive()
                        stateUpdates.update { current ->
                            if (generation != globalSearchGeneration.get()) return@update current
                            val updated = current.globalSearchResults.map { item ->
                                if (item.source.id == source.id) {
                                    item.copy(isLoading = false, mangas = page.mangas)
                                } else {
                                    item
                                }
                            }
                            val stillLoading = updated.any { it.isLoading }
                            current.copy(
                                globalSearchResults = updated,
                                isGlobalSearching = stillLoading,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        stateUpdates.update { current ->
                            if (generation != globalSearchGeneration.get()) return@update current
                            val updated = current.globalSearchResults.map { item ->
                                if (item.source.id == source.id) {
                                    item.copy(
                                        isLoading = false,
                                        errorMessage = e.message ?: "Failed to search",
                                        failureReason = mihon.desktop.download.classifyDownloadFailure(e),
                                    )
                                } else {
                                    item
                                }
                            }
                            val stillLoading = updated.any { it.isLoading }
                            current.copy(
                                globalSearchResults = updated,
                                isGlobalSearching = stillLoading,
                            )
                        }
                    }
                }
            }
        }
    }
}
