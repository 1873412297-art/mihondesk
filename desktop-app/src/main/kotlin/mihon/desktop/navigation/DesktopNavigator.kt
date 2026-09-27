package mihon.desktop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDesktopNavigator = staticCompositionLocalOf<DesktopNavigator?> { null }

@Composable
fun DesktopBackHandler(
    enabled: Boolean = true,
    onBack: () -> Boolean,
) {
    val navigator = LocalDesktopNavigator.current ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(navigator, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val unregister = navigator.registerBackHandler { currentOnBack() }
        onDispose { unregister() }
    }
}

class DesktopNavigator(
    private val initialDestination: DesktopDestination,
    private val onDestinationChanged: (DesktopDestination) -> Unit,
) {
    private val _backStack = mutableListOf<DesktopRoute>(initialDestination)
    val backStack: List<DesktopRoute> get() = _backStack

    var current: DesktopRoute by mutableStateOf(initialDestination)
        private set

    var currentDestination: DesktopDestination by mutableStateOf(initialDestination)
        private set

    private val backHandlers = mutableListOf<() -> Boolean>()

    val canPop: Boolean
        get() = _backStack.size > 1 || backHandlers.isNotEmpty()

    private fun updateCurrentDestination() {
        for (i in _backStack.lastIndex downTo 0) {
            val item = _backStack[i]
            if (item is DesktopDestination) {
                currentDestination = item
                return
            }
        }
        currentDestination = initialDestination
    }

    fun registerBackHandler(handler: () -> Boolean): () -> Unit {
        backHandlers.add(handler)
        return { backHandlers.remove(handler) }
    }

    fun push(route: DesktopRoute) {
        if (route == current) return
        if (_backStack.size >= 50) {
            _backStack.removeAt(0)
        }
        _backStack.add(route)
        current = route
        updateCurrentDestination()
    }

    fun pop(): Boolean {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.lastIndex)
            val newTop = _backStack.last()
            current = newTop
            updateCurrentDestination()
            return true
        }
        return false
    }

    fun replace(route: DesktopRoute) {
        if (_backStack.isNotEmpty()) {
            _backStack[_backStack.lastIndex] = route
        } else {
            _backStack.add(route)
        }
        current = route
        updateCurrentDestination()
    }

    fun navigate(destination: DesktopDestination) {
        if (destination == current) return
        val existingIndex = _backStack.indexOf(destination)
        if (existingIndex >= 0) {
            while (_backStack.size > existingIndex + 1) {
                _backStack.removeAt(_backStack.lastIndex)
            }
            current = _backStack.last()
            updateCurrentDestination()
            onDestinationChanged(destination)
            return
        }
        push(destination)
        onDestinationChanged(destination)
    }

    fun navigate(destination: DesktopDestination.Reader) {
        if (destination == current) return
        if (current is DesktopDestination.Reader) {
            replace(destination)
        } else {
            push(destination)
        }
    }

    fun navigate(destination: DesktopDestination.MangaDetails) {
        if (destination == current) return
        val existingIndex = _backStack.indexOf(destination)
        if (existingIndex >= 0) {
            while (_backStack.size > existingIndex + 1) {
                _backStack.removeAt(_backStack.lastIndex)
            }
            current = _backStack.last()
            updateCurrentDestination()
            return
        }
        if (current is DesktopDestination.Reader) {
            replace(destination)
        } else {
            push(destination)
        }
    }

    fun navigate(destination: DesktopDestination.Upcoming) {
        if (destination == current) return
        push(destination)
    }

    fun navigate(route: DesktopRoute) {
        when (route) {
            is DesktopDestination -> navigate(route)
            is DesktopDestination.Reader -> navigate(route)
            is DesktopDestination.MangaDetails -> navigate(route)
            is DesktopDestination.Upcoming -> navigate(route)
        }
    }

    fun back(): Boolean {
        for (handler in backHandlers.reversed()) {
            if (handler()) return true
        }
        return pop()
    }
}
