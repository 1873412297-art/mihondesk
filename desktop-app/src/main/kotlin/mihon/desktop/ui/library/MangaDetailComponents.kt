package mihon.desktop.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.text

@Composable
internal fun DetailActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag), content = content)
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag), content = content)
    }
}

internal fun chooseCoverImage(title: String): java.nio.file.Path? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val l = name.lowercase()
        l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
    }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return java.nio.file.Path.of(dir, file)
}

internal fun decodeGenres(raw: String): List<String> = try {
    Json.decodeFromString<List<String>>(raw)
} catch (_: Exception) {
    emptyList()
}

@Composable
internal fun DetailMessage(
    message: String,
    tag: String,
    onBack: () -> Unit,
    showBack: Boolean,
    onRetry: (() -> Unit)?,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyLarge)
        onRetry?.let {
            FilledTonalButton(onClick = it, modifier = Modifier.testTag("manga-detail-retry")) {
                Text(strings.libraryRetry)
            }
        }
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("manga-detail-back")) {
                Text(strings.mangaDetailBackToLibrary)
            }
        }
    }
}

@Composable
internal fun DetailLoading(showBack: Boolean, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val strings = LocalStrings.current
    LaunchedEffect(showBack) {
        if (showBack) focusRequester.requestFocus()
    }
    Column(
        Modifier.fillMaxSize().then(
            if (showBack) {
                Modifier.focusRequester(focusRequester).onPreviewKeyEvent {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }.focusable()
            } else {
                Modifier
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("manga-detail-loading"))
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("manga-detail-back")) {
                Text(strings.mangaDetailBack)
            }
        }
    }
}

@Composable
internal fun MetadataLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ExpandableMangaDescription(description: String) {
    val strings = LocalStrings.current
    val cleanDescription = remember(description) {
        description.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
    }
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            strings.mangaDetailDescriptionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cleanDescription,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
        if (cleanDescription.length > 120) {
            Text(
                text = if (expanded) strings.mangaDetailShowLess else strings.mangaDetailShowMore,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun MangaStatusBadge(status: Long, strings: mihon.desktop.i18n.DesktopStrings) {
    val (dotColor, label) = when (status) {
        1L -> Color(0xFF4CAF50) to strings.mangaDetailStatusOngoing
        2L -> MaterialTheme.colorScheme.primary to strings.mangaDetailStatusCompleted
        3L -> MaterialTheme.colorScheme.error to strings.mangaDetailStatusLicensed
        4L -> MaterialTheme.colorScheme.primary to strings.mangaDetailStatusPublishingFinished
        5L -> MaterialTheme.colorScheme.error to strings.statsStatusCancelled
        6L -> Color(0xFFF57C00) to strings.statsStatusHiatus
        else -> MaterialTheme.colorScheme.outline to strings.mangaDetailStatusUnknown
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = dotColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = dotColor,
            )
        }
    }
}
