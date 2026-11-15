package mihon.companion.ui.sync

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import mihon.companion.R

/** Turns a Google sign-in failure into something a user can act on. */
internal object SignInErrors {
    fun describe(error: Throwable): UiMessage {
        val api = error as? ApiException ?: return UiMessage(
            R.string.sign_in_failed,
            listOf(error.message ?: error.javaClass.simpleName),
        )
        return when (api.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> UiMessage(R.string.sign_in_error_developer)
            CommonStatusCodes.CANCELED -> UiMessage(R.string.sign_in_error_cancelled)
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT ->
                UiMessage(R.string.sign_in_error_network, listOf(api.statusCode))
            else -> UiMessage(R.string.sign_in_error_code, listOf(api.statusCode, api.message.orEmpty()))
        }
    }

    /** The consent screen closed without any result. */
    val cancelled = UiMessage(R.string.sign_in_error_cancelled)
}
