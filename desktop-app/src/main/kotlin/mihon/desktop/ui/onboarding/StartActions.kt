package mihon.desktop.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

/** The three ways a new user can get manga into the app; shared by the empty library and onboarding. */
enum class StartAction(val testTagSuffix: String) {
    ImportLocal("import-local"),
    RestoreBackup("restore-backup"),
    PairPhone("pair-phone"),
}

/**
 * Action cards for "where to start". [onAction] receives the chosen entry; actions whose callback
 * is unavailable are simply left out by passing a smaller [actions] list.
 */
@Composable
fun StartActionCards(
    onAction: (StartAction) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    actions: List<StartAction> = StartAction.entries,
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("$testTagPrefix-start-actions"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEach { action ->
            val (icon, title, description) = when (action) {
                StartAction.ImportLocal -> Triple(
                    Icons.Rounded.FolderOpen,
                    strings.text(UiText.StartImportLocalTitle),
                    strings.text(UiText.StartImportLocalDescription),
                )
                StartAction.RestoreBackup -> Triple(
                    Icons.Rounded.Restore,
                    strings.text(UiText.StartRestoreBackupTitle),
                    strings.text(UiText.StartRestoreBackupDescription),
                )
                StartAction.PairPhone -> Triple(
                    Icons.Rounded.PhoneAndroid,
                    strings.text(UiText.StartPairPhoneTitle),
                    strings.text(UiText.StartPairPhoneDescription),
                )
            }
            StartActionCard(
                icon = icon,
                title = title,
                description = description,
                onClick = { onAction(action) },
                modifier = Modifier.testTag("$testTagPrefix-start-${action.testTagSuffix}"),
            )
        }
    }
}

@Composable
private fun StartActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Decorative: the title next to it already names the action.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
