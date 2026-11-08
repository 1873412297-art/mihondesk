package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionDetailsTopBar(
    repoUrl: String?,
    showMenuTrust: Boolean,
    showMenuRevoke: Boolean,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onTrust: () -> Unit,
    onRevoke: () -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    val strings = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = strings.extensionInfo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("extension-details-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = strings.mangaDetailBack,
                )
            }
        },
        actions = {
            ExtensionDetailsTopBarActions(
                repoUrl = repoUrl,
                showMenu = showMenu,
                showMenuTrust = showMenuTrust,
                showMenuRevoke = showMenuRevoke,
                onOpenMenu = { showMenu = true },
                onDismissMenu = { showMenu = false },
                onToggleEnabled = onToggleEnabled,
                onClearCookies = onClearCookies,
                onTrust = onTrust,
                onRevoke = onRevoke,
                onShowSnackbar = onShowSnackbar,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ExtensionDetailsTopBarActions(
    repoUrl: String?,
    showMenu: Boolean,
    showMenuTrust: Boolean,
    showMenuRevoke: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onTrust: () -> Unit,
    onRevoke: () -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    val strings = LocalStrings.current
    if (repoUrl != null) {
        IconButton(onClick = { openExtensionRepository(repoUrl) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = strings.extensionOpenRepo,
            )
        }
    }
    Box {
        IconButton(onClick = onOpenMenu) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = strings.text(UiText.More),
            )
        }
        ExtensionDetailsMenu(
            expanded = showMenu,
            showTrust = showMenuTrust,
            showRevoke = showMenuRevoke,
            onDismiss = onDismissMenu,
            onEnableAll = { onToggleEnabled(true) },
            onDisableAll = { onToggleEnabled(false) },
            onClearCookies = onClearCookies,
            onTrust = onTrust,
            onRevoke = onRevoke,
            onShowSnackbar = onShowSnackbar,
        )
    }
}

@Composable
private fun ExtensionDetailsMenu(
    expanded: Boolean,
    showTrust: Boolean,
    showRevoke: Boolean,
    onDismiss: () -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onClearCookies: () -> Unit,
    onTrust: () -> Unit,
    onRevoke: () -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    val strings = LocalStrings.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(strings.extensionEnableAll) },
            onClick = {
                onDismiss()
                onEnableAll()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.extensionDisableAll) },
            onClick = {
                onDismiss()
                onDisableAll()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(strings.extensionClearCookies) },
            onClick = {
                onDismiss()
                onClearCookies()
                onShowSnackbar(strings.extensionCookiesCleared)
            },
        )
        if (showRevoke) {
            DropdownMenuItem(
                text = { Text(strings.extensionRevoke) },
                onClick = {
                    onDismiss()
                    onRevoke()
                },
            )
        }
        if (showTrust) {
            DropdownMenuItem(
                text = { Text(strings.extensionTrust) },
                onClick = {
                    onDismiss()
                    onTrust()
                },
            )
        }
    }
}
