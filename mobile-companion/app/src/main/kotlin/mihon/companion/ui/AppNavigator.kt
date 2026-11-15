package mihon.companion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.companion.CompanionRuntime
import mihon.sync.core.model.MangaKey

enum class Tab { Library, History, Sync }

data class NavState(val tab: Tab = Tab.Library, val openManga: MangaKey? = null)

/** App-level navigation plus the foreground refresh; screens only see their own presenter. */
class AppNavigator(private val runtime: CompanionRuntime) : ViewModel() {
    private val state = MutableStateFlow(NavState())
    val nav: StateFlow<NavState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            runtime.library.load()
            val status = runtime.sync.status.value
            runtime.scheduler.apply(status.signedIn && status.autoSync)
        }
    }

    /** Every time the app comes to the foreground: read Mihon's newest backup, then sync with Drive. */
    fun onForeground() {
        viewModelScope.launch {
            runtime.library.load()
            runtime.refresher.refreshOnForeground()
        }
    }

    fun select(tab: Tab) {
        state.value = NavState(tab = tab)
    }

    fun open(key: MangaKey) {
        state.value = state.value.copy(openManga = key)
    }

    /** Returns false when there is nothing to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (state.value.openManga == null) return false
        state.value = state.value.copy(openManga = null)
        return true
    }
}
