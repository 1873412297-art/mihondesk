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
import mihon.companion.mihonfolder.MihonFolderStatus
import mihon.companion.sync.DriveAuthorizer
import mihon.companion.sync.SyncStatus

data class UiMessage(@StringRes val text: Int, val args: List<Any> = emptyList())

data class SyncUiState(
    val status: SyncStatus = SyncStatus(),
    val mihon: MihonFolderStatus = MihonFolderStatus(),
    val pendingChanges: Int = 0,
    val deviceId: String = "",
    /** Google consent screen waiting to be launched by the UI. */
    val consent: PendingIntent? = null,
    val message: UiMessage? = null,
    val busy: Boolean = false,
    /** Why the last sign-in attempt failed; stays visible until the next attempt. */
    val signInError: UiMessage? = null,
    val packageName: String = "",
    val signingSha1: String = "",
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
    data class PickMihonFolder(val uri: Uri) : SyncAction
    data object ClearMihonFolder : SyncAction
    data object MessageShown : SyncAction
}

private data class Transient(
    val consent: PendingIntent? = null,
    val message: UiMessage? = null,
    val busy: Boolean = false,
    val signInError: UiMessage? = null,
)

class SyncPresenter(
    private val runtime: CompanionRuntime,
    private val resolver: ContentResolver,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val backups = BackupTransfer(runtime, resolver)
    private val deviceId = runtime.sync.deviceId()
    private val folders = MihonFolderPicker(runtime, resolver)

    val state: StateFlow<SyncUiState> = combine(
        runtime.sync.status,
        runtime.mihonFolder.status,
        runtime.library.library,
        transient,
    ) { status, mihon, library, t ->
        SyncUiState(
            status = status,
            mihon = mihon,
            pendingChanges = library.pendingChanges,
            deviceId = deviceId,
            consent = t.consent,
            message = t.message,
            busy = t.busy,
            signInError = t.signInError,
            packageName = runtime.signature.packageName,
            signingSha1 = runtime.signature.sha1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun onAction(action: SyncAction) {
        when (action) {
            SyncAction.SignIn -> signIn { authorized(runtime.authorizer.authorize()) }
            SyncAction.ConsentLaunched -> transient.value = transient.value.copy(consent = null)
            is SyncAction.ConsentResult -> signIn {
                // Even a cancelled result usually carries Google's real status (e.g. DEVELOPER_ERROR),
                // which fromIntent() throws as an ApiException.
                val data = action.data
                if (data == null && action.resultCode != Activity.RESULT_OK) {
                    transient.value = transient.value.copy(signInError = SignInErrors.cancelled)
                } else {
                    authorized(runtime.authorizer.fromIntent(data))
                }
            }
            SyncAction.SignOut -> launch { signOut() }
            SyncAction.SyncNow -> launch { runtime.refresher.refresh() }
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
            is SyncAction.PickMihonFolder -> busy { folders.pick(action.uri)?.let(::show) }
            SyncAction.ClearMihonFolder -> launch { folders.clear() }
            SyncAction.MessageShown -> transient.value = transient.value.copy(message = null)
        }
    }

    private suspend fun authorized(outcome: DriveAuthorizer.Outcome) {
        when (outcome) {
            is DriveAuthorizer.Outcome.NeedsConsent -> transient.value = transient.value.copy(consent = outcome.intent)
            is DriveAuthorizer.Outcome.Granted -> {
                runtime.sync.onSignedIn(outcome.email)
                runtime.scheduler.apply(runtime.sync.status.value.autoSync)
                runtime.refresher.refresh()
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

    /** A sign-in step whose failure is kept on the account card instead of a passing snackbar. */
    private fun signIn(block: suspend () -> Unit) {
        transient.value = transient.value.copy(signInError = null)
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CompanionLog.warn(TAG, "Google sign-in failed", error)
                transient.value = transient.value.copy(signInError = SignInErrors.describe(error))
            }
        }
    }

    /** Runs [block]; a failure becomes a visible message instead of disappearing. */
    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CompanionLog.warn(TAG, "Sync screen action failed", error)
                show(UiMessage(R.string.action_failed, listOf(error.message ?: error.javaClass.simpleName)))
            }
        }
    }

    private companion object {
        const val TAG = "SyncPresenter"
    }
}
