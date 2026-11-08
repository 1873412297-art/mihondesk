package mihon.desktop.extension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.JvmName

/** Trust state for an extension package signer. */
enum class ExtensionTrustStatus {
    TRUSTED,
    UNTRUSTED,
    UNKNOWN,
    INVALID,
    ;

    val label: String
        get() = when (this) {
            TRUSTED -> "Trusted"
            UNTRUSTED -> "Untrusted"
            UNKNOWN -> "Unknown"
            INVALID -> "Invalid"
        }

    val isTrusted: Boolean get() = this == TRUSTED
}

/**
 * A pending explicit-trust request created when an install is blocked because the package signer is
 * unknown. The UI can display this request as a dialog and retry the install after trusting.
 */
@Serializable
data class ExtensionTrustRequest(
    val pkg: String,
    val fingerprints: List<String> = emptyList(),
    val filePath: String? = null,
    val reason: String = "",
    @kotlinx.serialization.Transient
    val storeItem: ExtensionStoreItem? = null,
)
