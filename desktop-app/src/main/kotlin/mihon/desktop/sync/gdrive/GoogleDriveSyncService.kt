package mihon.desktop.sync.gdrive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.sync.core.model.Changeset
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.engine.SyncReport
import mihon.sync.transport.api.SyncTransport
import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveSnapshotCorruptException
import mihon.sync.transport.gdrive.DriveTokenProvider
import mihon.sync.transport.gdrive.GoogleTokens
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class GoogleDriveError { NOT_CONFIGURED, AUTH_CANCELLED, AUTH_EXPIRED, NETWORK, CORRUPT, UNKNOWN }

enum class GoogleDriveBusy { IDLE, SIGNING_IN, SYNCING }

data class GoogleDriveSyncState(
    val configured: Boolean,
    val signedIn: Boolean,
    val busy: GoogleDriveBusy = GoogleDriveBusy.IDLE,
    val lastSyncAt: Long = 0,
    val lastSummary: String = "",
    val error: GoogleDriveError? = null,
    val errorDetail: String? = null,
)

/** Desktop orchestration for Drive sync: sign-in, token refresh, debounced sync, UI state. */
class GoogleDriveSyncService(
    private val config: GoogleDriveConfig,
    private val vault: DriveTokenVault,
    private val stateStore: FileDriveSyncStateStore,
    private val localRepository: () -> SyncLocalRepository,
    private val scope: CoroutineScope,
    /** Interactive sign-in (browser + loopback). */
    private val authorizer: suspend () -> GoogleTokens,
    private val refresher: suspend (GoogleTokens) -> GoogleTokens,
    private val revoker: suspend (String) -> Unit = {},
    private val transportFactory: (DriveTokenProvider) -> SyncTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMillis: Long = 4_000L,
) {
    private val mutex = Mutex()
    private var requestJob: Job? = null
    private val _state = MutableStateFlow(
        GoogleDriveSyncState(
            configured = config.isConfigured,
            signedIn = vault.load()?.refreshToken != null,
            lastSyncAt = stateStore.lastSyncAt(),
            lastSummary = stateStore.read().lastSummary,
            error = if (config.isConfigured) null else GoogleDriveError.NOT_CONFIGURED,
        ),
    )
    val state: StateFlow<GoogleDriveSyncState> = _state.asStateFlow()

    suspend fun signIn(): Boolean {
        if (!config.isConfigured) {
            _state.update {
                it.copy(error = GoogleDriveError.NOT_CONFIGURED, errorDetail = GoogleDriveConfig.MISSING_SECRET_HINT)
            }
            return false
        }
        _state.update { it.copy(busy = GoogleDriveBusy.SIGNING_IN, error = null, errorDetail = null) }
        return try {
            val tokens = authorizer()
            if (tokens.refreshToken.isNullOrBlank()) throw DriveAuthException("Google returned no refresh token")
            vault.save(tokens)
            stateStore.reset()
            _state.update { it.copy(signedIn = true, busy = GoogleDriveBusy.IDLE, lastSyncAt = 0, lastSummary = "") }
            true
        } catch (e: CancellationException) {
            _state.update { it.copy(busy = GoogleDriveBusy.IDLE) }
            if (e is TimeoutCancellationException) {
                _state.update { it.copy(error = GoogleDriveError.AUTH_CANCELLED) }
                false
            } else {
                throw e
            }
        } catch (e: Exception) {
            val err = if (e is OAuthCancelledException) GoogleDriveError.AUTH_CANCELLED else classify(e)
            _state.update { it.copy(busy = GoogleDriveBusy.IDLE, error = err, errorDetail = e.message) }
            false
        }
    }

    suspend fun signOut() = mutex.withLock {
        requestJob?.cancel()
        vault.load()?.let { t -> runCatching { revoker(t.refreshToken ?: t.accessToken) } }
        vault.clear()
        stateStore.reset()
        _state.update {
            it.copy(
                signedIn = false,
                busy = GoogleDriveBusy.IDLE,
                lastSyncAt = 0,
                lastSummary = "",
                error = null,
                errorDetail = null,
            )
        }
    }

    /** Runs a sync now; returns null when not signed in / not configured. */
    suspend fun syncNow(): SyncReport? = mutex.withLock {
        if (!config.isConfigured || !_state.value.signedIn) return@withLock null
        val tokens = vault.load() ?: run {
            _state.update { it.copy(signedIn = false, error = GoogleDriveError.AUTH_EXPIRED) }
            return@withLock null
        }
        _state.update { it.copy(busy = GoogleDriveBusy.SYNCING, error = null, errorDetail = null) }
        val provider = DriveTokenProvider(tokens, refresh = refresher, onUpdated = { vault.save(it) }, clock = clock)
        val recorder = RecordingTransport(transportFactory(provider))
        val report = withContext(Dispatchers.IO) {
            try {
                SyncEngine(localRepository(), recorder, stateStore, clock).syncNow()
            } catch (e: CancellationException) {
                _state.update { it.copy(busy = GoogleDriveBusy.IDLE) }
                throw e
            }
        }
        if (report.success) {
            val now = clock()
            val pushed = if (report.pushedChangeset != null) 1 else 0
            val summary = "↓${report.pulledChangesetCount} ↑$pushed · ${report.totalChanged}"
            stateStore.recordSync(now, summary)
            _state.update { it.copy(busy = GoogleDriveBusy.IDLE, lastSyncAt = now, lastSummary = summary) }
        } else {
            val cause = recorder.lastError
            val err = cause?.let(::classify) ?: GoogleDriveError.UNKNOWN
            _state.update {
                it.copy(
                    busy = GoogleDriveBusy.IDLE,
                    error = err,
                    errorDetail = cause?.message ?: report.errorMessage,
                    signedIn = if (err == GoogleDriveError.AUTH_EXPIRED) false else it.signedIn,
                )
            }
            if (err == GoogleDriveError.AUTH_EXPIRED) vault.clear()
        }
        report
    }

    /** Debounced trigger (app start, chapter finished, library edits). */
    fun requestSync() {
        if (!config.isConfigured || !_state.value.signedIn) return
        synchronized(this) {
            requestJob?.cancel()
            requestJob = scope.launch(Dispatchers.IO) {
                delay(debounceMillis)
                try {
                    syncNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mihon.desktop.logging.DesktopLogger.warn("GoogleDriveSync", "Requested Drive sync failed", e)
                }
            }
        }
    }

    private class RecordingTransport(private val delegate: SyncTransport) : SyncTransport {
        @Volatile var lastError: Throwable? = null
        private inline fun <T> rec(block: () -> T): T = try {
            block()
        } catch (e: Throwable) {
            if (e !is CancellationException) lastError = e
            throw e
        }
        override suspend fun push(changeset: Changeset) = rec { delegate.push(changeset) }
        override suspend fun pull(sinceCursors: Map<String, Long>, excludeDeviceId: String?) =
            rec { delegate.pull(sinceCursors, excludeDeviceId) }
        override suspend fun headCursor(excludeDeviceId: String?) = rec { delegate.headCursor(excludeDeviceId) }
    }

    companion object {
        fun classify(e: Throwable): GoogleDriveError {
            var t: Throwable? = e
            while (t != null) {
                when (t) {
                    is DriveAuthException -> return GoogleDriveError.AUTH_EXPIRED
                    is DriveSnapshotCorruptException -> return GoogleDriveError.CORRUPT
                    is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException ->
                        return GoogleDriveError.NETWORK
                }
                t = t.cause
            }
            return if (e is IOException) GoogleDriveError.NETWORK else GoogleDriveError.UNKNOWN
        }
    }
}
