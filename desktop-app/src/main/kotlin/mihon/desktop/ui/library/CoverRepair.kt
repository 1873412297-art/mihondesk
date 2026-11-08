package mihon.desktop.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.get
import mihon.desktop.image.CoverFailureRegistry
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryRepository
import java.util.Collections

/** Refreshes covers that failed to load, one manga at a time, and reports whether it is running. */
internal class CoverRepair(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val sourceManager: DesktopSourceManager?,
    private val detailManga: () -> MangaDetails?,
    private val refreshDetails: () -> Unit,
    private val refreshDelegate: () -> (suspend (Long) -> Unit)?,
    private val coverFailures: CoverFailureRegistry = CoverFailureRegistry(),
) : AutoCloseable {
    private val autoRefreshedMangaIds: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf<Long>())
    private var job: Job? = null
    private val _isRepairingCovers = MutableStateFlow(false)
    val isRepairingCovers: StateFlow<Boolean> = _isRepairingCovers.asStateFlow()

    fun onCoverLoadFailed(code: Int, targetMangaId: Long? = null) {
        if (code != 404 && code != 410) return
        val detailManga = detailManga()
        val mangaId = when {
            targetMangaId != null -> targetMangaId
            detailManga != null -> detailManga.id
            else -> return
        }
        scope.launch(Dispatchers.IO) {
            val sourceId = when {
                detailManga != null && detailManga.id == mangaId -> detailManga.sourceId
                else -> repository.allMangaSnapshot().find { it.id == mangaId }?.sourceId ?: return@launch
            }
            if (sourceId == 0L || sourceManager?.get(sourceId) == null) return@launch
            if (!autoRefreshedMangaIds.add(mangaId)) return@launch

            val delegate = refreshDelegate()
            if (delegate != null) {
                try {
                    delegate(mangaId)
                } finally {
                    if (detailManga?.id == mangaId) refreshDetails()
                }
            } else if (detailManga?.id == mangaId) {
                refreshDetails()
            }
        }
    }

    fun repairBrokenCovers() {
        if (_isRepairingCovers.value) return
        val snapshot = coverFailures.snapshot()
        if (snapshot.isEmpty()) return
        job = scope.launch {
            _isRepairingCovers.value = true
            try {
                for ((mangaId, _) in snapshot) {
                    try {
                        val delegate = refreshDelegate()
                        if (delegate != null) {
                            delegate(mangaId)
                        }
                        refreshDetails()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Exception) {
                        mihon.desktop.logging.DesktopLogger.warn(
                            "Library",
                            "Cover repair failed for manga $mangaId",
                            error,
                        )
                    } finally {
                        coverFailures.clear(mangaId)
                    }
                    delay(300)
                }
            } finally {
                _isRepairingCovers.value = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _isRepairingCovers.value = false
    }

    override fun close() {
        job?.cancel()
    }
}
