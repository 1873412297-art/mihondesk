package mihon.desktop.ui.browse.migration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.library.model.LibraryManga
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

class BatchMigrationRunner(
    private val scope: CoroutineScope,
    private val onSnackbar: (suspend (String) -> Unit)? = null,
    private val stringsProvider: () -> DesktopStrings,
    private val onStateChanged: ((BatchMigrationState) -> Unit)? = null,
) {
    private val _state = MutableStateFlow<BatchMigrationState>(BatchMigrationState.Idle)
    val state: StateFlow<BatchMigrationState> = _state.asStateFlow()

    private var currentJob: Job? = null

    private fun updateState(newState: BatchMigrationState) {
        _state.value = newState
        onStateChanged?.invoke(newState)
    }

    fun start(
        mangas: List<LibraryManga>,
        targetSource: SourceDescriptor,
        searchFn: suspend (query: String) -> List<SManga>,
        migrateFn: suspend (oldManga: LibraryManga, targetManga: SManga) -> Unit,
        delayMs: Long = 500L,
        onComplete: (() -> Unit)? = null,
    ): Job {
        // Re-entry guard: starting while a batch is still running would interleave state updates
        // from two coroutines; return the active job instead.
        currentJob?.let { active -> if (active.isActive) return active }
        val job = scope.launch {
            try {
                runBatchMigration(
                    mangas = mangas,
                    targetSource = targetSource,
                    searchFn = searchFn,
                    migrateFn = migrateFn,
                    delayMs = delayMs,
                )
            } finally {
                onComplete?.invoke()
            }
        }
        currentJob = job
        return job
    }

    suspend fun runBatchMigration(
        mangas: List<LibraryManga>,
        targetSource: SourceDescriptor,
        searchFn: suspend (query: String) -> List<SManga>,
        migrateFn: suspend (oldManga: LibraryManga, targetManga: SManga) -> Unit,
        delayMs: Long = 500L,
    ) {
        if (mangas.isEmpty()) {
            updateState(
                BatchMigrationState.Completed(
                    targetSource = targetSource,
                    totalCount = 0,
                    successCount = 0,
                    failures = emptyList(),
                    wasCancelled = false,
                ),
            )
            return
        }

        val total = mangas.size
        var successCount = 0
        val failures = mutableListOf<BatchMigrationFailure>()

        try {
            for ((index, manga) in mangas.withIndex()) {
                currentCoroutineContext().ensureActive()

                updateState(
                    BatchMigrationState.Running(
                        targetSource = targetSource,
                        currentIndex = index,
                        totalCount = total,
                        currentManga = manga,
                        successCount = successCount,
                        failureCount = failures.size,
                    ),
                )

                if (delayMs > 0 && index > 0) {
                    delay(delayMs)
                }
                currentCoroutineContext().ensureActive()

                try {
                    var candidates = searchFn(manga.title)
                    if (candidates.isEmpty()) {
                        val cleaned = MigrationMatcher.cleanTitle(manga.title)
                        if (cleaned != manga.title && cleaned.isNotBlank()) {
                            candidates = searchFn(cleaned)
                        }
                    }

                    val eval = MigrationMatcher.evaluateCandidates(manga.title, candidates)
                    if (eval.isUniqueHighConfidence && eval.bestMatch != null) {
                        migrateFn(manga, eval.bestMatch.manga)
                        successCount++
                    } else if (eval.candidates.isEmpty() ||
                        (eval.bestMatch != null && eval.bestMatch.score < MigrationMatcher.ELIGIBLE_THRESHOLD)
                    ) {
                        failures += BatchMigrationFailure(
                            manga = manga,
                            reason = BatchMigrationFailureReason.NoMatchFound,
                        )
                    } else {
                        failures += BatchMigrationFailure(
                            manga = manga,
                            reason = BatchMigrationFailureReason.AmbiguousMatches,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += BatchMigrationFailure(
                        manga = manga,
                        reason = BatchMigrationFailureReason.MigrationError,
                        detailMessage = e.message,
                    )
                }
            }

            updateState(
                BatchMigrationState.Completed(
                    targetSource = targetSource,
                    totalCount = total,
                    successCount = successCount,
                    failures = failures,
                    wasCancelled = false,
                ),
            )

            val strings = stringsProvider()
            if (failures.isEmpty()) {
                onSnackbar?.invoke(strings.migrateBatchAllSuccess(total, targetSource.name))
            } else {
                onSnackbar?.invoke(strings.migrateBatchReportSummary(successCount, failures.size))
            }
        } catch (e: CancellationException) {
            updateState(
                BatchMigrationState.Completed(
                    targetSource = targetSource,
                    totalCount = total,
                    successCount = successCount,
                    failures = failures,
                    wasCancelled = true,
                ),
            )
            val strings = stringsProvider()
            onSnackbar?.invoke(strings.migrateBatchCancelled(successCount, total))
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }

    fun reset() {
        cancel()
        updateState(BatchMigrationState.Idle)
    }
}
