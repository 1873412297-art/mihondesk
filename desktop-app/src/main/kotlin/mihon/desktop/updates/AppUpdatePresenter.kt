package mihon.desktop.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

enum class AppUpdatePhase {
    Idle,
    Checking,
    Available,
    Downloading,
    Cancelling,
    Publishing,
    Ready,
    Current,
    Failed,
    Cancelled,
    PreparingUpdate,
    Exiting,
}

data class AppUpdateState(
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val release: UpdateCheckResult.UpdateAvailable? = null,
    val received: Long = 0,
    val total: Long = 0,
    val savedFile: Path? = null,
    val openFailed: Boolean = false,
    val verifiedSha256: String? = null,
    val installFailed: Boolean = false,
    val previousOutcome: AppUpdateOutcome? = null,
) {
    val busy: Boolean get() = phase in
        setOf(
            AppUpdatePhase.Checking,
            AppUpdatePhase.Downloading,
            AppUpdatePhase.Cancelling,
            AppUpdatePhase.Publishing,
            AppUpdatePhase.PreparingUpdate,
            AppUpdatePhase.Exiting,
        )
}

/** UI entry points and state transitions are confined to the supplied application UI scope. */
class AppUpdatePresenter(
    private val service: DesktopAppUpdateService,
    private val scope: CoroutineScope,
    private val installer: AppUpdateInstaller? = null,
    private val onExit: () -> Unit = {},
) {
    val canInstall: Boolean get() = installer?.available == true
    private val mutableState = MutableStateFlow(AppUpdateState(previousOutcome = installer?.lastOutcome()))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private val uiContext = scope.coroutineContext.minusKey(Job)

    fun check() {
        if (state.value.busy) return
        mutableState.value = AppUpdateState(phase = AppUpdatePhase.Checking)
        operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                mutableState.value = when (val result = service.checkForUpdates()) {
                    is UpdateCheckResult.UpdateAvailable -> AppUpdateState(AppUpdatePhase.Available, result)
                    is UpdateCheckResult.UpToDate -> AppUpdateState(AppUpdatePhase.Current)
                    is UpdateCheckResult.CheckFailed -> AppUpdateState(AppUpdatePhase.Failed)
                }
            } catch (_: CancellationException) {
                mutableState.value = AppUpdateState(AppUpdatePhase.Cancelled)
            }
        }
    }

    fun download(destination: Path) {
        if (state.value.busy) return
        val release = state.value.release ?: return
        val asset = release.matchedAsset ?: return
        mutableState.value = AppUpdateState(AppUpdatePhase.Downloading, release, total = asset.size)
        operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val checksum = service.releaseChecksum(release.release, asset)
                val saved = service.downloadAsset(
                    asset,
                    destination,
                    checksum,
                    onProgress = { received, total ->
                        withContext(uiContext) {
                            mutableState.value =
                                state.value.copy(received = received, total = total)
                        }
                    },
                    onPublishing = {
                        withContext(uiContext) {
                            mutableState.value =
                                state.value.copy(phase = AppUpdatePhase.Publishing)
                        }
                    },
                )
                mutableState.value = state.value.copy(
                    phase = if (saved) AppUpdatePhase.Ready else AppUpdatePhase.Failed,
                    savedFile = destination.takeIf { saved },
                    verifiedSha256 = checksum.takeIf { saved },
                )
            } catch (_: CancellationException) {
                mutableState.value = state.value.copy(phase = AppUpdatePhase.Cancelled)
            } catch (_: Exception) {
                mutableState.value = state.value.copy(phase = AppUpdatePhase.Failed)
            }
        }
    }

    fun cancel() {
        if (state.value.phase !in
            setOf(AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.PreparingUpdate)
        ) {
            return
        }
        mutableState.value = state.value.copy(phase = AppUpdatePhase.Cancelling)
        operation?.cancel()
    }

    fun reportOpenFailure() {
        mutableState.value = state.value.copy(openFailed = true)
    }

    fun install() {
        if (!canInstall || state.value.busy ||
            state.value.phase !in setOf(AppUpdatePhase.Ready, AppUpdatePhase.Cancelled)
        ) {
            return
        }
        val file = state.value.savedFile ?: return
        val checksum = state.value.verifiedSha256 ?: return
        mutableState.value = state.value.copy(phase = AppUpdatePhase.PreparingUpdate, installFailed = false)
        operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var prepared: PreparedAppUpdate? = null
            try {
                prepared = requireNotNull(installer).prepare(file, checksum)
                mutableState.value = state.value.copy(phase = AppUpdatePhase.Exiting)
                prepared.commit()
                onExit()
            } catch (_: CancellationException) {
                runCatching { prepared?.abort() }
                mutableState.value = state.value.copy(phase = AppUpdatePhase.Cancelled)
            } catch (_: Exception) {
                runCatching { prepared?.abort() }
                mutableState.value = state.value.copy(
                    phase = AppUpdatePhase.Ready,
                    installFailed = true,
                    previousOutcome = installer?.lastOutcome(),
                )
            }
        }
    }

    suspend fun shutdown() {
        operation?.cancelAndJoin()
    }
}
