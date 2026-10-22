package mihon.desktop.ui.palette

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import mihon.desktop.ui.settings.SettingsSection
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import kotlin.coroutines.cancellation.CancellationException

/** Commands the palette can run without leaving the keyboard. */
enum class PaletteCommand {
    OpenLibrary,
    OpenUpdates,
    OpenHistory,
    OpenBrowse,
    OpenDownloads,
    OpenStats,
    UpdateLibrary,
    ShowShortcuts,
    ToggleNavigationRail,
}

sealed interface PaletteTarget {
    data class Manga(val mangaId: Long) : PaletteTarget
    data class Command(val command: PaletteCommand) : PaletteTarget
    data class Settings(val section: SettingsSection) : PaletteTarget
    data class SourceManga(val source: SourceDescriptor, val manga: SManga) : PaletteTarget
}

data class PaletteItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val target: PaletteTarget,
    val keywords: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
)

/** One installed source's slice of a palette search. */
data class PaletteSourceGroup(
    val source: SourceDescriptor,
    val loading: Boolean = true,
    val mangas: List<SManga> = emptyList(),
    val error: String? = null,
)

private const val SCORE_EXACT = 1000
private const val SCORE_PREFIX = 800
private const val SCORE_WORD_PREFIX = 600
private const val SCORE_CONTAINS = 400
private const val SCORE_SUBSEQUENCE = 100

/**
 * How well [query] matches [text]; null when it does not match. Exact beats prefix beats a
 * word prefix beats a substring beats a loose subsequence ("opc" → "One Piece"). Shorter
 * texts win ties so "Downloads" ranks above "Download settings".
 */
fun matchScore(query: String, text: String): Int? {
    val q = query.trim().lowercase()
    val t = text.trim().lowercase()
    if (q.isEmpty() || t.isEmpty()) return null
    val lengthPenalty = (t.length - q.length).coerceIn(0, 99)
    val base = when {
        t == q -> SCORE_EXACT
        t.startsWith(q) -> SCORE_PREFIX
        t.split(' ', '-', '_', '.', ':', '：', '·', '/').any { it.startsWith(q) } -> SCORE_WORD_PREFIX
        t.contains(q) -> SCORE_CONTAINS
        isSubsequence(q, t) -> SCORE_SUBSEQUENCE
        else -> return null
    }
    return base - lengthPenalty
}

private fun isSubsequence(query: String, text: String): Boolean {
    var index = 0
    for (char in text) {
        if (index < query.length && char == query[index]) index++
    }
    return index == query.length
}

/** Items matching [query] best-first; a blank query keeps the given order. */
fun filterPaletteItems(query: String, items: List<PaletteItem>, limit: Int): List<PaletteItem> {
    if (query.isBlank()) return items.take(limit)
    return items.mapNotNull { item ->
        val best = (listOf(item.title) + item.keywords).mapNotNull { matchScore(query, it) }.maxOrNull()
        best?.let { item to it }
    }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}

/** Keyboard selection that wraps at both ends. */
fun moveSelection(current: Int, delta: Int, size: Int): Int {
    if (size <= 0) return 0
    return Math.floorMod(current + delta, size)
}

fun paletteSourceItems(groups: List<PaletteSourceGroup>): List<PaletteItem> = groups.flatMap { group ->
    group.mangas.map { manga ->
        PaletteItem(
            key = "source-${group.source.id}-${manga.title}",
            title = manga.title,
            subtitle = manga.author ?: group.source.name,
            target = PaletteTarget.SourceManga(group.source, manga),
            thumbnailUrl = manga.thumbnailUrl,
        )
    }
}

const val PALETTE_MIN_SOURCE_QUERY = 2

/**
 * Searches every installed source at once and reports merged, per-source snapshots in source
 * order. A failing source becomes an error row instead of aborting the others.
 */
class PaletteSourceSearch(
    private val sources: suspend () -> List<SourceDescriptor>,
    private val search: suspend (sourceId: Long, query: String) -> List<SManga>,
    private val perSourceLimit: Int = 5,
    maxConcurrent: Int = 6,
) {
    private val permits = Semaphore(maxConcurrent)

    suspend fun run(query: String, onUpdate: (List<PaletteSourceGroup>) -> Unit) {
        val trimmed = query.trim()
        if (trimmed.length < PALETTE_MIN_SOURCE_QUERY) {
            onUpdate(emptyList())
            return
        }
        val targets = sources()
        val lock = Mutex()
        var groups = targets.map { PaletteSourceGroup(source = it) }
        onUpdate(groups)
        coroutineScope {
            targets.map { source ->
                async {
                    val finished = try {
                        val found = permits.withPermit { search(source.id, trimmed) }
                        PaletteSourceGroup(source, loading = false, mangas = found.take(perSourceLimit))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        PaletteSourceGroup(
                            source,
                            loading = false,
                            error =
                            failure.message ?: failure.javaClass.simpleName,
                        )
                    }
                    lock.withLock {
                        groups = groups.map { if (it.source.id == source.id) finished else it }
                        onUpdate(groups)
                    }
                }
            }.awaitAll()
        }
    }
}
