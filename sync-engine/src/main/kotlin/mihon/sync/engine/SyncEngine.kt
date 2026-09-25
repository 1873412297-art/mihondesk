package mihon.sync.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncTransport

class SyncEngine(
    private val repository: SyncLocalRepository,
    private val transport: SyncTransport,
    private val stateStore: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onReport: (suspend (SyncReport) -> Unit)? = null,
) {
    private val mutex = Mutex()

    suspend fun syncNow(): SyncReport = mutex.withLock {
        val startedAt = clock()
        try {
            val deviceId = stateStore.getDeviceId()

            // 1. PULL PHASE
            val peerWatermarks = stateStore.getAllPeerPullWatermarks()
            val remoteChangesets = transport.pull(sinceCursors = peerWatermarks, excludeDeviceId = deviceId)

            var totalMangaInserted = 0
            var totalMangaMerged = 0
            var totalChapterInserted = 0
            var totalChapterMerged = 0
            var totalCategoriesLinked = 0
            val overriddenItems = mutableListOf<SyncOverriddenItem>()

            val updatedPeerWatermarks = peerWatermarks.toMutableMap()
            for (cs in remoteChangesets) {
                val applyResult = repository.applyChangeset(cs)
                totalMangaInserted += applyResult.mangaInserted
                totalMangaMerged += applyResult.mangaMerged
                totalChapterInserted += applyResult.chapterInserted
                totalChapterMerged += applyResult.chapterMerged
                totalCategoriesLinked += applyResult.categoriesLinked
                overriddenItems.addAll(applyResult.overriddenItems)

                val prev = updatedPeerWatermarks[cs.deviceId] ?: 0L
                val updated = maxOf(prev, cs.cursor)
                updatedPeerWatermarks[cs.deviceId] = updated
                stateStore.setLastPullWatermark(cs.deviceId, updated)
            }

            // 2. PUSH PHASE
            val lastPushWatermark = stateStore.getLastPushWatermark()
            val delta = repository.exportDelta(sinceCursor = lastPushWatermark)
            val tombstones = repository.exportTombstones(sinceCursor = lastPushWatermark)
            var pushedChangeset: Changeset? = null

            if (!delta.isEmpty || tombstones.isNotEmpty()) {
                val newCursor = stateStore.nextCursor()
                val changeset = Changeset(
                    deviceId = deviceId,
                    baseSchema = 1,
                    cursor = newCursor,
                    producedAt = clock(),
                    upserts = delta,
                    tombstones = tombstones,
                )
                transport.push(changeset)

                val maxMangaModified = delta.mangas.maxOfOrNull { m ->
                    maxOf(m.lastModifiedAt, m.chapters.maxOfOrNull { it.lastModifiedAt } ?: 0L)
                } ?: 0L
                val maxTombstone = tombstones.maxOfOrNull { it.deletedAt } ?: 0L
                val newWatermark = maxOf(lastPushWatermark, maxMangaModified, maxTombstone)

                stateStore.setLastPushCursor(newCursor)
                stateStore.setLastPushWatermark(newWatermark)
                pushedChangeset = changeset
            }

            val report = SyncReport(
                success = true,
                pulledChangesetCount = remoteChangesets.size,
                mangaInserted = totalMangaInserted,
                mangaMerged = totalMangaMerged,
                chapterInserted = totalChapterInserted,
                chapterMerged = totalChapterMerged,
                categoriesLinked = totalCategoriesLinked,
                pushedChangeset = pushedChangeset,
                overriddenItems = overriddenItems,
                durationMs = clock() - startedAt,
            )
            onReport?.invoke(report)
            report
        } catch (e: Exception) {
            val failureReport = SyncReport(
                success = false,
                pulledChangesetCount = 0,
                mangaInserted = 0,
                mangaMerged = 0,
                chapterInserted = 0,
                chapterMerged = 0,
                categoriesLinked = 0,
                pushedChangeset = null,
                errorMessage = e.message ?: e.toString(),
                durationMs = clock() - startedAt,
            )
            onReport?.invoke(failureReport)
            failureReport
        }
    }
}
