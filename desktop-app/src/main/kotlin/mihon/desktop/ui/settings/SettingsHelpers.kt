package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.ContentDensity
import mihon.desktop.ui.common.LocalSpacing
import java.nio.file.Path
import javax.swing.JFileChooser

internal fun chooseStorageDirectory(initialPath: String, activePath: Path?, title: String): Path? {
    val initial = runCatching {
        initialPath.takeIf(String::isNotBlank)?.let(Path::of)
    }.getOrNull() ?: activePath
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initial?.toFile()?.let { selected ->
            currentDirectory = when {
                selected.isDirectory -> selected
                selected.parentFile?.isDirectory == true -> selected.parentFile
                else -> null
            }
        }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val selected = chooser.selectedFile?.toPath() ?: return null
    return selected.toAbsolutePath().normalize().takeIf { chooser.selectedFile.isDirectory }
}

internal fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}

internal fun parsePositiveLongSet(value: String): Set<Long> = value
    .split(',', ';', ' ')
    .asSequence()
    .mapNotNull { it.trim().toLongOrNull() }
    .filter { it > 0L }
    .toSet()

internal const val CONTENT_DENSITY_PREVIEW_TEST_TAG = "content-density-preview"

@Composable
internal fun InterfaceDensityCard(current: ContentDensity, onDensityChange: (ContentDensity) -> Unit) {
    val strings = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth().testTag("appearance-interface-density")) {
        Column(
            modifier = Modifier.padding(LocalSpacing.current.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(strings.text(UiText.InterfaceDensity), fontWeight = FontWeight.Bold)
            Text(
                strings.text(UiText.InterfaceDensityHint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    ContentDensity.Standard to strings.text(UiText.DensityStandard),
                    ContentDensity.Compact to strings.text(UiText.DensityCompact),
                ).forEach { (density, label) ->
                    val tag = "content-density-button-${density.name}"
                    if (density == current) {
                        Button(onClick = {}, modifier = Modifier.testTag(tag)) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { onDensityChange(density) },
                            modifier = Modifier.testTag(tag),
                        ) { Text(label) }
                    }
                }
            }
            Text(strings.text(UiText.DensityPreview), style = MaterialTheme.typography.labelMedium)
            val preview = current.spacing
            Row(
                modifier = Modifier.testTag(CONTENT_DENSITY_PREVIEW_TEST_TAG),
                horizontalArrangement = Arrangement.spacedBy(preview.gridGap),
            ) {
                repeat(4) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.width(48.dp).height(68.dp).background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        ),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(preview.listRowGap)) {
                repeat(2) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth(0.6f)
                            .height(preview.listCoverHeight * 0.5f + preview.listRowPadding)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            ),
                    )
                }
            }
        }
    }
}
