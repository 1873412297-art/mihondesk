package mihon.desktop.preferences

import mihon.desktop.logging.DesktopLogger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

/**
 * Quarantines an unreadable preferences file, logs the failure, and returns empty defaults. The
 * broken file is kept next to the original so a later save cannot silently erase recoverable state.
 */
internal fun recoverCorruptPreferences(
    file: Path,
    error: Exception,
    invalidateCache: () -> Unit,
): Properties {
    DesktopLogger.warn("Preferences", "Unreadable preferences at $file; recovering defaults", error)
    invalidateCache()
    val corruptFile = file.resolveSibling("${file.fileName}.corrupt-${UUID.randomUUID()}")
    try {
        Files.move(file, corruptFile)
    } catch (moveError: IOException) {
        DesktopLogger.warn("Preferences", "Could not quarantine $file; leaving it in place", moveError)
    } catch (moveError: SecurityException) {
        DesktopLogger.warn("Preferences", "Could not quarantine $file; leaving it in place", moveError)
    }
    return Properties()
}
