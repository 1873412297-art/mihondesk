package mihon.desktop.ui.browse

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.extension.setSourcePreferenceValue

internal fun BrowsePresenter.reloadPreferences() {
    val prefs = preferenceStore.load()
    val stored = preferenceStore.property(BrowsePresenter.PREF_KEY_PINNED_SOURCES) ?: ""
    val ids = stored.split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()
    stateUpdates.update {
        it.copy(
            pinnedSourceIds = ids,
            hiddenSourceIds = prefs.hiddenSourceIds,
            showNsfw = prefs.showNsfwSources,
            globalSearchOnlyPinned = prefs.globalSearchOnlyPinned,
        )
    }
}

internal fun BrowsePresenter.hideSource(sourceId: Long) {
    val updated = stateUpdates.value.hiddenSourceIds + sourceId
    preferenceStore.updatePreferences { it.copy(hiddenSourceIds = updated) }
    stateUpdates.update { it.copy(hiddenSourceIds = updated) }
    val sourceName = stateUpdates.value.sources.find { it.id == sourceId }?.name ?: "Source"
    scope.launch {
        snackbarUpdates.emit("HIDDEN:$sourceId:$sourceName")
    }
}

internal fun BrowsePresenter.unhideSource(sourceId: Long) {
    val updated = stateUpdates.value.hiddenSourceIds - sourceId
    preferenceStore.updatePreferences { it.copy(hiddenSourceIds = updated) }
    stateUpdates.update { it.copy(hiddenSourceIds = updated) }
}

internal fun BrowsePresenter.resetHiddenSources() {
    preferenceStore.updatePreferences { it.copy(hiddenSourceIds = emptySet()) }
    stateUpdates.update { it.copy(hiddenSourceIds = emptySet()) }
}

internal fun BrowsePresenter.setShowNsfw(show: Boolean) {
    preferenceStore.updatePreferences { it.copy(showNsfwSources = show) }
    stateUpdates.update { it.copy(showNsfw = show) }
}

internal fun BrowsePresenter.setGlobalSearchOnlyPinned(onlyPinned: Boolean) {
    preferenceStore.updatePreferences { it.copy(globalSearchOnlyPinned = onlyPinned) }
    stateUpdates.update { it.copy(globalSearchOnlyPinned = onlyPinned) }
    if (stateUpdates.value.isGlobalSearchOpen && stateUpdates.value.globalSearchQuery.isNotBlank()) {
        performGlobalSearch()
    }
}

internal fun BrowsePresenter.setSourceLanguageFilter(lang: String?) {
    stateUpdates.update { it.copy(sourceLanguageFilter = lang) }
}

internal fun BrowsePresenter.savePinnedSources(ids: Set<Long>) {
    preferenceStore.update {
        setProperty(BrowsePresenter.PREF_KEY_PINNED_SOURCES, ids.joinToString(","))
    }
    stateUpdates.update { it.copy(pinnedSourceIds = ids) }
}

internal fun BrowsePresenter.togglePinSource(sourceId: Long) {
    val current = stateUpdates.value.pinnedSourceIds
    val updated = if (current.contains(sourceId)) {
        current - sourceId
    } else {
        current + sourceId
    }
    savePinnedSources(updated)
}

internal fun BrowsePresenter.setTab(tab: BrowseTab) {
    stateUpdates.update { it.copy(selectedTab = tab) }
    if (tab == BrowseTab.Migration) {
        refreshMigrationCounts()
    }
}

internal fun BrowsePresenter.setSearchQuery(query: String) {
    stateUpdates.update { it.copy(searchQuery = query) }
}

internal fun BrowsePresenter.setSourcePreferenceValue(sourceId: Long, key: String, value: String) {
    sourceManager.setSourcePreferenceValue(sourceId, key, value)
    stateUpdates.update { current ->
        val updatedValues = current.sourcePreferenceValues[sourceId].orEmpty().toMutableMap()
        updatedValues[key] = value
        current.copy(
            sourcePreferenceValues = current.sourcePreferenceValues + (sourceId to updatedValues),
        )
    }
}

internal fun BrowsePresenter.addRepository(repoUrl: String) {
    storeService.addRepository(repoUrl)
    refresh()
}

internal fun BrowsePresenter.removeRepository(repoUrl: String) {
    storeService.removeRepository(repoUrl)
    refresh()
}

internal fun BrowsePresenter.updateAllPending() {
    val installedMap = stateUpdates.value.installedExtensions.associateBy { it.pkg }
    val toUpdate = stateUpdates.value.availableExtensions.filter { available ->
        val inst = installedMap[available.pkg]
        inst != null && available.versionCode > inst.manifest.versionCode
    }

    installItems(toUpdate)
}
