package mihon.companion.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.companion.data.CompanionSyncRepository
import mihon.companion.data.FileSyncStateStore
import mihon.companion.data.LibraryStore
import mihon.companion.data.SyncStateFile
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncReport
import mihon.sync.transport.api.SyncTransport

/** Snapshot of the sync side for the UI. */
data class SyncStatus(
    val running: Boolean = false,
    val accountEmail: String? = null,
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    val lastReport: SyncReport? = null,
    val autoSync: Boolean = true,
) {
    val signedIn: Boolean get() = accountEmail != null
}

/** Runs one pull-merge-push round against Drive, with the same [SyncEngine] the desktop uses. */
class CompanionSyncService(
    private val library: LibraryStore,
    private val stateStore: FileSyncStateStore,
    private val tokens: DriveTokenSource,
    private val transportFactory: () -> SyncTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val repository = CompanionSyncRepository(library) { stateStore.getDeviceId() }
    private val state = MutableStateFlow(statusFromDisk())

    val status: StateFlow<SyncStatus> = state.asStateFlow()

    fun deviceId(): String = stateStore.read().deviceId

    suspend fun syncNow(): SyncReport? = mutex.withLock {
        if (!state.value.signedIn) return@withLock null
        state.value = state.value.copy(running = true)
        try {
            tokens.reset()
            val report = SyncEngine(repository, transportFactory(), stateStore, clock).syncNow()
            if (report.success && report.pushedChangeset != null) library.acknowledge(repository.exportedSeq)
            val saved = stateStore.update {
                if (report.success) {
                    it.copy(lastSyncAt = clock(), lastError = null)
                } else {
                    it.copy(lastError = report.errorMessage)
                }
            }
            state.value = statusFrom(saved).copy(lastReport = report)
            report
        } finally {
            if (state.value.running) state.value = state.value.copy(running = false)
        }
    }

    fun onSignedIn(email: String?) {
        state.value =
            statusFrom(stateStore.update { it.copy(accountEmail = email ?: GOOGLE_ACCOUNT, lastError = null) })
    }

    /** Forgets the account locally; returns the email so the caller can revoke the grant. */
    fun onSignedOut(): String? {
        val email = stateStore.read().accountEmail
        state.value = statusFrom(stateStore.update { it.copy(accountEmail = null, lastError = null) })
        return email?.takeIf { it != GOOGLE_ACCOUNT }
    }

    fun setAutoSync(enabled: Boolean) {
        state.value =
            statusFrom(stateStore.update { it.copy(autoSync = enabled) }).copy(lastReport = state.value.lastReport)
    }

    /** The next sync downloads every peer's data again (repairs a phone library that lost entries). */
    fun resetPeers() = stateStore.resetPeers()

    private fun statusFromDisk() = statusFrom(stateStore.read())

    private fun statusFrom(file: SyncStateFile) = SyncStatus(
        running = false,
        accountEmail = file.accountEmail,
        lastSyncAt = file.lastSyncAt,
        lastError = file.lastError,
        autoSync = file.autoSync,
    )

    private companion object {
        /** Placeholder when Google does not disclose the account email. */
        const val GOOGLE_ACCOUNT = "Google"
    }
}
