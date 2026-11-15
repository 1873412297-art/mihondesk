package mihon.companion.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mihon.companion.R

/** Sign-in failure (in red) plus the values Google Cloud must know, selectable for copying. */
@Composable
internal fun SignInHelp(error: UiMessage?, packageName: String, sha1: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (error != null) {
            Text(
                context.getString(error.text, *error.args.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SelectionContainer {
            Text(
                stringResource(R.string.signing_info, packageName, sha1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
