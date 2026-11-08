package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.security.APP_LOCK_TIMEOUT_OPTIONS
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.MIN_PIN_LENGTH

@Composable
internal fun SecuritySettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    appLockController: DesktopAppLockController,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val preferences = presenter.preferences.value
    var showSetPinDialog by remember { mutableStateOf(false) }
    var setPin by remember { mutableStateOf("") }
    var setPinConfirm by remember { mutableStateOf("") }
    var setPinError by remember { mutableStateOf<String?>(null) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    fun refreshPreferences() {
        presenter.reloadPreferences()
        appLockController.refresh()
        onPreferencesChanged?.invoke(presenter.preferences.value)
    }

    fun enableWithPin(pin: String, confirm: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH) {
            setPinError = strings.text(UiText.PinMinimum, MIN_PIN_LENGTH)
            return false
        }
        if (pin != confirm) {
            setPinError = strings.text(UiText.PinsMismatch)
            return false
        }
        val enabled = appLockController.enableWithPin(
            pin = pin,
            lockOnStartup = preferences.appLockOnStartup,
            idleTimeoutMinutes = preferences.appLockIdleTimeoutMinutes,
        )
        if (!enabled) {
            setPinError = strings.text(UiText.CouldNotSetPin)
            return false
        }
        setPin = ""
        setPinConfirm = ""
        setPinError = null
        refreshPreferences()
        pinMessage = strings.text(UiText.LockEnabled)
        return true
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = strings.text(UiText.Security),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Enable/disable + status
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(strings.text(UiText.AppLock), fontWeight = FontWeight.Bold)
                    Text(
                        text = if (preferences.appLockEnabled) {
                            strings.text(UiText.LockEnabledHint)
                        } else {
                            strings.text(UiText.LockDisabledHint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("security-status-text"),
                    )
                }
                Switch(
                    checked = preferences.appLockEnabled,
                    onCheckedChange = { checked ->
                        pinError = null
                        pinMessage = null
                        if (checked) {
                            when {
                                appLockController.isPinConfigured() -> {
                                    appLockController.enableWithStoredPin()
                                    refreshPreferences()
                                    pinMessage = strings.text(UiText.LockEnabled)
                                }
                                setPin.length >= MIN_PIN_LENGTH && setPin == setPinConfirm -> {
                                    enableWithPin(setPin, setPinConfirm)
                                }
                                else -> {
                                    setPinError = null
                                    showSetPinDialog = true
                                }
                            }
                        } else {
                            appLockController.disableLock()
                            refreshPreferences()
                            pinMessage = strings.text(UiText.LockDisabled)
                        }
                    },
                    modifier = Modifier.testTag("security-enable-switch"),
                )
            }
        }

        if (!preferences.appLockEnabled && !showSetPinDialog) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.text(UiText.SetPin), fontWeight = FontWeight.Bold)
                    Text(
                        strings.text(UiText.PinHint, MIN_PIN_LENGTH),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = setPin,
                        onValueChange = {
                            setPin = it
                            setPinError = null
                        },
                        label = { Text(strings.text(UiText.NewPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-pin-field"),
                    )
                    OutlinedTextField(
                        value = setPinConfirm,
                        onValueChange = {
                            setPinConfirm = it
                            setPinError = null
                        },
                        label = { Text(strings.text(UiText.ConfirmPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-pin-confirm-field"),
                    )
                    setPinError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("security-pin-error"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { enableWithPin(setPin, setPinConfirm) },
                            modifier = Modifier.testTag("security-enable-button"),
                        ) {
                            Text(strings.text(UiText.EnableLock))
                        }
                        OutlinedButton(
                            onClick = {
                                setPinError = null
                                showSetPinDialog = true
                            },
                            modifier = Modifier.testTag("security-set-pin-button"),
                        ) {
                            Text(strings.text(UiText.SetPinMore))
                        }
                    }
                }
            }
        }

        if (preferences.appLockEnabled) {
            ChangePinCard(appLockController, onChanged = ::refreshPreferences, onMessage = { pinMessage = it })

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(strings.text(UiText.LockBehavior), fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(strings.text(UiText.LockOnStartup), fontWeight = FontWeight.Medium)
                            Text(
                                strings.text(UiText.LockOnStartupHint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preferences.appLockOnStartup,
                            onCheckedChange = {
                                appLockController.setLockOnStartup(it)
                                refreshPreferences()
                            },
                            modifier = Modifier.testTag("security-lock-on-startup-switch"),
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.text(UiText.AutoLock), fontWeight = FontWeight.Medium)
                        Text(
                            strings.text(UiText.AutoLockHint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            APP_LOCK_TIMEOUT_OPTIONS.forEach { minutes ->
                                val isSelected = preferences.appLockIdleTimeoutMinutes == minutes
                                val label = if (minutes ==
                                    0
                                ) {
                                    strings.text(UiText.Never)
                                } else {
                                    strings.text(UiText.Minutes, minutes)
                                }
                                if (isSelected) {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.testTag("security-timeout-$minutes"),
                                    ) {
                                        Text(label)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            appLockController.setIdleTimeoutMinutes(minutes)
                                            refreshPreferences()
                                        },
                                        modifier = Modifier.testTag("security-timeout-$minutes"),
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(strings.text(UiText.LockNow), fontWeight = FontWeight.Medium)
                            Text(
                                strings.text(UiText.LockNowHint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { appLockController.lockNow() },
                            modifier = Modifier.testTag("security-lock-now-button"),
                        ) {
                            Text(strings.text(UiText.LockNow))
                        }
                    }
                }
            }
        }

        pinMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("security-pin-message"),
            )
        }
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(strings.text(UiText.SetAppLockPin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.text(UiText.PinHint, MIN_PIN_LENGTH),
                    )
                    OutlinedTextField(
                        value = setPin,
                        onValueChange = {
                            setPin = it
                            setPinError = null
                        },
                        label = { Text(strings.text(UiText.NewPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-pin-field"),
                    )
                    OutlinedTextField(
                        value = setPinConfirm,
                        onValueChange = {
                            setPinConfirm = it
                            setPinError = null
                        },
                        label = { Text(strings.text(UiText.ConfirmPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-pin-confirm-field"),
                    )
                    setPinError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("security-pin-error"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (enableWithPin(setPin, setPinConfirm)) {
                            showSetPinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("security-set-pin-confirm-button"),
                ) {
                    Text(strings.dialogOk)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSetPinDialog = false
                        setPinError = null
                    },
                    modifier = Modifier.testTag("security-set-pin-cancel-button"),
                ) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }
}
