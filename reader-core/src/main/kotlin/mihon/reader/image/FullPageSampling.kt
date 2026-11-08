package mihon.reader.image

import mihon.reader.model.FrameId
import mihon.reader.model.PageId

/** Shares the page and spread sampling budget between demand loads and extended prefetch. */
fun fullPageTileKey(
    pageId: PageId,
    frameId: FrameId?,
    metadata: ImageMetadata,
    maxFullPagePixels: Long,
): TileKey {
    require(maxFullPagePixels > 0) { "maxFullPagePixels must be positive" }
    var sampleSize = 1
    if (!metadata.isAnimated) {
        while (
            ((metadata.width.toLong() + sampleSize - 1) / sampleSize) *
            ((metadata.height.toLong() + sampleSize - 1) / sampleSize) > maxFullPagePixels
        ) {
            sampleSize = Math.multiplyExact(sampleSize, 2)
        }
    }
    return TileKey(
        pageId = pageId,
        frameId = frameId.takeIf { metadata.isAnimated },
        bounds = IntRect(0, 0, metadata.width, metadata.height),
        sampleSize = sampleSize,
    )
}
