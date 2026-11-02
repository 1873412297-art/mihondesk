package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveRestSnapshotStore
import mihon.sync.transport.gdrive.GoogleDriveTransport
import okhttp3.OkHttpClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Drive sign-in for Android via Identity AuthorizationClient (drive.appdata scope only).
 * The access token is re-requested on every sync; Play services caches and refreshes it, so no
 * refresh token or client secret ever lives on the device.
 */
object GoogleDriveAuth {
    const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    sealed interface Outcome {
        data class Authorized(val accessToken: String) : Outcome

        /** User consent required: launch with an ActivityResultLauncher for IntentSenderRequest. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome
    }

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
        .build()

    suspend fun authorize(context: Context): Outcome = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { cont.resume(toOutcome(it)) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Parses the consent activity result returned from [Outcome.NeedsConsent]. */
    fun fromConsentResult(activity: Activity, data: Intent?): Outcome =
        toOutcome(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))

    private fun toOutcome(result: AuthorizationResult): Outcome {
        val token = result.accessToken
        return when {
            result.hasResolution() -> Outcome.NeedsConsent(result.pendingIntent!!)
            token != null -> Outcome.Authorized(token)
            else -> throw DriveAuthException("Google returned no access token")
        }
    }

    /** Background-safe token: never shows UI; throws [DriveAuthException] if consent is needed. */
    suspend fun silentAccessToken(context: Context): String = when (val o = authorize(context)) {
        is Outcome.Authorized -> o.accessToken
        is Outcome.NeedsConsent -> throw DriveAuthException("Google Drive sign-in required")
    }

    fun transport(context: Context, client: OkHttpClient) =
        GoogleDriveTransport(
            DriveRestSnapshotStore(client, accessToken = { silentAccessToken(context.applicationContext) }),
        )
}
