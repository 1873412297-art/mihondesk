package mihon.desktop.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.text

/** Banner shown when the manga's source extension is missing, with install/ignore actions. */
@Composable
internal fun MissingSourceBanner(
    missing: MissingSourceInfo?,
    strings: DesktopStrings,
    onInstall: (ExtensionStoreItem) -> Unit,
    onIgnore: () -> Unit,
) {
    missing?.let { missing ->
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("missing-source-banner"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val bannerTitle = if (missing.extension != null) {
                        strings.missingSourceBannerTitle(
                            extName = missing.extension.name.ifBlank {
                                strings.missingSourceUnknownExtension
                            },
                            mangaCount = missing.mangaCount,
                        )
                    } else {
                        strings.missingSourceBannerNotFound
                    }
                    Text(
                        text = bannerTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (missing.extension != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { onInstall(missing.extension) },
                            modifier = Modifier.testTag("missing-source-install-btn"),
                        ) {
                            Text(strings.missingSourceBannerInstall)
                        }
                        TextButton(
                            onClick = onIgnore,
                            modifier = Modifier.testTag("missing-source-ignore-btn"),
                        ) {
                            Text(strings.missingSourceBannerIgnore)
                        }
                    }
                }
            }
        }
    }
}
