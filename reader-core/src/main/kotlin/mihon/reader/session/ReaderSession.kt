package mihon.reader.session

import kotlinx.coroutines.flow.StateFlow
import mihon.reader.model.PageId

interface ReaderSession {
    val state: StateFlow<ReaderState>

    suspend fun open(chapterId: Long)

    /** Opens [chapterId], on the last page when [landAtEnd] is set (paging back into a chapter). */
    suspend fun open(chapterId: Long, landAtEnd: Boolean) {
        open(chapterId)
    }

    fun dispatch(action: ReaderAction)

    suspend fun retry(pageId: PageId)

    suspend fun flushProgress()

    suspend fun closeAndFlush()

    fun cancelWithoutFlush()
}
