package mihon.desktop.download

import mihon.reader.source.WindowsFileNames

/**
 * Names for the title-based download layout that predates `manga-<id>/chapter-<id>`.
 *
 * New directories always use [WindowsFileNames.sanitize]. Lookups also try [legacy], the rule
 * releases up to 0.3.26 used, so a chapter downloaded back then is still found when the two rules
 * disagree (names of 200-255 characters, `COM¹`, `CONIN$`).
 */
internal object DownloadFileNames {
    fun current(name: String): String = WindowsFileNames.sanitize(name)

    /** Every name a chapter or manga directory may have on disk, preferred name first. */
    fun candidates(name: String): List<String> = listOf(current(name), legacy(name)).distinct()

    private val legacyReserved = Regex("[\\x00-\\x1f\\\\/:*?\"<>|]")
    private val legacyDeviceName = Regex("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")

    private fun legacy(name: String): String {
        val sanitized = name.replace(legacyReserved, "_").trim().trimEnd('.', ' ').ifEmpty { "unnamed" }
        val deviceName = sanitized.substringBefore('.').trimEnd()
        return if (deviceName.matches(legacyDeviceName)) "_$sanitized" else sanitized
    }
}
