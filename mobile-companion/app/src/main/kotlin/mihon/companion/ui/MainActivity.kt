package mihon.companion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mihon.companion.CompanionApp
import mihon.companion.CompanionRuntime
import mihon.companion.R
import mihon.companion.ui.detail.DetailPresenter
import mihon.companion.ui.detail.DetailScreen
import mihon.companion.ui.history.HistoryPresenter
import mihon.companion.ui.history.HistoryScreen
import mihon.companion.ui.library.LibraryPresenter
import mihon.companion.ui.library.LibraryScreen
import mihon.companion.ui.sync.SyncPresenter
import mihon.companion.ui.sync.SyncScreen
import mihon.companion.ui.theme.CompanionTheme
import mihon.sync.core.model.MangaKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val runtime = (application as CompanionApp).runtime
        setContent { CompanionTheme { CompanionAppContent(runtime) } }
    }
}

@Composable
private fun CompanionAppContent(runtime: CompanionRuntime) {
    val navigator = viewModel { AppNavigator(runtime) }
    val nav by navigator.nav.collectAsStateWithLifecycle()
    BackHandler(enabled = nav.openManga != null) { navigator.back() }
    LifecycleStartEffect(navigator) {
        navigator.onForeground()
        onStopOrDispose { }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (nav.openManga == null) BottomBar(nav.tab, navigator::select) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val open = nav.openManga
        Box(modifier) {
            when {
                open != null -> DetailRoute(runtime, open, onBack = { navigator.back() })
                nav.tab == Tab.Library -> LibraryRoute(runtime, navigator::open)
                nav.tab == Tab.History -> HistoryRoute(runtime, navigator::open)
                else -> SyncRoute(runtime)
            }
        }
    }
}

@Composable
private fun LibraryRoute(runtime: CompanionRuntime, onOpen: (MangaKey) -> Unit) {
    val presenter = viewModel { LibraryPresenter(runtime.library.library) }
    val state by presenter.state.collectAsStateWithLifecycle()
    LibraryScreen(state, presenter::onAction, onOpen)
}

@Composable
private fun HistoryRoute(runtime: CompanionRuntime, onOpen: (MangaKey) -> Unit) {
    val presenter = viewModel { HistoryPresenter(runtime.library.library) }
    val state by presenter.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onOpen)
}

@Composable
private fun DetailRoute(runtime: CompanionRuntime, key: MangaKey, onBack: () -> Unit) {
    val presenter = viewModel(key = key.toKeyString()) {
        DetailPresenter(
            key = key,
            store = runtime.library,
            deviceId = runtime.sync::deviceId,
            onEdited = { if (runtime.sync.status.value.signedIn) runtime.scheduler.requestSoon() },
        )
    }
    val state by presenter.state.collectAsStateWithLifecycle()
    DetailScreen(state, presenter::onAction, onBack)
}

@Composable
private fun SyncRoute(runtime: CompanionRuntime) {
    val resolver = LocalContext.current.contentResolver
    val presenter = viewModel { SyncPresenter(runtime, resolver) }
    val state by presenter.state.collectAsStateWithLifecycle()
    SyncScreen(state, presenter::onAction)
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        listOf(
            Triple(Tab.Library, Icons.Outlined.CollectionsBookmark, R.string.nav_library),
            Triple(Tab.History, Icons.Outlined.History, R.string.nav_history),
            Triple(Tab.Sync, Icons.Outlined.Sync, R.string.nav_sync),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}
