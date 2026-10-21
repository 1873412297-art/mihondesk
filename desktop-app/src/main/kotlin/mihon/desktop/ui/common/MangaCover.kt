package mihon.desktop.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mihon.desktop.image.DesktopImageLoader
import mihon.desktop.image.ImageRequest
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.image.LocalImageLoader
import java.net.URI
import java.nio.file.Path

fun coverHeaders(baseUrl: String?, thumbnailUrl: String? = null): Map<String, String> {
    val referer = when {
        !baseUrl.isNullOrBlank() -> baseUrl.trim()
        !thumbnailUrl.isNullOrBlank() -> {
            try {
                val uri = URI(thumbnailUrl.trim())
                if (uri.scheme != null && uri.host != null) {
                    val portPart = if (uri.port != -1) ":${uri.port}" else ""
                    "${uri.scheme}://${uri.host}$portPart"
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        else -> null
    }
    return if (referer != null) mapOf("Referer" to referer) else emptyMap()
}

/** Grid covers are decoded from (and cached as) thumbnails this size; enough for 2x HiDPI cards. */
const val DEFAULT_COVER_THUMBNAIL_SIZE = 480

@Composable
fun MangaCover(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    mangaId: Long? = null,
    localMangaPath: Path? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(4.dp),
    imageLoader: DesktopImageLoader? = LocalImageLoader.current,
    headers: Map<String, String> = emptyMap(),
    onCoverHttpError: ((Int) -> Unit)? = null,
    /** Longest edge (px) of the cached thumbnail to load; null loads the full-size image. */
    thumbnailSize: Int? = DEFAULT_COVER_THUMBNAIL_SIZE,
) {
    val fallbackHeaders = remember(thumbnailUrl) { coverHeaders(null, thumbnailUrl) }
    val effectiveHeaders = if (headers.isNotEmpty()) {
        headers
    } else {
        fallbackHeaders
    }
    val currentOnCoverHttpError by rememberUpdatedState(onCoverHttpError)
    var bitmap by remember(thumbnailUrl, mangaId, localMangaPath, effectiveHeaders) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var loading by remember(thumbnailUrl, mangaId, localMangaPath, effectiveHeaders) { mutableStateOf(true) }

    LaunchedEffect(thumbnailUrl, mangaId, localMangaPath, imageLoader, effectiveHeaders, thumbnailSize) {
        if (imageLoader == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val loaded = imageLoader.load(
            ImageRequest(
                uri = thumbnailUrl,
                mangaId = mangaId,
                localMangaPath = localMangaPath,
                headers = effectiveHeaders,
                onHttpError = currentOnCoverHttpError,
                maxThumbnailSize = thumbnailSize,
            ),
        )
        bitmap = loaded
        loading = false
    }

    Surface(
        modifier = modifier.clip(shape).testTag("manga-cover-${mangaId ?: "item"}"),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val current = bitmap
            when {
                current != null -> {
                    Image(
                        bitmap = current,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
                loading -> {
                    // Static placeholder: no per-cover animation while a whole shelf loads.
                    Box(modifier = Modifier.fillMaxSize().testTag("manga-cover-placeholder"))
                }
                else -> {
                    Text(
                        text = contentDescription?.take(2)?.uppercase() ?: "📖",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
