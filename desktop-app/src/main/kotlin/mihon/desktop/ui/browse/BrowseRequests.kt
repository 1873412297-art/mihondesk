package mihon.desktop.ui.browse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.desktop.extension.ExtensionStoreItem
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

/** Lets code outside Browse (the Ctrl+K palette) open a source manga inside the Browse tab. */
object BrowseRequests {
    private val _pending = MutableStateFlow<BrowseNavigationState?>(null)
    val pending: StateFlow<BrowseNavigationState?> = _pending.asStateFlow()

    fun openManga(source: SourceDescriptor, manga: SManga) {
        _pending.value = BrowseNavigationState.MangaDetail(source, manga)
    }

    private val _pendingInstall = MutableStateFlow<ExtensionStoreItem?>(null)
    val pendingInstall: StateFlow<ExtensionStoreItem?> = _pendingInstall.asStateFlow()
    private val _pendingMigration = MutableStateFlow<SourceWithMangaCount?>(null)
    val pendingMigration: StateFlow<SourceWithMangaCount?> = _pendingMigration.asStateFlow()

    /** Installs a repo extension after the user clicked "install extension" in the activity center. */
    fun installExtension(item: ExtensionStoreItem) {
        _pendingInstall.value = item
    }

    fun openMigration(source: SourceWithMangaCount) {
        _pendingMigration.value = source
    }

    fun consumeInstall() {
        _pendingInstall.value = null
    }

    fun consumeMigration() {
        _pendingMigration.value = null
    }

    fun consume() {
        _pending.value = null
    }
}
