package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.diagnostics.DiagnosticBundleService
import mihon.desktop.diagnostics.DiagnosticSummary
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.i18n.TraditionalChineseStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.security.APP_LOCK_TIMEOUT_OPTIONS
import mihon.desktop.security.ChangePinResult
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.MIN_PIN_LENGTH
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.TrackerAuthType
import mihon.desktop.ui.common.ContentDensity
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.searchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry
import mihon.desktop.ui.track.TrackerLoginDialog
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
fun SettingsScreen(
    preferenceStore: DesktopPreferenceStore,
    readerSettingsStore: DesktopReaderSettingsStore,
    diagnosticService: DiagnosticBundleService? = null,
    trackerManager: DesktopTrackerManager? = null,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    onOpenCookieManager: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onPreferencesChanged: ((DesktopPreferences) -> Unit)? = null,
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: Path? = null,
    diskCacheDir: Path? = null,
    profileRoot: Path? = null,
    databaseCleaner: mihon.desktop.library.repository.LibraryDatabaseCleaner? = null,
    downloadCategories: List<mihon.desktop.category.DesktopCategory> = emptyList(),
    appLockController: DesktopAppLockController? = null,
    backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler? = null,
    initialSection: SettingsSection? = null,
    contentDensity: ContentDensity = ContentDensity.Standard,
    onContentDensityChange: (ContentDensity) -> Unit = {},
    aboutContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val strings = LocalStrings.current
    val settingsPresenter = remember(preferenceStore, readerSettingsStore) {
        SettingsPresenter(preferenceStore, readerSettingsStore)
    }
    val scheduleCoordinator = remember(backgroundScheduler, backgroundScope) {
        val saved = settingsPresenter.preferences.value
        BackgroundScheduleCoordinator(
            backgroundScheduler,
            backgroundScope,
            saved.libraryUpdateIntervalHours to saved.backupIntervalHours,
            saved.backgroundTasksEnabled,
        )
    }
    val notifyPreferencesChanged: (DesktopPreferences) -> Unit = { updated ->
        onPreferencesChanged?.invoke(updated)
        scheduleCoordinator.observe(updated, restore = { previous ->
            val restored = settingsPresenter.updatePreferences {
                it.copy(
                    libraryUpdateIntervalHours = previous.first,
                    backupIntervalHours = previous.second,
                )
            }
            onPreferencesChanged?.invoke(restored)
        }) { detail ->
            snackbarHostState?.showSnackbar(strings.text(UiText.BackgroundScheduleFailed, detail))
        }
    }
    val securityController = remember(preferenceStore, appLockController) {
        appLockController ?: DesktopAppLockController(preferenceStore)
    }
    var selectedSection by remember(initialSection) { mutableStateOf(initialSection ?: SettingsSection.General) }
    var searchQuery by remember { mutableStateOf("") }
    val searchableEntries = remember(strings) { buildSearchableSettings(strings) }
    val matchingResults = remember(searchQuery, searchableEntries) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            searchableEntries.filter { it.matches(q, strings) }
        }
    }
    Row(modifier = modifier.fillMaxSize().testTag("settings-screen")) {
        // Left side sections navigation
        Surface(
            modifier = Modifier.width(260.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            SettingsSidebar(
                strings = strings,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                matchingResults = matchingResults,
                selectedSection = selectedSection,
                onSectionSelected = {
                    selectedSection = it
                    searchQuery = ""
                },
                onSearchResult = {
                    selectedSection = it.section
                    searchQuery = ""
                },
            )
        }

        // Right side section content
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Box(
                modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth(),
            ) {
                if (searchQuery.isNotBlank()) {
                    SettingsSearchResultsPane(
                        query = searchQuery,
                        results = matchingResults,
                        strings = strings,
                        onSelectEntry = { entry ->
                            selectedSection = entry.section
                            searchQuery = ""
                        },
                    )
                } else {
                    when (selectedSection) {
                        SettingsSection.General -> GeneralSettingsPane(
                            preferenceStore = preferenceStore,
                            presenter = settingsPresenter,
                            onPreferencesChanged = notifyPreferencesChanged,
                            onOpenOnboarding = onOpenOnboarding,
                        )
                        SettingsSection.Security -> SecuritySettingsPane(
                            preferenceStore = preferenceStore,
                            presenter = settingsPresenter,
                            appLockController = securityController,
                            onPreferencesChanged = notifyPreferencesChanged,
                        )
                        SettingsSection.Appearance -> AppearanceSettingsPane(
                            preferenceStore,
                            settingsPresenter,
                            notifyPreferencesChanged,
                            contentDensity,
                            onContentDensityChange,
                        )
                        SettingsSection.Library -> LibrarySettingsPane(
                            preferenceStore,
                            settingsPresenter,
                            updateScheduler,
                            notifyPreferencesChanged,
                        )
                        SettingsSection.Reader -> ReaderSettingsPane(settingsPresenter)
                        SettingsSection.Shortcuts -> ShortcutSettingsPane(
                            remember(preferenceStore) { DesktopKeyBindingStore(preferenceStore) },
                        )
                        SettingsSection.Downloads -> DownloadsSettingsPane(
                            preferenceStore = preferenceStore,
                            presenter = settingsPresenter,
                            onPreferencesChanged = notifyPreferencesChanged,
                            downloadsDir = downloadsDir,
                            categories = downloadCategories,
                        )
                        SettingsSection.Tracking -> TrackingSettingsPane(trackerManager)
                        SettingsSection.Backup -> BackupSettingsPane(
                            preferenceStore = preferenceStore,
                            presenter = settingsPresenter,
                            backupScheduler = backupScheduler,
                            syncScheduler = syncScheduler,
                            onImportBackup = onImportBackup,
                            onExportBackup = onExportBackup,
                            onPreferencesChanged = notifyPreferencesChanged,
                        )
                        SettingsSection.Advanced -> AdvancedSettingsPane(
                            preferenceStore = preferenceStore,
                            presenter = settingsPresenter,
                            backgroundScheduler = backgroundScheduler,
                            scheduleCoordinator = scheduleCoordinator,
                            onPreferencesChanged = notifyPreferencesChanged,
                            diagnosticService = diagnosticService,
                            onOpenCookieManager = onOpenCookieManager,
                            downloadCacheCleaner = downloadCacheCleaner,
                            downloadsDir = downloadsDir,
                            diskCacheDir = diskCacheDir,
                            profileRoot = profileRoot,
                            databaseCleaner = databaseCleaner,
                        )
                        SettingsSection.About -> aboutContent?.invoke() ?: AboutScreen()
                    }
                }
            }
        }
    }
}
