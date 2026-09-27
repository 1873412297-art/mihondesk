package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import mihon.desktop.download.downloadFailureReason
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.hint
import mihon.desktop.i18n.text
import mihon.desktop.i18n.title
import mihon.extension.ipc.NetworkFailure
import mihon.extension.ipc.NetworkFailureKind

@Composable
internal fun sourceNetworkFailureText(failure: NetworkFailure): String {
    val strings = LocalStrings.current
    val detail = when (failure.kind) {
        NetworkFailureKind.SITE_BLOCKED -> strings.text(UiText.NetworkFailureSiteBlocked)
        NetworkFailureKind.WEB_VERIFICATION -> strings.text(UiText.NetworkFailureWebVerification)
        NetworkFailureKind.AUTHENTICATION_REQUIRED -> strings.text(UiText.NetworkFailureAuthRequired)
        NetworkFailureKind.RATE_LIMITED -> strings.text(UiText.NetworkFailureRateLimited)
        else -> failure.kind.downloadFailureReason().let {
            "${strings.text(it.title)}\n${strings.text(it.hint)}"
        }
    }
    val context = listOfNotNull(
        failure.host.takeIf { it.isNotBlank() },
        failure.statusCode.takeIf { it > 0 }?.let { "HTTP $it" },
    ).joinToString(" · ")
    return listOf(detail, context).filter { it.isNotBlank() }.joinToString("\n")
}
