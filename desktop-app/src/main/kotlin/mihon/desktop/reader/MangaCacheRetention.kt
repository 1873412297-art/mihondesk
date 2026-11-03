package mihon.desktop.reader

import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopRoute

/** Whether the temporary online-chapter cache should stay for the manga now on screen. */
internal sealed interface MangaCacheRetention {
    /** Reader is open but the manga id is not known yet. Do not wipe. */
    data object Keep : MangaCacheRetention

    /** No manga screen is showing. */
    data object Clear : MangaCacheRetention

    data class Switch(val mangaId: Long) : MangaCacheRetention
}

/**
 * Chapter changes inside the reader, and backing out to the same manga's details, keep the cache.
 * Leaving that manga (library grid, another section, another book) clears it.
 */
internal fun mangaCacheRetention(
    destination: DesktopRoute,
    libraryMangaId: Long?,
    readerMangaId: Long?,
): MangaCacheRetention {
    val mangaId = when (destination) {
        is DesktopDestination.Reader -> readerMangaId
        is DesktopDestination.MangaDetails -> destination.mangaId
        DesktopDestination.Library -> libraryMangaId
        else -> null
    }
    return when {
        destination is DesktopDestination.Reader && mangaId == null -> MangaCacheRetention.Keep
        mangaId != null -> MangaCacheRetention.Switch(mangaId)
        else -> MangaCacheRetention.Clear
    }
}
