package mihon.sync.transport.gdrive

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Caches the access token and refreshes it with [refresh] shortly before expiry. */
class DriveTokenProvider(
    initial: GoogleTokens?,
    private val refresh: suspend (GoogleTokens) -> GoogleTokens,
    private val onUpdated: suspend (GoogleTokens) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var tokens = initial

    suspend fun accessToken(): String = mutex.withLock {
        val current = tokens ?: throw DriveAuthException("not signed in")
        if (current.expiresAtMillis - 60_000 > clock()) return current.accessToken
        val next = refresh(current)
        tokens = next
        onUpdated(next)
        next.accessToken
    }
}
