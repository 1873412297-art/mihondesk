package mihon.sync.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncCursorConflictException
import mihon.sync.transport.api.SyncTransport

class SyncEngine(
    private val repository: SyncLocalRepository,
    private val transport: SyncTransport,
    private val stateStore: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onReport: (suspend (SyncReport) -> Unit)? = null,
) {
    companion object {
        // Initial push + gap-fill retry + fresh-cursor retry. Beyond this the local cursor
        // history has genuinely diverged from the server's and the failure must surface.
        const val MAX_PUSH_ATTEMPTS = 3
    }

    private val mutex = Mutex()

    /**
     * Pushes [changeset], self-healing cursor conflicts instead of failing permanently.
     *
     * The local clock is persisted before the push, so a crash in between leaves the next push
     * one cursor ahead of the server (gap 409). Recovery first retries with the cursor that
     * fills the server's expected gap — our last acked cursor + 1 — reusing the burned cursor
     * with the identical delta (the delta is still exported from the unchanged push watermark,
     * so no locally persisted change is lost). The server also answers an identical-payload
     * replay of an acked-but-unpersisted cursor with 204, which heals the crash-after-ack
     * window. If the gap cursor is taken with different content, a fresh cursor past the local
     * clock is tried as a last resort.
     */
    private suspend fun pushWithConflictRecovery(changeset: Changeset): Changeset {
        var attempt = 0
        var current = changeset
        while (true) {
            try {
                transport.push(current)
                return current
            } catch (conflict: SyncCursorConflictException) {
                attempt++
                if (attempt >= MAX_PUSH_ATTEMPTS) throw conflict
                current = when (attempt) {
                    1 -> changeset.copy(cursor = stateStore.getLastPushCursor() + 1)
                    else -> changeset.copy(cursor = stateStore.nextCursor())
                }
            }
        }
    }

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
                val pushed = pushWithConflictRecovery(changeset)

                val maxMangaModified = delta.mangas.maxOfOrNull { m ->
                    maxOf(m.lastModifiedAt, m.chapters.maxOfOrNull { it.lastModifiedAt } ?: 0L)
                } ?: 0L
                val maxTombstone = tombstones.maxOfOrNull { it.deletedAt } ?: 0L
                val newWatermark = maxOf(lastPushWatermark, maxMangaModified, maxTombstone)

                stateStore.setLastPushCursor(pushed.cursor)
                stateStore.setLastPushWatermark(newWatermark)
                pushedChangeset = pushed
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
