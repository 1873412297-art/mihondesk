package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

internal val PrefsHorizontalPadding = 16.dp
internal val PrefsVerticalPadding = 12.dp
internal val TrailingWidgetBuffer = 16.dp

@Composable
fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = PrefsHorizontalPadding, vertical = PrefsVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(end = 16.dp),
                content = { icon() },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp,
                )
            }
            subcomponent?.invoke(this)
        }
        if (widget != null) {
            Box(
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                content = { widget() },
            )
        }
    }
}

@Composable
fun TextPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
) {
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                )
            }
        } else {
            null
        },
        icon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    tint = iconTint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            null
        },
        onClick = onPreferenceClick,
        widget = widget,
    )
}

@Composable
fun SwitchPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean = false,
    switchTag: String? = null,
    onCheckedChanged: (Boolean) -> Unit,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.then(
                    if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
                ),
            )
        },
        onPreferenceClick = { onCheckedChanged(!checked) },
    )
}
