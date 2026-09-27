package mihon.desktop.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTooltipBox(
    text: String = "",
    tooltip: String = text,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val display = if (tooltip.isNotBlank()) tooltip else text
    if (display.isBlank()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(display)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
