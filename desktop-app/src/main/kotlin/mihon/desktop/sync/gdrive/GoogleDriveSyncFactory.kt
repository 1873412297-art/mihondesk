package mihon.desktop.sync.gdrive

import kotlinx.coroutines.CoroutineScope
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.transport.gdrive.DriveRestSnapshotStore
import mihon.sync.transport.gdrive.GoogleDriveTransport
import mihon.sync.transport.gdrive.GoogleOAuth
import mihon.sync.transport.gdrive.Pkce
import okhttp3.OkHttpClient
import java.awt.Desktop
import java.net.ProxySelector
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Wires the production Drive sync service for one profile. */
object GoogleDriveSyncFactory {
    fun create(
        profileRoot: Path,
        scope: CoroutineScope,
        deviceId: suspend () -> String,
        localRepository: () -> SyncLocalRepository,
        proxySelector: ProxySelector? = ProxySelector.getDefault(),
        config: GoogleDriveConfig = GoogleDriveConfig.resolve(),
        openBrowser: (String) -> Unit = ::openInBrowser,
    ): GoogleDriveSyncService {
        val dir = profileRoot.resolve("gdrive")
        val http = OkHttpClient.Builder()
            .apply { if (proxySelector != null) proxySelector(proxySelector) }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return GoogleDriveSyncService(
            config = config,
            vault = DriveTokenVault(dir.resolve("tokens.bin")),
            stateStore = FileDriveSyncStateStore(dir.resolve("sync-state.json"), deviceId),
            localRepository = localRepository,
            scope = scope,
            authorizer = {
                val verifier = Pkce.newVerifier()
                val state = Pkce.newState()
                LoopbackOAuthReceiver(state).use { receiver ->
                    openBrowser(
                        GoogleOAuth.authorizationUrl(
                            config.clientId,
                            receiver.redirectUri,
                            Pkce.challenge(verifier),
                            state,
                        ),
                    )
                    val code = receiver.awaitCode()
                    GoogleOAuth.exchangeCode(
                        http,
                        config.clientId,
                        config.clientSecret,
                        code,
                        verifier,
                        receiver.redirectUri,
                    )
                }
            },
            refresher = { old ->
                GoogleOAuth.refresh(
                    http,
                    config.clientId,
                    config.clientSecret,
                    old.refreshToken ?: throw mihon.sync.transport.gdrive.DriveAuthException("no refresh token"),
                )
            },
            revoker = { GoogleOAuth.revoke(http, it) },
            transportFactory = { tokens -> GoogleDriveTransport(DriveRestSnapshotStore(http, tokens::accessToken)) },
        )
    }

    private fun openInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
        }
    }
}
