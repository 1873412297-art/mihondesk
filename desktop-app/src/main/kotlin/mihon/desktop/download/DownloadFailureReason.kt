package mihon.desktop.download

import kotlinx.serialization.Serializable
import mihon.desktop.extension.SourceHttpException
import mihon.extension.ipc.NetworkFailureKind
import mihon.extension.ipc.findNetworkFailure
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
import javax.net.ssl.SSLException

/** Stable queue metadata; explanations belong to the UI, original diagnostics remain untouched. */
@Serializable
enum class DownloadFailureReason {
    TLS,
    TIMEOUT,
    OFFLINE,
    PROXY,
    CONNECTION,
    RATE_LIMITED,
    AUTHENTICATION,
    WEB_VERIFICATION,
    SITE_BLOCKED,
    DOMAIN_DENIED,
    SOURCE_UNAVAILABLE,
    EXTENSION_MISSING,
    EMPTY_CHAPTER,
    INVALID_IMAGE,
    STORAGE_FULL,
    STORAGE_ACCESS,
    MISSING_FILES,
    REGISTRATION,
    HTTP,
    UNKNOWN,
}

fun NetworkFailureKind.downloadFailureReason(): DownloadFailureReason = when (this) {
    NetworkFailureKind.TLS -> DownloadFailureReason.TLS
    NetworkFailureKind.TIMEOUT -> DownloadFailureReason.TIMEOUT
    NetworkFailureKind.OFFLINE -> DownloadFailureReason.OFFLINE
    NetworkFailureKind.PROXY -> DownloadFailureReason.PROXY
    NetworkFailureKind.CONNECTION -> DownloadFailureReason.CONNECTION
    NetworkFailureKind.RATE_LIMITED -> DownloadFailureReason.RATE_LIMITED
    NetworkFailureKind.AUTHENTICATION_REQUIRED -> DownloadFailureReason.AUTHENTICATION
    NetworkFailureKind.WEB_VERIFICATION -> DownloadFailureReason.WEB_VERIFICATION
    NetworkFailureKind.SITE_BLOCKED -> DownloadFailureReason.SITE_BLOCKED
    NetworkFailureKind.DOMAIN_DENIED -> DownloadFailureReason.DOMAIN_DENIED
    NetworkFailureKind.HTTP_ERROR -> DownloadFailureReason.HTTP
    NetworkFailureKind.INVALID_REQUEST -> DownloadFailureReason.UNKNOWN
}

fun classifyDownloadFailure(error: Throwable): DownloadFailureReason {
    error.findNetworkFailure()?.let { return it.kind.downloadFailureReason() }
    val chain = generateSequence(error) { it.cause }.take(16).toList()
    chain.filterIsInstance<SourceHttpException>().firstOrNull()?.let {
        return it.kind?.downloadFailureReason() ?: when (it.code) {
            429 -> DownloadFailureReason.RATE_LIMITED
            401, 403 -> DownloadFailureReason.AUTHENTICATION
            else -> DownloadFailureReason.HTTP
        }
    }
    return when {
        chain.any { it is SSLException } -> DownloadFailureReason.TLS
        chain.any { it is SocketTimeoutException } -> DownloadFailureReason.TIMEOUT
        chain.any { it is UnknownHostException } -> DownloadFailureReason.OFFLINE
        chain.any { it is AccessDeniedException } -> DownloadFailureReason.STORAGE_ACCESS
        chain.any {
            val msg = it.message.orEmpty().lowercase(java.util.Locale.ROOT)
            "extension package file missing" in msg || "扩展包文件缺失" in msg ||
                "please reinstall the extension" in msg || "请重新安装该扩展" in msg
        } -> DownloadFailureReason.EXTENSION_MISSING
        else -> classifyDownloadFailure(chain.joinToString("\n") { it.message.orEmpty() })
    }
}

/** Older queues only have a diagnostic string. Match known errors conservatively. */
fun classifyDownloadFailure(message: String?): DownloadFailureReason {
    val text = message.orEmpty().lowercase(java.util.Locale.ROOT)
    return when {
        "access denied: domain" in text || "domain_denied" in text -> DownloadFailureReason.DOMAIN_DENIED
        "extension package file missing" in text || "扩展包文件缺失" in text ||
            "please reinstall the extension" in text || "请重新安装该扩展" in text ->
            DownloadFailureReason.EXTENSION_MISSING
        "no isolated host registered" in text || ("source with id" in text && "not found" in text) ||
            "source unavailable" in text -> DownloadFailureReason.SOURCE_UNAVAILABLE
        "tls:" in text || "sslhandshakeexception" in text || "terminated the handshake" in text ->
            DownloadFailureReason.TLS
        "timed out" in text || "timeout" in text -> DownloadFailureReason.TIMEOUT
        "unknownhostexception" in text || "unable to resolve host" in text -> DownloadFailureReason.OFFLINE
        "proxy:" in text -> DownloadFailureReason.PROXY
        "connection refused" in text || "connection reset" in text || "connection interrupted" in text ->
            DownloadFailureReason.CONNECTION
        "http 429" in text || "rate_limited" in text -> DownloadFailureReason.RATE_LIMITED
        "web_verification" in text -> DownloadFailureReason.WEB_VERIFICATION
        "site_blocked" in text -> DownloadFailureReason.SITE_BLOCKED
        "http 401" in text || "http 403" in text || "authentication_required" in text ->
            DownloadFailureReason.AUTHENTICATION
        "page list is empty" in text -> DownloadFailureReason.EMPTY_CHAPTER
        "unsupported image" in text || "invalid image" in text || "not an image" in text ->
            DownloadFailureReason.INVALID_IMAGE
        "insufficient disk space" in text || "no space left" in text -> DownloadFailureReason.STORAGE_FULL
        "accessdeniedexception" in text -> DownloadFailureReason.STORAGE_ACCESS
        "files are missing or corrupt" in text -> DownloadFailureReason.MISSING_FILES
        "could not be registered for offline" in text -> DownloadFailureReason.REGISTRATION
        Regex("\\bhttp [45][0-9]{2}\\b").containsMatchIn(text) -> DownloadFailureReason.HTTP
        else -> DownloadFailureReason.UNKNOWN
    }
}
