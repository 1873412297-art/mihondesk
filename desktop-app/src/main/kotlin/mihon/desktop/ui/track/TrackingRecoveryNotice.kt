package mihon.desktop.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.track.ConflictResolutionPolicy
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.TrackOnReadSyncService

/** Recovery appears only when account access or conflicting reading progress needs attention. */
@Composable
fun TrackingRecoveryNotice(service: TrackOnReadSyncService, manager: DesktopTrackerManager) {
    val authentication by service.authenticationRequired.collectAsState()
    val conflicts by service.conflicts.collectAsState()
    var open by remember { mutableStateOf(false) }
    var loginTracker by remember { mutableStateOf<DesktopTracker?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    if (authentication.isNotEmpty() || conflicts.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("tracking-recovery-notice"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.text(UiText.TrackingNeedsAttention))
                TextButton(onClick = { open = true }) { Text(strings.text(UiText.TrackingReview)) }
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(strings.text(UiText.TrackingResumeTitle)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp).testTag("tracking-recovery-dialog"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(authentication.toList(), key = { "auth-$it" }) { id ->
                        val tracker = manager.get(id)
                        Column {
                            Text(tracker?.name ?: "#$id", style = MaterialTheme.typography.titleSmall)
                            Text(
                                strings.text(
                                    UiText.TrackingSignInAgain,
                                ),
                            )
                            if (tracker != null) {
                                TextButton(onClick = { loginTracker = tracker }) {
                                    Text(strings.text(UiText.TrackingSignIn))
                                }
                            }
                        }
                    }
                    items(conflicts, key = { "conflict-${it.mangaId}-${it.trackerId}" }) { conflict ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${conflict.mangaTitle} · ${conflict.trackerName}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                strings.text(
                                    UiText.TrackingConflictSummary,
                                    conflict.localChapterRead,
                                    conflict.remoteChapterRead,
                                ),
                            )
                            Row {
                                listOf(
                                    ConflictResolutionPolicy.LOCAL_WINS,
                                    ConflictResolutionPolicy.REMOTE_WINS,
                                ).forEach { policy ->
                                    TextButton(
                                        enabled = !submitting,
                                        onClick = {
                                            scope.launch {
                                                submitting = true
                                                actionError = null
                                                try {
                                                    service.resolveConflict(
                                                        conflict.mangaId,
                                                        conflict.trackerId,
                                                        policy,
                                                    )
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (error: Exception) {
                                                    actionError = error.message
                                                } finally {
                                                    submitting = false
                                                }
                                            }
                                        },
                                    ) {
                                        Text(
                                            if (policy == ConflictResolutionPolicy.LOCAL_WINS) {
                                                strings.text(UiText.TrackingKeepLocal)
                                            } else {
                                                strings.text(UiText.TrackingUseRemote)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    actionError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    if (authentication.isEmpty() && conflicts.isEmpty()) {
                        item { Text(strings.text(UiText.TrackingCanContinue)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(strings.dialogClose) } },
        )
    }
    loginTracker?.let { tracker ->
        TrackerLoginDialog(
            tracker = tracker,
            onDismiss = { loginTracker = null },
            onLogin = { manager.login(tracker.id, it) },
        )
    }
}
