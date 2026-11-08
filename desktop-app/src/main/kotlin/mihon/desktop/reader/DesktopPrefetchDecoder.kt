package mihon.desktop.reader

import mihon.reader.image.ImageMetadata
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.ChapterSource
import mihon.reader.source.usePageInput

/** Uses the same sampled decoding operation as the core coordinator for extended prefetch. */
internal suspend fun PageDecoder.decodePrefetchedPage(
    source: ChapterSource,
    pageId: PageId,
    metadata: ImageMetadata,
    key: TileKey,
) = if (key.sampleSize == 1) {
    source.usePageInput(pageId) { input ->
        decodeFull(input, metadata, FrameId(pageId, 0))
    }
} else {
    source.usePageInput(pageId) { input ->
        decodeRegion(
            input,
            metadata,
            TileRequest(
                key,
                (key.bounds.width + key.sampleSize - 1) / key.sampleSize,
                (key.bounds.height + key.sampleSize - 1) / key.sampleSize,
            ),
        )
    }
}
