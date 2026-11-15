package mihon.extension.host

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.extension.ipc.WebViewRequest
import mihon.extension.ipc.WebViewResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object WebViewBridge {
    @Volatile private var handler: (suspend (WebViewRequest) -> WebViewResponse)? = null
    fun install(handler: suspend (WebViewRequest) -> WebViewResponse) {
        this.handler = handler
    }
    suspend fun execute(request: WebViewRequest): WebViewResponse =
        (handler ?: error("Desktop browser handler is unavailable"))(request).also {
            check(it.success) { it.error ?: "Browser operation failed" }
        }

    /**
     * For the synchronous Android APIs (cookies, user agent, destroy). Bounded so a stuck browser
     * helper fails the extension call instead of parking its thread forever.
     */
    fun executeBlocking(request: WebViewRequest, timeout: Duration = BLOCKING_CALL_TIMEOUT): WebViewResponse =
        runBlocking {
            try {
                withTimeout(timeout) { execute(request) }
            } catch (timedOut: TimeoutCancellationException) {
                throw IllegalStateException(
                    "Browser helper did not answer '${request.action}' within $timeout",
                    timedOut,
                )
            }
        }

    val BLOCKING_CALL_TIMEOUT: Duration = 60.seconds

    fun identity(): ExtensionExecutionContext.Identity = ExtensionExecutionContext.Identity(
        ExtensionExecutionContext.currentPackageId(),
        ExtensionExecutionContext.currentSourceId(),
        ExtensionExecutionContext.currentApplication(),
        ExtensionExecutionContext.currentPriority(),
    )
    fun request(action: String, sessionId: String = "", url: String = "", value: String = ""): WebViewRequest {
        val identity = identity()
        return WebViewRequest(
            action,
            requireNotNull(identity.packageId) { "WebView requires an extension identity" },
            requireNotNull(identity.sourceId) { "WebView requires a source identity" },
            sessionId,
            url,
            value = value,
        )
    }
}
