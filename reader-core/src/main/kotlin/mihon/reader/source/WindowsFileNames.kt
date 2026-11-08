package mihon.reader.source

/**
 * Turns source-supplied text (manga titles, chapter names, archive entry names) into a single
 * path component that Windows will create, open and delete.
 *
 * The rules come from the Win32 "Naming Files, Paths, and Namespaces" page: the reserved characters
 * `<>:"/\|?*`, control characters, trailing dots and spaces (silently stripped by Win32, so `a.` and
 * `a` collide), and the legacy device names that resolve to a device in any directory and with any
 * extension (`CON`, `nul.txt`, `COM¹`).
 */
object WindowsFileNames {
    /**
     * Leaves room under the 255 UTF-16 unit component limit for the suffixes the downloader adds
     * while staging (`.previous`, `.cbz_tmp`, `_tmp`, `.part`).
     */
    const val MAX_LENGTH = 200

    private const val FALLBACK = "unnamed"
    private const val REPLACEMENT = '_'
    private const val RESERVED_CHARACTERS = "<>:\"/\\|?*"

    // COM and LPT also reserve the superscript digits ¹ ² ³ (U+00B9, U+00B2, U+00B3).
    private val reservedDeviceName = Regex(
        "(?i)CON|PRN|AUX|NUL|CONIN\\$|CONOUT\\$|COM[1-9\\u00B9\\u00B2\\u00B3]|LPT[1-9\\u00B9\\u00B2\\u00B3]",
    )

    fun sanitize(name: String): String {
        val replaced = buildString(name.length) {
            name.forEach { character ->
                append(if (character.code < 0x20 || character in RESERVED_CHARACTERS) REPLACEMENT else character)
            }
        }
        val trimmed = truncate(replaced.trim()).trimEnd('.', ' ').ifEmpty { FALLBACK }
        return if (isReservedDeviceName(trimmed)) "$REPLACEMENT$trimmed" else trimmed
    }

    /** Win32 matches the device name against the part before the first dot, ignoring trailing spaces. */
    fun isReservedDeviceName(name: String): Boolean =
        reservedDeviceName.matches(name.substringBefore('.').trimEnd(' '))

    /** Cuts at [MAX_LENGTH] without splitting a surrogate pair, which would leave an invalid name. */
    private fun truncate(value: String): String {
        if (value.length <= MAX_LENGTH) return value
        val end = if (Character.isHighSurrogate(value[MAX_LENGTH - 1])) MAX_LENGTH - 1 else MAX_LENGTH
        return value.substring(0, end)
    }
}
