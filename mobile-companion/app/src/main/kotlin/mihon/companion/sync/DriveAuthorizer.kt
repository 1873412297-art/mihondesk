package mihon.companion.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import mihon.sync.transport.gdrive.GoogleOAuth

/**
 * Google authorization through the phone's system accounts (Google Identity Services).
 * Only the `drive.appdata` scope is requested: the app sees nothing but its own hidden folder.
 */
class DriveAuthorizer(private val context: Context) {
    sealed interface Outcome {
        data class Granted(val accessToken: String, val email: String?) : Outcome
        data class NeedsConsent(val intent: PendingIntent) : Outcome
    }

    private val scopes = listOf(Scope(GoogleOAuth.SCOPE))

    suspend fun authorize(): Outcome {
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
        return toOutcome(Identity.getAuthorizationClient(context).authorize(request).await())
    }

    /** Result of the consent screen launched from [Outcome.NeedsConsent]. */
    fun fromIntent(data: Intent?): Outcome =
        toOutcome(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))

    /** Drops a cached token Google rejected, so the next [authorize] mints a fresh one. */
    suspend fun clearToken(token: String) {
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(token).build())
            .await()
    }

    suspend fun revoke(email: String) {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(email, "com.google"))
            .setScopes(scopes)
            .build()
        Identity.getAuthorizationClient(context).revokeAccess(request).await()
    }

    private fun toOutcome(result: AuthorizationResult): Outcome {
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) return Outcome.NeedsConsent(pending)
        val token = result.accessToken ?: error("Google returned no access token")
        return Outcome.Granted(token, result.toGoogleSignInAccount()?.email)
    }
}
