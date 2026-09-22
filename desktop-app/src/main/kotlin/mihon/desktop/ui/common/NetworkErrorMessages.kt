package mihon.desktop.ui.common

import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.SimplifiedChineseStrings

private val CLOUDFLARE_ERROR_CODE_REGEX =
    Regex("""(?i)(?:error\s+code:?|cf-error-code["'>\s:]*|\bcloudflare[^\d]{1,30})\s*(10\d\d)\b""")

/**
 * Maps raw network error messages containing Cloudflare error hints (e.g. error code: 1005)
 * to user-friendly localized messages. If no Cloudflare signature is detected, the original
 * message is returned unchanged.
 */
fun formatNetworkErrorMessage(
    message: String?,
    strings: DesktopStrings = SimplifiedChineseStrings,
): String {
    if (message.isNullOrBlank()) return message.orEmpty()

    val match = CLOUDFLARE_ERROR_CODE_REGEX.find(message)
    if (match != null) {
        val code = match.groupValues[1]
        return when (code) {
            "1005" -> strings.networkErrorCloudflare1005
            "1015" -> strings.networkErrorCloudflare1015
            "1016" -> strings.networkErrorCloudflare1016
            "1020" -> strings.networkErrorCloudflare1020
            else -> strings.networkErrorCloudflareOther(code)
        }
    }

    if (message.contains("sorry, you have been blocked", ignoreCase = true)) {
        return strings.networkErrorCloudflare1020
    }

    return message
}
