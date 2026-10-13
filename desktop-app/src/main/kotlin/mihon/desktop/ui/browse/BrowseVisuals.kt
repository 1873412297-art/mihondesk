package mihon.desktop.ui.browse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.image.ImageRequest
import mihon.desktop.image.LocalImageLoader

/** Where an extension/source icon can come from: installed icon file first, then repo index URL. */
internal fun extensionIconUri(installed: InstalledExtension?, item: ExtensionStoreItem?): String? =
    installed?.iconPath?.takeIf { it.isNotBlank() } ?: item?.iconUrl?.takeIf { it.isNotBlank() }

/** Builds sourceId -> icon uri from installed extensions (preferred) and repo index entries. */
internal fun sourceIconUris(
    installed: List<InstalledExtension>,
    available: List<ExtensionStoreItem>,
): Map<Long, String> {
    val result = HashMap<Long, String>()
    available.forEach { item ->
        val uri = item.iconUrl.takeIf { it.isNotBlank() } ?: return@forEach
        item.sources.forEach { result[it.id] = uri }
    }
    installed.forEach { ext ->
        val uri = ext.iconPath?.takeIf { it.isNotBlank() } ?: return@forEach
        ext.manifest.sources.forEach { result[it.id] = uri }
    }
    return result
}

@Composable
internal fun ExtensionIcon(
    name: String,
    iconUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    dimmed: Boolean = false,
) {
    val loader = LocalImageLoader.current
    var bitmap by remember(iconUri) {
        mutableStateOf<ImageBitmap?>(iconUri?.let { loader?.getFromMemory("uri_$it") })
    }
    LaunchedEffect(iconUri, loader) {
        if (bitmap == null && !iconUri.isNullOrBlank() && loader != null) {
            bitmap = runCatching { loader.load(ImageRequest(uri = iconUri)) }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.25f)
    val alpha = if (dimmed) 0.45f else 1f
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = modifier.size(size).clip(shape).testTag("ext-icon-image"),
        )
    } else {
        val (bg, fg) = letterAvatarColors(name)
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(bg.copy(alpha = bg.alpha * alpha))
                .testTag("ext-icon-letter"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                color = fg.copy(alpha = alpha),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

@Composable
private fun letterAvatarColors(name: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val palette = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.primary to c.onPrimary,
        c.tertiary to c.onTertiary,
    )
    val index = (name.lowercase().hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

enum class BadgeTone { Neutral, Primary, Danger, Positive }

@Composable
internal fun SmallBadge(
    text: String,
    tone: BadgeTone = BadgeTone.Neutral,
    modifier: Modifier = Modifier,
    textTag: String? = null,
) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BadgeTone.Neutral -> c.surfaceVariant to c.onSurfaceVariant
        BadgeTone.Primary -> c.primaryContainer to c.onPrimaryContainer
        BadgeTone.Danger -> c.errorContainer to c.onErrorContainer
        BadgeTone.Positive -> c.secondaryContainer to c.onSecondaryContainer
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .then(if (textTag != null) Modifier.testTag(textTag) else Modifier),
        )
    }
}
