package mihon.desktop.extension

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mihon.desktop.logging.DesktopLogger

/**
 * Decodes the persisted installed-extension list. A corrupt value is logged and passed to
 * [onCorrupt] so the caller can preserve it before a later save overwrites the original entry.
 */
internal fun decodeInstalledExtensions(
    raw: String,
    json: Json,
    onCorrupt: (String) -> Unit,
): List<InstalledExtension> =
    try {
        json.decodeFromString<List<InstalledExtension>>(raw)
    } catch (error: Exception) {
        DesktopLogger.warn("ExtensionInstaller", "Installed extension list is corrupt", error)
        onCorrupt(raw)
        emptyList()
    }
