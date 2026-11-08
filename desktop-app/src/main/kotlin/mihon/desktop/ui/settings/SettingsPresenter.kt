package mihon.desktop.ui.settings

import androidx.compose.runtime.mutableStateOf
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore

/**
 * Single source of truth for the preference values shown in Settings. Panes read [preferences]
 * instead of each keeping a stale copy loaded from disk, and every write goes through
 * [updatePreferences] so all open panes observe the same value.
 */
internal class SettingsPresenter(
    private val preferenceStore: DesktopPreferenceStore,
    private val readerSettingsStore: DesktopReaderSettingsStore,
) {
    internal val store: DesktopPreferenceStore get() = preferenceStore
    val preferences = mutableStateOf(preferenceStore.load())
    val readerSettings = mutableStateOf(readerSettingsStore.load())

    fun updatePreferences(transform: (DesktopPreferences) -> DesktopPreferences): DesktopPreferences {
        val updated = preferenceStore.updatePreferences(transform)
        preferences.value = updated
        return updated
    }

    /** Re-reads the store after a mutation performed through another API (storage path cards). */
    fun reloadPreferences() {
        preferences.value = preferenceStore.load()
    }

    fun saveReaderSettings(value: DesktopReaderSettings) {
        readerSettingsStore.save(value)
        readerSettings.value = value
    }
}
