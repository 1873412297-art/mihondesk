package mihon.desktop.webview

import com.sun.net.httpserver.HttpExchange
import mihon.desktop.logging.DesktopLogger

/** Reject invalid browser broker requests while retaining diagnostics for either failure. */
internal fun rejectBrokerRequest(exchange: HttpExchange, failure: Exception) {
    DesktopLogger.warn("DesktopWebViewManager", "Rejected browser broker request", failure)
    runCatching { exchange.sendResponseHeaders(403, -1) }.onFailure {
        DesktopLogger.debug("DesktopWebViewManager", "Unable to send rejected broker response: ${it.message}")
    }
}
