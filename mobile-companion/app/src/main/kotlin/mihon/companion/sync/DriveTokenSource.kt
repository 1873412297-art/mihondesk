package mihon.companion.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.companion.CompanionLog
import mihon.sync.transport.gdrive.DriveAuthException

/**
 * Supplies access tokens without UI. Google caches and refreshes them for us; we only keep the
 * current one for the duration of a sync and drop it when Drive answers 401.
 */
class DriveTokenSource(private val authorizer: DriveAuthorizer) {
    private val mutex = Mutex()
    private var cached: String? = null

    suspend fun accessToken(): String = mutex.withLock {
        cached ?: when (val outcome = authorizer.authorize()) {
            is DriveAuthorizer.Outcome.Granted -> outcome.accessToken.also { cached = it }
            is DriveAuthorizer.Outcome.NeedsConsent -> throw DriveAuthException(SIGN_IN_REQUIRED)
        }
    }

    suspend fun invalidate() = mutex.withLock {
        val stale = cached ?: return@withLock
        cached = null
        try {
            authorizer.clearToken(stale)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CompanionLog.warn(TAG, "Could not clear rejected Google token", error)
        }
    }

    /** Forget the token between sync runs; Google hands back the same one while it is valid. */
    suspend fun reset() = mutex.withLock { cached = null }

    companion object {
        const val SIGN_IN_REQUIRED = "Google sign-in required"
        private const val TAG = "DriveToken"
    }
}
