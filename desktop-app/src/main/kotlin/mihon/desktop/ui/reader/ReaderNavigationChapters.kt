package mihon.desktop.ui.reader

import mihon.desktop.library.model.LibraryChapter

/** Keep a directly requested chapter in the reader even when its scanlator is excluded. */
internal fun readerNavigationChapters(
    filtered: List<LibraryChapter>,
    all: List<LibraryChapter>,
    requestedChapterId: Long,
): List<LibraryChapter> =
    if (filtered.any { it.id == requestedChapterId }) {
        filtered
    } else {
        filtered + all.filter { it.id == requestedChapterId }
    }
