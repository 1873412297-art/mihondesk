package mihon.desktop.ui.browse

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourceState
import mihon.desktop.i18n.LocalStrings
import java.time.format.DateTimeFormatter

@Composable
internal fun DetailsHeader(
    extension: InstalledExtension,
    icon: ImageBitmap?,
    effectiveTrustStatus: ExtensionTrustStatus,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    sources: List<SourceState>,
    dateFormatter: DateTimeFormatter,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onExtIncognitoChange: (Boolean) -> Unit,
    onCopyDebugInfo: () -> Unit,
    onTrustClick: () -> Unit,
    onRevokeClick: () -> Unit,
    showTrustButton: Boolean,
    showRevokeButton: Boolean,
) {
    Column {
        ExtensionIdentityHeader(
            extension = extension,
            icon = icon,
            onCopyDebugInfo = onCopyDebugInfo,
        )
        ExtensionInfoRow(extension = extension)
        ExtensionActionButtons(
            extension = extension,
            updateItem = updateItem,
            sources = sources,
            showTrustButton = showTrustButton,
            showRevokeButton = showRevokeButton,
            onClickUninstall = onClickUninstall,
            onClickUpdate = onClickUpdate,
            onToggleEnabled = onToggleEnabled,
            onClearCookies = onClearCookies,
            onOpenSourcePreferences = onOpenSourcePreferences,
            onTrustClick = onTrustClick,
            onRevokeClick = onRevokeClick,
        )
        ExtensionIncognitoPreference(
            isExtensionIncognito = isExtensionIncognito,
            onExtIncognitoChange = onExtIncognitoChange,
        )
        ExtensionMetadataRow(
            extension = extension,
            effectiveTrustStatus = effectiveTrustStatus,
            updateItem = updateItem,
            dateFormatter = dateFormatter,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    }
}

@Composable
private fun ExtensionIdentityHeader(
    extension: InstalledExtension,
    icon: ImageBitmap?,
    onCopyDebugInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExtensionIcon(
            icon = icon,
            name = extension.manifest.name,
            onCopyDebugInfo = onCopyDebugInfo,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = extension.manifest.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("extension-details-name"),
        )
        Text(
            text = extension.pkg.substringAfter("eu.kanade.tachiyomi.extension."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("extension-details-package"),
        )
    }
}

@Composable
private fun ExtensionIcon(
    icon: ImageBitmap?,
    name: String,
    onCopyDebugInfo: () -> Unit,
) {
    val iconModifier = Modifier
        .size(112.dp)
        .clip(RoundedCornerShape(24.dp))
        .clickable(onClick = onCopyDebugInfo)
        .testTag("extension-details-icon")
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = iconModifier,
        )
    } else {
        Surface(
            modifier = iconModifier,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ExtensionInfoRow(extension: InstalledExtension) {
    val strings = LocalStrings.current
    var showNsfwWarning by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoText(
            modifier = Modifier.weight(1f),
            primaryText = extension.manifest.version,
            secondaryText = strings.extensionVersion,
            primaryTag = "extension-details-version",
        )
        InfoDivider()
        InfoText(
            modifier = Modifier.weight(if (extension.manifest.isNsfw) 1.5f else 1f),
            primaryText = LocaleHelper.getSourceDisplayName(extension.manifest.lang),
            secondaryText = strings.extensionLanguage,
            primaryTag = "extension-details-language",
        )
        if (extension.manifest.isNsfw) {
            InfoDivider()
            InfoText(
                modifier = Modifier.weight(1f),
                primaryText = strings.extensionNsfwShort,
                primaryTextStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                ),
                secondaryText = strings.extensionAgeRating,
                primaryTag = "extension-details-nsfw",
                onClick = { showNsfwWarning = true },
            )
        }
    }
    if (showNsfwWarning) {
        NsfwWarningDialog(onClickConfirm = { showNsfwWarning = false })
    }
}

@Composable
private fun InfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    primaryTag: String? = null,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(if (primaryTag != null) Modifier.testTag(primaryTag) else Modifier)
            .then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )
        Text(
            text = secondaryText + if (onClick != null) " ⓘ" else "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
    )
}
