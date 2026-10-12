package mihon.desktop.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.input.ReaderShortcutAction
import mihon.desktop.ui.shortcut.BindableKeys
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyChord
import mihon.desktop.ui.shortcut.ShellShortcutAction
import mihon.desktop.ui.shortcut.ShortcutAssignmentPlan
import mihon.desktop.ui.shortcut.ShortcutCaptureState
import mihon.desktop.ui.shortcut.ShortcutTarget
import mihon.desktop.ui.shortcut.chordsFor
import mihon.desktop.ui.shortcut.defaultChords
import mihon.desktop.ui.shortcut.planAssignment
import mihon.desktop.ui.shortcut.resetToDefault
import mihon.desktop.ui.shortcut.shortcutLabel
import mihon.desktop.ui.shortcut.withoutChord

internal fun DesktopStrings.shortcutTargetLabel(target: ShortcutTarget): String = when (target) {
    is ShortcutTarget.Global -> shortcutLabel(target.action)
    is ShortcutTarget.Reader -> shortcutLabel(target.action)
}

internal fun DesktopStrings.shortcutScopeLabel(target: ShortcutTarget): String = when (target) {
    is ShortcutTarget.Global -> text(UiText.ShortcutScopeGlobal)
    is ShortcutTarget.Reader -> text(UiText.ShortcutScopeReader)
}

internal fun ShortcutTarget.testId(): String = when (this) {
    is ShortcutTarget.Global -> "global-${action.name}"
    is ShortcutTarget.Reader -> "reader-${action.name}"
}

private fun ShortcutTarget.isDefault(bindings: DesktopKeyBindings): Boolean = bindings.chordsFor(this) ==
    when (this) {
        is ShortcutTarget.Global -> action.defaultChords()
        is ShortcutTarget.Reader -> action.defaultChords()
    }

/** Settings → Keyboard shortcuts: rebind shell and reader keys, with conflict checks and resets. */
@Composable
internal fun ShortcutSettingsPane(store: DesktopKeyBindingStore) {
    val strings = LocalStrings.current
    var bindings by remember(store) { mutableStateOf(store.load()) }
    var captureTarget by remember { mutableStateOf<ShortcutTarget?>(null) }

    fun commit(updated: DesktopKeyBindings) {
        store.save(updated)
        bindings = store.load()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("shortcut-settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.text(UiText.ShortcutSettingsTitle),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { commit(DesktopKeyBindings.Default) },
                enabled = bindings != DesktopKeyBindings.Default,
                modifier = Modifier.testTag("shortcut-reset-all"),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.text(UiText.ShortcutResetDefaults))
            }
        }
        Text(
            strings.text(UiText.ShortcutSettingsHint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShortcutScopeCard(
            title = strings.text(UiText.ShortcutScopeGlobal),
            subtitle = strings.text(UiText.ShortcutScopeGlobalHint),
            targets = ShellShortcutAction.entries.map { ShortcutTarget.Global(it) },
            bindings = bindings,
            onAdd = { captureTarget = it },
            onRemove = { target, chord -> commit(bindings.withoutChord(target, chord)) },
            onReset = { commit(bindings.resetToDefault(it)) },
        )
        ShortcutScopeCard(
            title = strings.text(UiText.ShortcutScopeReader),
            subtitle = null,
            targets = ReaderShortcutAction.entries.map { ShortcutTarget.Reader(it) },
            bindings = bindings,
            onAdd = { captureTarget = it },
            onRemove = { target, chord -> commit(bindings.withoutChord(target, chord)) },
            onReset = { commit(bindings.resetToDefault(it)) },
        )
    }

    captureTarget?.let { target ->
        ShortcutCaptureDialog(
            target = target,
            bindings = bindings,
            onDismiss = { captureTarget = null },
            onConfirm = { updated ->
                commit(updated)
                captureTarget = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutScopeCard(
    title: String,
    subtitle: String?,
    targets: List<ShortcutTarget>,
    bindings: DesktopKeyBindings,
    onAdd: (ShortcutTarget) -> Unit,
    onRemove: (ShortcutTarget, KeyChord) -> Unit,
    onReset: (ShortcutTarget) -> Unit,
) {
    val strings = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            targets.forEachIndexed { index, target ->
                if (index > 0) HorizontalDivider()
                val label = strings.shortcutTargetLabel(target)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("shortcut-row-${target.testId()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        if (!target.isDefault(bindings)) {
                            Text(
                                strings.text(UiText.ShortcutChanged),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.weight(0.6f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val chords = bindings.chordsFor(target)
                        if (chords.isEmpty()) {
                            Text(
                                strings.text(UiText.ShortcutUnbound),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        chords.forEach { chord ->
                            val chordLabel = chord.displayLabel()
                            InputChip(
                                selected = false,
                                onClick = { onRemove(target, chord) },
                                label = { Text(chordLabel) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = strings.text(UiText.ShortcutRemove, chordLabel),
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.testTag("shortcut-chip-${target.testId()}-${chord.serialize()}"),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onAdd(target) },
                        modifier = Modifier.testTag("shortcut-add-${target.testId()}"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = strings.text(UiText.ShortcutAdd, label))
                    }
                    IconButton(
                        onClick = { onReset(target) },
                        enabled = !target.isDefault(bindings),
                        modifier = Modifier.testTag("shortcut-reset-${target.testId()}"),
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = strings.text(UiText.ShortcutResetAction, label),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Records one key chord. Esc cancels; a chord already in use shows who owns it and lets the user move
 * it here (or keep both when the clash is only with the other scope).
 */
@Composable
internal fun ShortcutCaptureDialog(
    target: ShortcutTarget,
    bindings: DesktopKeyBindings,
    onDismiss: () -> Unit,
    onConfirm: (DesktopKeyBindings) -> Unit,
) {
    val strings = LocalStrings.current
    var plan by remember(target) { mutableStateOf<ShortcutAssignmentPlan?>(null) }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        ShortcutCaptureState.active = true
        onDispose { ShortcutCaptureState.active = false }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shortcut-capture-dialog"),
        icon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
        title = { Text(strings.text(UiText.ShortcutCaptureTitle)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key in BindableKeys.modifierKeys) return@onPreviewKeyEvent true
                        val chord = KeyChord.from(event)
                        if (chord == KeyChord(Key.Escape)) {
                            onDismiss()
                            return@onPreviewKeyEvent true
                        }
                        val next = bindings.planAssignment(target, chord)
                        if (next.hasConflict) plan = next else onConfirm(next.replaced)
                        true
                    }
                    .testTag("shortcut-capture-area"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.text(UiText.ShortcutCaptureFor, strings.shortcutTargetLabel(target)))
                    plan?.let { pending ->
                        val chordLabel = pending.chord.displayLabel()
                        Text(
                            chordLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("shortcut-capture-chord"),
                        )
                        pending.sameScopeConflicts.forEach { other ->
                            Text(
                                strings.text(UiText.ShortcutConflict, chordLabel, strings.shortcutTargetLabel(other)),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("shortcut-conflict"),
                            )
                        }
                        pending.otherScopeConflicts.forEach { other ->
                            Text(
                                strings.text(
                                    UiText.ShortcutConflictOtherScope,
                                    chordLabel,
                                    strings.shortcutScopeLabel(other),
                                    strings.shortcutTargetLabel(other),
                                ),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("shortcut-conflict-other-scope"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val pending = plan
            if (pending != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pending.keepBoth?.let { keepBoth ->
                        TextButton(
                            onClick = { onConfirm(keepBoth) },
                            modifier = Modifier.testTag("shortcut-keep-both"),
                        ) {
                            Text(strings.text(UiText.ShortcutKeepBoth))
                        }
                    }
                    TextButton(
                        onClick = { onConfirm(pending.replaced) },
                        modifier = Modifier.testTag("shortcut-replace"),
                    ) {
                        Text(strings.text(UiText.ShortcutReplace))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("shortcut-capture-cancel")) {
                Text(strings.dialogCancel)
            }
        },
    )
}
