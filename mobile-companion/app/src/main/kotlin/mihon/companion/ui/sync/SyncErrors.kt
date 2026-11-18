package mihon.companion.ui.sync

import mihon.companion.R
import mihon.companion.sync.DriveTokenSource

/**
 * The sync engine only keeps the failure's message text; turn the common network ones into
 * advice instead of a bare "timeout".
 */
internal object SyncErrors {
    fun describe(raw: String): UiMessage {
        val text = raw.lowercase()
        return when {
            "timeout" in text || "timed out" in text -> UiMessage(R.string.sync_error_timeout)
            "unable to resolve host" in text || "failed to connect" in text ||
                "connection reset" in text || "unexpected end of stream" in text ->
                UiMessage(R.string.sync_error_network, listOf(raw))
            DriveTokenSource.SIGN_IN_REQUIRED.lowercase() in text || "(401)" in text ->
                UiMessage(R.string.sync_error_sign_in)
            else -> UiMessage(R.string.sync_error, listOf(raw))
        }
    }
}
