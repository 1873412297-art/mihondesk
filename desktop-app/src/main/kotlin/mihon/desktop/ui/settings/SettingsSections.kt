package mihon.desktop.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

enum class SettingsSection(val label: String, val group: SettingsGroup) {
    General("General", SettingsGroup.App),
    Appearance("Appearance", SettingsGroup.App),
    Shortcuts("Keyboard shortcuts", SettingsGroup.App),
    Security("Security", SettingsGroup.App),
    Library("Library", SettingsGroup.Content),
    Reader("Reader", SettingsGroup.Content),
    Downloads("Downloads", SettingsGroup.Content),
    Tracking("Tracking", SettingsGroup.Content),
    Backup("Backup & Restore", SettingsGroup.Data),
    Advanced("Advanced & Diagnostics", SettingsGroup.Data),
    About("About", SettingsGroup.None),
    ;

    val icon: ImageVector
        get() = when (this) {
            General -> Icons.Rounded.Tune
            Security -> Icons.Rounded.Lock
            Appearance -> Icons.Rounded.Palette
            Library -> Icons.Rounded.CollectionsBookmark
            Reader -> Icons.AutoMirrored.Rounded.ChromeReaderMode
            Shortcuts -> Icons.Rounded.Keyboard
            Downloads -> Icons.Rounded.Download
            Tracking -> Icons.Rounded.SyncAlt
            Backup -> Icons.Rounded.Backup
            Advanced -> Icons.Rounded.Code
            About -> Icons.Rounded.Info
        }

    fun localized(strings: DesktopStrings): String = when (this) {
        General -> strings.settingsSectionGeneral
        Security -> strings.text(UiText.Security)
        Appearance -> strings.settingsSectionAppearance
        Library -> strings.libraryTitle
        Reader -> strings.settingsSectionReader
        Shortcuts -> strings.text(UiText.ShortcutSettingsTitle)
        Downloads -> strings.settingsSectionDownloads
        Tracking -> strings.settingsSectionTracking
        Backup -> strings.settingsSectionBackup
        Advanced -> strings.settingsSectionAdvanced
        About -> strings.aboutTitle
    }
}

/** Visible groups in the Settings sidebar (0.3.0 IA). [None] sections render after a divider. */
enum class SettingsGroup {
    App,
    Content,
    Data,
    None,
    ;

    fun localized(strings: DesktopStrings): String? = when (this) {
        App -> strings.text(UiText.SettingsGroupApp)
        Content -> strings.text(UiText.SettingsGroupContent)
        Data -> strings.text(UiText.SettingsGroupData)
        None -> null
    }
}
