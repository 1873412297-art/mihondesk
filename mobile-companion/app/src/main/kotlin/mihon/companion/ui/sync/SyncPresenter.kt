package mihon.companion.ui.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.companion.CompanionLog
import mihon.companion.CompanionRuntime
import mihon.companion.R
import mihon.companion.sync.DriveAuthorizer
import mihon.companion.sync.SyncStatus

data class UiMessage(@StringRes val text: Int, val args: List<Any> = emptyList())

data class SyncUiState(
    val status: SyncStatus = SyncStatus(),
    val pendingChanges: Int = 0,
    val deviceId: String = "",
    /** Google consent screen waiting to be launched by the UI. */
    val consent: PendingIntent? = null,
    val message: UiMessage? = null,
    val busy: Boolean = false,
)

sealed interface SyncAction {
    data object SignIn : SyncAction
    data object ConsentLaunched : SyncAction
    data class ConsentResult(val resultCode: Int, val data: Intent?) : SyncAction
    data object SignOut : SyncAction
    data object SyncNow : SyncAction
    data class SetAutoSync(val enabled: Boolean) : SyncAction
    data class Import(val uri: Uri) : SyncAction
    data class Export(val uri: Uri) : SyncAction
    data object Repull : SyncAction
    data object MessageShown : SyncAction
}

private data class Transient(
    val consent: PendingIntent? = null,
    val message: UiMessage? = null,
    val busy: Boolean = false,
)

class SyncPresenter(
    private val runtime: CompanionRuntime,
    private val resolver: ContentResolver,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val backups = BackupTransfer(runtime, resolver)
    private val deviceId = runtime.sync.deviceId()

    val state: StateFlow<SyncUiState> = combine(runtime.sync.status, runtime.library.library, transient) {
            status,
            library,
            t,
        ->
        SyncUiState(status, library.pendingChanges, deviceId, t.consent, t.message, t.busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun onAction(action: SyncAction) {
        when (action) {
            SyncAction.SignIn -> launch { authorized(runtime.authorizer.authorize()) }
            SyncAction.ConsentLaunched -> transient.value = transient.value.copy(consent = null)
            is SyncAction.ConsentResult -> launch {
                if (action.resultCode != Activity.RESULT_OK) {
                    show(UiMessage(R.string.sign_in_cancelled))
                } else {
                    authorized(runtime.authorizer.fromIntent(action.data))
                }
            }
            SyncAction.SignOut -> launch { signOut() }
            SyncAction.SyncNow -> launch { runtime.sync.syncNow() }
            is SyncAction.SetAutoSync -> {
                runtime.sync.setAutoSync(action.enabled)
                runtime.scheduler.apply(action.enabled && runtime.sync.status.value.signedIn)
            }
            is SyncAction.Import -> busy {
                show(backups.import(action.uri))
                runtime.sync.syncNow()
            }
            is SyncAction.Export -> busy { show(backups.export(action.uri)) }
            SyncAction.Repull -> {
                runtime.sync.resetPeers()
                show(UiMessage(R.string.sync_repull_done))
            }
            SyncAction.MessageShown -> transient.value = transient.value.copy(message = null)
        }
    }

    private suspend fun authorized(outcome: DriveAuthorizer.Outcome) {
        when (outcome) {
            is DriveAuthorizer.Outcome.NeedsConsent -> transient.value = transient.value.copy(consent = outcome.intent)
            is DriveAuthorizer.Outcome.Granted -> {
                runtime.sync.onSignedIn(outcome.email)
                runtime.scheduler.apply(runtime.sync.status.value.autoSync)
                runtime.sync.syncNow()
            }
        }
    }

    private suspend fun signOut() {
        val email = runtime.sync.onSignedOut()
        runtime.scheduler.apply(false)
        if (email != null) runtime.authorizer.revoke(email)
    }

    private fun show(message: UiMessage) {
        transient.value = transient.value.copy(message = message)
    }

    private fun busy(block: suspend () -> Unit) = launch {
        transient.value = transient.value.copy(busy = true)
        try {
            block()
        } finally {
            transient.value = transient.value.copy(busy = false)
        }
    }

    /** Runs [block]; sign-in failures become a visible message instead of disappearing. */
    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CompanionLog.warn(TAG, "Sync screen action failed", error)
                show(UiMessage(R.string.sign_in_failed, listOf(error.message ?: error.javaClass.simpleName)))
            }
        }
    }

    private companion object {
        const val TAG = "SyncPresenter"
    }
}
