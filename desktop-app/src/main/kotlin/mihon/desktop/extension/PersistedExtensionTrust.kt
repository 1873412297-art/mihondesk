package mihon.desktop.extension

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore

private const val PREF_KEY_TRUST_CORRUPT = "extension.trusted_signatures.corrupt"

/** On-disk shape of the extension signer trust store. */
@Serializable
internal data class PersistedExtensionTrust(
    val trusted: Map<String, List<String>> = emptyMap(),
    val revoked: List<String> = emptyList(),
)

/**
 * Decodes the persisted trust document, including the legacy bare-map format. A corrupt document is
 * logged, copied to [PREF_KEY_TRUST_CORRUPT], and decoded as empty so revoked entries stay
 * recoverable instead of being silently overwritten by the next write.
 */
internal fun decodePersistedTrust(
    store: DesktopPreferenceStore,
    storageKey: String,
    json: Json,
): PersistedExtensionTrust {
    val raw = store.property(storageKey) ?: return PersistedExtensionTrust()
    // Accept a legacy bare map format: {"pkg":["fingerprint"]}. The primary format has
    // reserved "trusted"/"revoked" keys and is decoded below.
    try {
        val legacy = json.decodeFromString<Map<String, List<String>>>(raw)
        if (!legacy.containsKey("trusted") && !legacy.containsKey("revoked")) {
            return PersistedExtensionTrust(trusted = legacy)
        }
    } catch (_: Exception) {
        // Not the legacy format; fall through to the primary schema.
    }
    return try {
        json.decodeFromString<PersistedExtensionTrust>(raw)
    } catch (error: Exception) {
        DesktopLogger.warn("ExtensionTrust", "Trust store is corrupt; starting from an empty state", error)
        if (store.property(PREF_KEY_TRUST_CORRUPT) != raw) {
            store.update { setProperty(PREF_KEY_TRUST_CORRUPT, raw) }
        }
        PersistedExtensionTrust()
    }
}
