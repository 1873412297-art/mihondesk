package mihon.desktop.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Queue edits: retrying, clearing, reordering and moving whole manga groups. */
internal class DownloadQueueEditor(
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val persistence: DownloadQueuePersistence,
    private val startLoop: () -> Unit,
    private val pauseChapter: (Long) -> Boolean,
    private val resumeChapter: (Long) -> Boolean,
    private val cancelChapter: (Long) -> DesktopDownload?,
) {

    @Synchronized
    fun retry(chapterId: Long) {
        retryFailed { it.chapterId == chapterId }
    }

    @Synchronized
    fun retryAllFailed() {
        retryFailed { true }
    }

    fun retryFailed(select: (DesktopDownload) -> Boolean) {
        if (queueState.value.none { it.status == DownloadStatus.ERROR && select(it) }) return
        queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.ERROR && select(item)) {
                    item.copy(
                        status = DownloadStatus.QUEUED,
                        error = null,
                        failureReason = null,
                        pages = item.pages.map { page ->
                            if (page.status ==
                                PageStatus.ERROR
                            ) {
                                page.copy(status = PageStatus.QUEUE, error = null)
                            } else {
                                page
                            }
                        },
                    )
                } else {
                    item
                }
            }
        }
        persistence.persist()
        startLoop()
    }

    @Synchronized
    fun clearCompleted(): List<DesktopDownload> {
        val completed = queueState.value.filter { it.status == DownloadStatus.COMPLETED }
        queueState.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } }
        persistence.persist()
        return completed
    }

    @Synchronized
    fun moveUp(chapterId: Long): Boolean {
        val list = queueState.value.toMutableList()
        val index = list.indexOfFirst { it.chapterId == chapterId }
        if (index <= 0) return false
        val item = list.removeAt(index)
        list.add(index - 1, item)
        queueState.value = list
        persistence.persist()
        return true
    }

    @Synchronized
    fun moveDown(chapterId: Long): Boolean {
        val list = queueState.value.toMutableList()
        val index = list.indexOfFirst { it.chapterId == chapterId }
        if (index < 0 || index >= list.lastIndex) return false
        val item = list.removeAt(index)
        list.add(index + 1, item)
        queueState.value = list
        persistence.persist()
        return true
    }

    @Synchronized
    fun reorder(fromIndex: Int, toIndex: Int): Boolean {
        val list = queueState.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return false
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        queueState.value = list
        persistence.persist()
        return true
    }

    /** Pauses every queued or downloading chapter of [mangaId]. Returns how many were paused. */
    @Synchronized
    fun pauseManga(mangaId: Long): Int = chapterIdsOf(mangaId) {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }.count { pauseChapter(it) }

    /** Resumes every paused chapter of [mangaId]. Returns how many were resumed. */
    @Synchronized
    fun resumeManga(
        mangaId: Long,
    ): Int = chapterIdsOf(mangaId) { it.status == DownloadStatus.PAUSED }.count { resumeChapter(it) }

    /** Re-queues the failed chapters of [mangaId]. Returns how many were retried. */
    @Synchronized
    fun retryManga(mangaId: Long): Int {
        val failed = chapterIdsOf(mangaId) { it.status == DownloadStatus.ERROR }
        if (failed.isNotEmpty()) retryFailed { it.mangaId == mangaId }
        return failed.size
    }

    /** Removes every chapter of [mangaId] from the queue (cleaning temporary files). */
    @Synchronized
    fun cancelManga(
        mangaId: Long,
    ): List<DesktopDownload> = chapterIdsOf(mangaId) { true }.mapNotNull { cancelChapter(it) }

    /** Moves all chapters of [mangaId] ahead of the rest of the queue, keeping their relative order. */
    @Synchronized
    fun moveMangaToTop(mangaId: Long): Boolean {
        val list = queueState.value
        val (group, rest) = list.partition { it.mangaId == mangaId }
        val reordered = group + rest
        if (group.isEmpty() || reordered.map { it.chapterId } == list.map { it.chapterId }) return false
        queueState.value = reordered
        persistence.persist()
        return true
    }

    private fun chapterIdsOf(mangaId: Long, filter: (DesktopDownload) -> Boolean): List<Long> =
        queueState.value.filter { it.mangaId == mangaId && filter(it) }.map { it.chapterId }
}
