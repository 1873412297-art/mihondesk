package mihon.sync.transport.http

import java.io.IOException

sealed class SyncHttpException(message: String, cause: Throwable? = null) : IOException(message, cause)

class SyncPairingException(
    message: String = "Pairing expired or invalid token",
    cause: Throwable? = null,
) : SyncHttpException(message, cause)

class SyncNetworkException(
    message: String,
    cause: Throwable? = null,
) : SyncHttpException(message, cause)

class SyncProtocolException(
    message: String,
    cause: Throwable? = null,
) : SyncHttpException(message, cause)
