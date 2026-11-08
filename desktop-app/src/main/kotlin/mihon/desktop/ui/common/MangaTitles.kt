package mihon.desktop.ui.common

import java.util.Locale

/** Title key used by duplicate detection and migration matching: letters (including CJK) and digits only. */
internal fun normalizeMangaTitle(title: String): String = title
    .lowercase(Locale.ROOT)
    .filter { it.isLetterOrDigit() }
