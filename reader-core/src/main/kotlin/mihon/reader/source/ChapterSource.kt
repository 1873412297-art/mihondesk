package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.io.Closeable

interface ChapterSource : Closeable {
    val asset: ReaderChapterAsset

    suspend fun pages(): List<PageDescriptor>

    suspend fun open(pageId: PageId): BoundedPageInput
}

/**
 * Opens [pageId] and closes the input when [block] fails before the decoder takes ownership.
 *
 * Decoders close their input in a `use` block, but cancellation can surface before that block runs
 * (for example on dispatcher entry), which would otherwise leave the page stream and its archive
 * handle open until the chapter closes.
 */
suspend fun <T> ChapterSource.usePageInput(pageId: PageId, block: suspend (BoundedPageInput) -> T): T {
    val input = open(pageId)
    return try {
        block(input)
    } catch (error: Throwable) {
        runCatching { input.close() }.onFailure(error::addSuppressed)
        throw error
    }
}

fun interface ChapterSourceFactory {
    fun create(asset: ReaderChapterAsset): ChapterSource
}
