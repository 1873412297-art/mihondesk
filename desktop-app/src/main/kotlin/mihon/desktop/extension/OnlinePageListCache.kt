package mihon.desktop.extension

import mihon.extension.source.model.Page

/**
 * Page lists kept between a warmup source and the chapter that actually opens. Scoped to a reader
 * factory (one runtime) so a profile switch cannot reuse another profile's lists, with insertion
 * order eviction so the entry just stored is never the one dropped.
 */
class OnlinePageListCache(
    private val maxEntries: Int = MAX_PAGE_LISTS,
) {
    private val entries = LinkedHashMap<String, List<Page>>()
    private val lock = Any()

    fun get(key: String): List<Page>? = synchronized(lock) { entries[key] }

    fun remember(key: String, pages: List<Page>) {
        synchronized(lock) {
            if (entries.size >= maxEntries && !entries.containsKey(key)) {
                entries.keys.firstOrNull()?.let(entries::remove)
            }
            entries[key] = pages
        }
    }

    fun discardManga(mangaId: Long) {
        val prefix = "$mangaId\n"
        synchronized(lock) { entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove) }
    }

    fun discardChapter(mangaId: Long, sourceId: Long, chapterUrl: String) {
        synchronized(lock) { entries.remove(pageListKey(mangaId, sourceId, chapterUrl)) }
    }

    companion object {
        private const val MAX_PAGE_LISTS = 24

        internal fun pageListKey(mangaId: Long, sourceId: Long, chapterUrl: String): String =
            "$mangaId\n$sourceId\n$chapterUrl"
    }
}
