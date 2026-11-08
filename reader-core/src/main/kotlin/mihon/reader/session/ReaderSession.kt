package mihon.reader.session

import kotlinx.coroutines.Job
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

    /**
     * Stops loading without flushing progress and returns the job that releases chapter resources.
     * The caller must let this job finish before cancelling the session's coroutine scope.
     */
    fun cancelWithoutFlush(): Job
}
