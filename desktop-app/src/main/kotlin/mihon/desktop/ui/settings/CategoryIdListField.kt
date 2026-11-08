package mihon.desktop.ui.settings

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Category ID list entry that keeps the raw text while editing.
 *
 * Deriving the field value from the parsed set drops a separator as soon as it is typed, so the next
 * digit is appended to the previous ID ("1" + "," + "2" became "12").
 */
@Composable
internal fun CategoryIdListField(
    label: String,
    selected: Set<Long>,
    onSelectedChange: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(selected.sorted().joinToString(",")) }
    OutlinedTextField(
        value = draft,
        onValueChange = { value ->
            draft = value
            onSelectedChange(parsePositiveLongSet(value))
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}
