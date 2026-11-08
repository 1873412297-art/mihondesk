package mihon.desktop.extension

import mihon.extension.ipc.IpcSession

internal const val DEFAULT_SOURCE_REQUEST_TIMEOUT_MILLIS = 15_000L

/** Applies the normal source timeout without retaining a process manager's replaceable session. */
internal class ExtensionRequestTransport(
    private val defaultTimeoutMillis: Long = DEFAULT_SOURCE_REQUEST_TIMEOUT_MILLIS,
) {
    suspend fun request(
        command: String,
        payloadJson: String = "",
        session: suspend () -> IpcSession,
        timeoutMillis: Long = defaultTimeoutMillis,
    ): String = session().sendRequest(command, payloadJson, timeoutMillis)
}
