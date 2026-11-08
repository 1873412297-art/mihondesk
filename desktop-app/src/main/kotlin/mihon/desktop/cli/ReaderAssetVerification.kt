package mihon.desktop.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import mihon.reader.image.IntRect
import mihon.reader.prefetch.PageLoadCoordinator
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import mihon.reader.source.usePageInput

internal suspend fun ReaderSession.awaitReady(chapterId: Long) =
    awaitState { it.chapterId == chapterId && it.loadState is ReaderLoadState.Ready }

internal suspend fun ReaderSession.awaitState(predicate: (mihon.reader.session.ReaderState) -> Boolean) =
    withTimeoutOrNull(10_000) { state.first(predicate) }
        ?: error("timed out waiting for reader state; last state=${state.value}")

internal suspend fun PackagedReaderVerifier.verifyAsset(
    label: String,
    asset: ReaderChapterAsset,
    factory: mihon.desktop.reader.DesktopReaderFactory,
    scope: CoroutineScope,
): AssetVerification {
    val source = factory.sourceFactory.create(asset)
    source.use { activeSource ->
        PageLoadCoordinator(factory.decoder, factory.cache, scope).use { coordinator ->
            val pages = coordinator.openChapter(activeSource)
            check(pages.isNotEmpty()) { "$label exposed no pages" }
            var decoded = 0
            var corrupt = false
            var successfulAfterCorrupt = false
            val gifHashes = linkedSetOf<String>()
            pages.forEach { page ->
                val lowerName = page.id.entryName.lowercase()
                val expectedCorrupt = lowerName.substringAfterLast('/').startsWith("35-corrupt.") || label == "cbr"
                if (expectedCorrupt) {
                    val outcome = runCatching { coordinator.loadVisible(page.id) }
                    val failure = outcome.exceptionOrNull()
                    check(failure is ReaderFailure.CorruptImage || failure is ReaderFailure.UnsupportedImage) {
                        "$label corrupt page did not fail as a typed image error; actual=" +
                            "${failure?.javaClass?.name}:${failure?.message}; page=${page.id}; " +
                            "decoded=${outcome.getOrNull()?.tile?.image?.let(::hashImage)}"
                    }
                    corrupt = true
                    return@forEach
                }
                if (lowerName.contains("extreme")) {
                    val loaded = coordinator.loadRegion(
                        page.id,
                        IntRect(0, 0, EXTREME_TILE_SIZE, EXTREME_TILE_SIZE),
                    )
                    check(loaded.tile.image.width == EXTREME_TILE_SIZE)
                    check(loaded.tile.image.height == EXTREME_TILE_SIZE)
                    check(
                        (loaded.tile.image.getRGB(0, EXTREME_TILE_SIZE - 1) and 0xff) ==
                            (EXTREME_TILE_SIZE - 1) % 251,
                    )
                    decoded++
                    if (corrupt) successfulAfterCorrupt = true
                    return@forEach
                }

                val metadata = activeSource.usePageInput(page.id) { factory.decoder.probe(it) }
                if (metadata.frameCount > 1) {
                    val first = coordinator.loadVisible(page.id, 0)
                    gifHashes += hashImage(first.tile.image)
                    delay(metadata.frameDurationsMillis.first().coerceAtLeast(20L))
                    val second = coordinator.loadVisible(page.id, 1)
                    gifHashes += hashImage(second.tile.image)
                    check(gifHashes.size == 2) { "$label GIF frames were identical" }
                    decoded += 2
                } else {
                    val loaded = coordinator.loadVisible(page.id)
                    check(loaded.tile.image.width > 0 && loaded.tile.image.height > 0)
                    if (lowerName.contains("transparent")) {
                        check((loaded.tile.image.getRGB(0, 0) ushr 24) == 0) {
                            "$label transparent page lost alpha"
                        }
                    }
                    if (lowerName.contains("opaque")) {
                        check((loaded.tile.image.getRGB(0, 0) ushr 24) == 0xff) {
                            "$label opaque page gained transparency"
                        }
                    }
                    decoded++
                }
                if (corrupt) successfulAfterCorrupt = true
            }
            return AssetVerification(pages.size, decoded, gifHashes.toList(), corrupt, successfulAfterCorrupt)
        }
    }
}
