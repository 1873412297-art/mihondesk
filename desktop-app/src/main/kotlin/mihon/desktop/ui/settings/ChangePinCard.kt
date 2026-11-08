package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.security.ChangePinResult
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.MIN_PIN_LENGTH

/** Change or disable the app-lock PIN; success is reported to the pane through [onMessage]. */
@Composable
internal fun ChangePinCard(
    appLockController: DesktopAppLockController,
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    val strings = LocalStrings.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.text(UiText.ChangePin), fontWeight = FontWeight.Bold)
            ChangePinFields(
                currentPin = currentPin,
                newPin = newPin,
                newPinConfirm = newPinConfirm,
                error = error,
                onCurrentPinChange = {
                    currentPin = it
                    error = null
                },
                onNewPinChange = {
                    newPin = it
                    error = null
                },
                onConfirmChange = {
                    newPinConfirm = it
                    error = null
                },
            )
            ChangePinActions(
                onChange = {
                    onMessage(null)
                    val failure = changePinFailure(appLockController, strings, currentPin, newPin, newPinConfirm)
                    error = failure
                    if (failure == null) {
                        currentPin = ""
                        newPin = ""
                        newPinConfirm = ""
                        onChanged()
                        onMessage(strings.text(UiText.PinChanged))
                    }
                },
                onDisable = {
                    error = null
                    appLockController.disableLock()
                    onChanged()
                    onMessage(strings.text(UiText.LockDisabled))
                },
            )
        }
    }
}

@Composable
private fun ChangePinFields(
    currentPin: String,
    newPin: String,
    newPinConfirm: String,
    error: String?,
    onCurrentPinChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedTextField(
        value = currentPin,
        onValueChange = onCurrentPinChange,
        label = { Text(strings.text(UiText.CurrentPin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("security-current-pin-field"),
    )
    OutlinedTextField(
        value = newPin,
        onValueChange = onNewPinChange,
        label = { Text(strings.text(UiText.NewPin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("security-new-pin-field"),
    )
    OutlinedTextField(
        value = newPinConfirm,
        onValueChange = onConfirmChange,
        label = { Text(strings.text(UiText.ConfirmNewPin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("security-new-pin-confirm-field"),
    )
    error?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("security-change-pin-error"),
        )
    }
}

@Composable
private fun ChangePinActions(onChange: () -> Unit, onDisable: () -> Unit) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onChange, modifier = Modifier.testTag("security-change-pin-button")) {
            Text(strings.text(UiText.ChangePin))
        }
        OutlinedButton(onClick = onDisable, modifier = Modifier.testTag("security-disable-button")) {
            Text(strings.text(UiText.DisableLock))
        }
    }
}

/** The message to show, or null when the PIN was changed. Mirrors the validation order of the UI. */
private fun changePinFailure(
    appLockController: DesktopAppLockController,
    strings: DesktopStrings,
    currentPin: String,
    newPin: String,
    newPinConfirm: String,
): String? = when {
    currentPin.length < MIN_PIN_LENGTH -> strings.text(UiText.EnterCurrentPin)
    newPin.length < MIN_PIN_LENGTH -> strings.text(UiText.NewPinMinimum, MIN_PIN_LENGTH)
    newPin != newPinConfirm -> strings.text(UiText.NewPinsMismatch)
    else -> when (appLockController.changePin(currentPin, newPin)) {
        ChangePinResult.Success -> null
        ChangePinResult.WrongCurrentPin -> strings.text(UiText.WrongCurrentPin)
        ChangePinResult.InvalidNewPin -> strings.text(UiText.NewPinMinimum, MIN_PIN_LENGTH)
        ChangePinResult.NotConfigured -> strings.text(UiText.LockNotConfigured)
    }
}
