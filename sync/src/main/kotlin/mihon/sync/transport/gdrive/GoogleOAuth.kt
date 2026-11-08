package mihon.sync.transport.gdrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class GoogleTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
)

/** Google OAuth 2.0 for installed apps (loopback + PKCE), restricted to the Drive appdata scope. */
object GoogleOAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    private val json = Json { ignoreUnknownKeys = true }

    fun authorizationUrl(clientId: String, redirectUri: String, codeChallenge: String, state: String): String =
        AUTH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .addQueryParameter("state", state)
            .build().toString()

    fun parseTokenResponse(body: String, now: Long, previousRefreshToken: String? = null): GoogleTokens {
        val obj: JsonObject = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw DriveException("invalid token response", it) }
        obj["error"]?.jsonPrimitive?.content?.let { err ->
            val desc = obj["error_description"]?.jsonPrimitive?.content.orEmpty()
            throw DriveAuthException("$err $desc".trim())
        }
        val access = obj["access_token"]?.jsonPrimitive?.content ?: throw DriveException("no access_token")
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        return GoogleTokens(
            accessToken = access,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: previousRefreshToken,
            expiresAtMillis = now + expiresIn * 1000,
        )
    }

    suspend fun exchangeCode(
        client: OkHttpClient,
        clientId: String,
        clientSecret: String?,
        code: String,
        verifier: String,
        redirectUri: String,
        now: () -> Long = System::currentTimeMillis,
    ): GoogleTokens = postToken(
        client,
        FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .apply { if (!clientSecret.isNullOrBlank()) add("client_secret", clientSecret) }
            .add("code_verifier", verifier)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build(),
        now,
        null,
    )

    suspend fun refresh(
        client: OkHttpClient,
        clientId: String,
        clientSecret: String?,
        refreshToken: String,
        now: () -> Long = System::currentTimeMillis,
    ): GoogleTokens = postToken(
        client,
        FormBody.Builder()
            .add("client_id", clientId)
            .apply { if (!clientSecret.isNullOrBlank()) add("client_secret", clientSecret) }
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build(),
        now,
        refreshToken,
    )

    suspend fun revoke(client: OkHttpClient, token: String) = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder().url(REVOKE_ENDPOINT).post(FormBody.Builder().add("token", token).build()).build(),
            ).execute().close()
        }
        Unit
    }

    private suspend fun postToken(
        client: OkHttpClient,
        form: FormBody,
        now: () -> Long,
        previousRefresh: String?,
    ): GoogleTokens = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(TOKEN_ENDPOINT).post(form).build()).execute().use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful &&
                !body.contains("\"error\"")
            ) {
                throw DriveException("token endpoint HTTP ${resp.code}")
            }
            parseTokenResponse(body, now(), previousRefresh)
        }
    }
}
