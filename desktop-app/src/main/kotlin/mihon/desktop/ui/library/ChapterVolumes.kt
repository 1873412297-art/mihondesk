package mihon.desktop.ui.library

import mihon.desktop.library.model.LibraryChapter

private val VOLUME_PATTERNS = listOf(
    // "Vol.2", "Vol 2", "Volume 3", "vol.12"; the word boundary keeps "Revolution 3" out.
    Regex("""(?i)(?<![a-z])vol(?:ume)?\.?\s*(\d{1,4})"""),
    // "第4卷", "第 5 巻"
    Regex("""第\s*(\d{1,4})\s*[卷巻册冊]"""),
)

/**
 * Sources rarely expose a structured volume, but many encode it in the chapter name (as Mihon's
 * own chapter recognition does). Returns null when no volume is present.
 */
fun parseChapterVolume(name: String): Int? =
    VOLUME_PATTERNS.firstNotNullOfOrNull { pattern -> pattern.find(name)?.groupValues?.get(1)?.toIntOrNull() }

/** One scanlator per line or comma separated, highest priority first. */
fun parsePreferredScanlators(raw: String?): List<String> =
    raw.orEmpty().split('\n', ',').map(String::trim).filter(String::isNotEmpty)

/**
 * When several groups release the same chapter number, keeps only the release(s) from the
 * highest-priority scanlator in [priority]. Chapters whose scanlators are all unranked, and
 * chapters without a recognised number, are left untouched. Order is preserved.
 */
fun applyScanlatorPriority(chapters: List<LibraryChapter>, priority: List<String>): List<LibraryChapter> {
    if (priority.isEmpty()) return chapters
    val ranks = priority.mapIndexed { index, name -> name.trim().lowercase() to index }.toMap()
    fun rank(chapter: LibraryChapter): Int =
        chapter.scanlator?.trim()?.lowercase()?.let(ranks::get) ?: Int.MAX_VALUE
    val bestByNumber = chapters
        .filter { it.chapterNumber >= 0 }
        .groupBy { it.chapterNumber }
        .mapValues { (_, group) -> group.minOf(::rank) }
    return chapters.filter { chapter ->
        if (chapter.chapterNumber < 0) return@filter true
        val best = bestByNumber.getValue(chapter.chapterNumber)
        best == Int.MAX_VALUE || rank(chapter) == best
    }
}

/** Global, ordered preferred-scanlator list (see [parsePreferredScanlators]). */
const val PREFERRED_SCANLATORS_KEY = "chapters.preferred-scanlators"
