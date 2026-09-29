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
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.searchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry
import mihon.desktop.ui.track.TrackerLoginDialog
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import java.nio.file.Path
import javax.swing.JFileChooser

enum class SettingsSection(val label: String) {
    General("General"),
    Security("Security"),
    Appearance("Appearance"),
    Library("Library"),
    Reader("Reader"),
    Downloads("Downloads"),
    Tracking("Tracking"),
    Backup("Backup & Restore"),
    Advanced("Advanced & Diagnostics"),
    ;

    val icon: ImageVector
        get() = when (this) {
            General -> Icons.Rounded.Tune
            Security -> Icons.Rounded.Lock
            Appearance -> Icons.Rounded.Palette
            Library -> Icons.Rounded.CollectionsBookmark
            Reader -> Icons.AutoMirrored.Rounded.ChromeReaderMode
            Downloads -> Icons.Rounded.Download
            Tracking -> Icons.Rounded.SyncAlt
            Backup -> Icons.Rounded.Backup
            Advanced -> Icons.Rounded.Code
        }

    fun localized(strings: DesktopStrings): String = when (this) {
        General -> strings.settingsSectionGeneral
        Security -> strings.text(UiText.Security)
        Appearance -> strings.settingsSectionAppearance
        Library -> strings.libraryTitle
        Reader -> strings.settingsSectionReader
        Downloads -> strings.settingsSectionDownloads
        Tracking -> strings.settingsSectionTracking
        Backup -> strings.settingsSectionBackup
        Advanced -> strings.settingsSectionAdvanced
    }
}

@Composable
fun SettingsScreen(
    preferenceStore: DesktopPreferenceStore,
    readerSettingsStore: DesktopReaderSettingsStore,
    diagnosticService: DiagnosticBundleService? = null,
    trackerManager: DesktopTrackerManager? = null,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    syncServerManager: mihon.desktop.sync.DesktopSyncServerManager? = null,
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
    modifier: Modifier = Modifier,
) {
    val backgroundScope = rememberCoroutineScope()
    var previousSchedule by remember {
        val saved = preferenceStore.load()
        mutableStateOf(saved.libraryUpdateIntervalHours to saved.backupIntervalHours)
    }
    val notifyPreferencesChanged: (DesktopPreferences) -> Unit = { updated ->
        onPreferencesChanged?.invoke(updated)
        val schedule = updated.libraryUpdateIntervalHours to updated.backupIntervalHours
        if (updated.backgroundTasksEnabled && schedule != previousSchedule) {
            backgroundScope.launch(Dispatchers.IO) {
                runCatching { backgroundScheduler?.reconcile(true, schedule.first, schedule.second) }
            }
        }
        previousSchedule = schedule
    }
    val strings = LocalStrings.current
    val securityController = remember(preferenceStore, appLockController) {
        appLockController ?: DesktopAppLockController(preferenceStore)
    }
    var selectedSection by remember { mutableStateOf(SettingsSection.General) }
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
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val searchFocusRequester = LocalSearchFocusRequester.current
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = strings.settingsSearchPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("settings-clear-search").size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = strings.text(UiText.ClearSearch),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .searchFocusRequester(searchFocusRequester)
                        .trackTextInputFocus()
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                    true
                                } else {
                                    false
                                }
                            } else if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                if (matchingResults.isNotEmpty()) {
                                    selectedSection = matchingResults.first().section
                                    searchQuery = ""
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        .testTag("settings-search"),
                )
                SettingsSection.entries.forEach { section ->
                    val isSelected = searchQuery.isBlank() && section == selectedSection
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedSection = section
                                searchQuery = ""
                            }
                            .testTag("settings-section-${section.name}"),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = section.localized(strings),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        // Right side section content
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
        ) {
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
                            onPreferencesChanged = notifyPreferencesChanged,
                            onOpenOnboarding = onOpenOnboarding,
                        )
                        SettingsSection.Security -> SecuritySettingsPane(
                            preferenceStore = preferenceStore,
                            appLockController = securityController,
                            onPreferencesChanged = notifyPreferencesChanged,
                        )
                        SettingsSection.Appearance -> AppearanceSettingsPane(preferenceStore, notifyPreferencesChanged)
                        SettingsSection.Library -> LibrarySettingsPane(
                            preferenceStore,
                            updateScheduler,
                            notifyPreferencesChanged,
                        )
                        SettingsSection.Reader -> ReaderSettingsPane(readerSettingsStore)
                        SettingsSection.Downloads -> DownloadsSettingsPane(
                            preferenceStore = preferenceStore,
                            onPreferencesChanged = notifyPreferencesChanged,
                            downloadsDir = downloadsDir,
                            categories = downloadCategories,
                        )
                        SettingsSection.Tracking -> TrackingSettingsPane(trackerManager)
                        SettingsSection.Backup -> BackupSettingsPane(
                            preferenceStore = preferenceStore,
                            backupScheduler = backupScheduler,
                            syncScheduler = syncScheduler,
                            syncServerManager = syncServerManager,
                            onImportBackup = onImportBackup,
                            onExportBackup = onExportBackup,
                            onPreferencesChanged = notifyPreferencesChanged,
                        )
                        SettingsSection.Advanced -> AdvancedSettingsPane(
                            preferenceStore = preferenceStore,
                            backgroundScheduler = backgroundScheduler,
                            onPreferencesChanged = notifyPreferencesChanged,
                            diagnosticService = diagnosticService,
                            onOpenCookieManager = onOpenCookieManager,
                            downloadCacheCleaner = downloadCacheCleaner,
                            downloadsDir = downloadsDir,
                            diskCacheDir = diskCacheDir,
                            profileRoot = profileRoot,
                            databaseCleaner = databaseCleaner,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResultsPane(
    query: String,
    results: List<SearchableSettingEntry>,
    strings: DesktopStrings,
    onSelectEntry: (SearchableSettingEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings-search-pane"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = strings.settingsSearchResultsCount(results.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("settings-search-results-count"),
            )
        }

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .testTag("settings-search-empty"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = strings.settingsSearchNoResults,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings-search-results"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.id }) { entry ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEntry(entry) }
                            .testTag("settings-search-result-${entry.id}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = entry.section.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = entry.section.localized(strings),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!entry.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SearchableSettingEntry(
    val id: String,
    val section: SettingsSection,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
) {
    fun matches(query: String, strings: DesktopStrings): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val localizedSection = section.localized(strings).lowercase()
        val sectionName = section.name.lowercase()
        val matchesTitle = title.lowercase().contains(q)
        val matchesSubtitle = subtitle?.lowercase()?.contains(q) == true
        val matchesSection = localizedSection.contains(q) || sectionName.contains(q)
        val matchesKeywords = keywords.any { it.lowercase().contains(q) }
        return matchesTitle || matchesSubtitle || matchesSection || matchesKeywords
    }
}

private fun buildSearchableSettings(strings: DesktopStrings): List<SearchableSettingEntry> {
    val backgroundTasksTitle = when (strings) {
        SimplifiedChineseStrings -> "关闭应用后继续更新与备份"
        TraditionalChineseStrings -> "關閉應用程式後繼續更新與備份"
        else -> "Run updates and backups while the app is closed"
    }
    return listOf(
        // General
        SearchableSettingEntry(
            id = "general-language",
            section = SettingsSection.General,
            title = strings.settingsLanguageTitle,
            subtitle = listOf(
                strings.settingsLanguageSystem,
                strings.settingsLanguageSimplifiedChinese,
                strings.settingsLanguageTraditionalChinese,
                strings.settingsLanguageEnglish,
            ).joinToString(),
            keywords = listOf("language", "locale", "chinese", "english", "语言", "語言", "中文", "英文", "繁体", "简体"),
        ),
        SearchableSettingEntry(
            id = "general-app-info",
            section = SettingsSection.General,
            title = strings.settingsAppInfoTitle,
            subtitle = "mihondesk, version, platform",
            keywords = listOf("version", "platform", "about", "info", "build", "版本", "平台", "关于", "應用資訊"),
        ),
        SearchableSettingEntry(
            id = "general-incognito",
            section = SettingsSection.General,
            title = strings.incognitoTitle,
            subtitle = strings.incognitoDescription,
            keywords = listOf("incognito", "private", "history", "pause", "无痕", "私密", "隐私", "隱私", "無痕模式"),
        ),
        SearchableSettingEntry(
            id = "general-run-background",
            section = SettingsSection.General,
            title = strings.runInBackgroundTitle,
            subtitle = strings.runInBackgroundSummary,
            keywords = listOf("background", "close", "minimize", "tray", "后台", "關閉", "後台", "最小化", "托盘"),
        ),
        SearchableSettingEntry(
            id = "general-nsfw",
            section = SettingsSection.General,
            title = strings.settingsShowNsfw,
            subtitle = strings.settingsShowNsfwDesc,
            keywords = listOf("nsfw", "18+", "adult", "sources", "filter", "成人", "色情", "限制", "图源", "圖源"),
        ),
        SearchableSettingEntry(
            id = "general-reset-hidden-sources",
            section = SettingsSection.General,
            title = strings.settingsResetHiddenSources,
            subtitle = strings.settingsResetHiddenSourcesDesc,
            keywords = listOf("hidden", "sources", "reset", "unhide", "隐藏", "重置", "圖源", "隱藏"),
        ),
        SearchableSettingEntry(
            id = "general-onboarding",
            section = SettingsSection.General,
            title = strings.settingsReshowOnboarding,
            subtitle = strings.settingsReshowOnboardingDesc,
            keywords = listOf("onboarding", "guide", "setup", "wizard", "welcome", "引导", "嚮導", "新手", "重现"),
        ),
        SearchableSettingEntry(
            id = "general-check-updates",
            section = SettingsSection.General,
            title = strings.text(UiText.AppUpdateTitle),
            subtitle = null,
            keywords = listOf("update", "upgrade", "version", "check", "更新", "檢查更新", "升級"),
        ),

        // Security
        SearchableSettingEntry(
            id = "security-app-lock",
            section = SettingsSection.Security,
            title = strings.text(UiText.AppLock),
            subtitle = strings.text(UiText.LockEnabledHint),
            keywords = listOf(
                "app lock",
                "pin",
                "password",
                "security",
                "protect",
                "应用锁",
                "應用鎖",
                "密碼",
                "密码",
                "锁定",
                "安全",
            ),
        ),
        SearchableSettingEntry(
            id = "security-lock-on-startup",
            section = SettingsSection.Security,
            title = strings.text(UiText.LockOnStartup),
            subtitle = strings.text(UiText.LockOnStartupHint),
            keywords = listOf("startup", "boot", "launch", "lock", "启动", "啟動", "锁定"),
        ),
        SearchableSettingEntry(
            id = "security-idle-timeout",
            section = SettingsSection.Security,
            title = strings.text(UiText.AutoLock),
            subtitle = strings.text(UiText.AutoLockHint),
            keywords = listOf("idle", "timeout", "minutes", "lock", "闲置", "閒置", "超时", "逾時", "自动锁定"),
        ),
        SearchableSettingEntry(
            id = "security-change-pin",
            section = SettingsSection.Security,
            title = strings.text(UiText.ChangePin),
            subtitle = strings.text(UiText.PinHint, MIN_PIN_LENGTH),
            keywords = listOf("change pin", "modify", "password", "修改", "密码", "PIN"),
        ),
        SearchableSettingEntry(
            id = "security-lock-now",
            section = SettingsSection.Security,
            title = strings.text(UiText.LockNow),
            subtitle = strings.text(UiText.LockNowHint),
            keywords = listOf("lock now", "manual lock", "立即锁定", "馬上鎖定"),
        ),

        // Appearance
        SearchableSettingEntry(
            id = "appearance-theme-mode",
            section = SettingsSection.Appearance,
            title = strings.settingsThemeModeTitle,
            subtitle = "${strings.settingsThemeSystem}, ${strings.settingsThemeLight}, ${strings.settingsThemeDark}",
            keywords = listOf(
                "theme",
                "mode",
                "dark",
                "light",
                "system",
                "dark mode",
                "主题",
                "模式",
                "深色",
                "浅色",
                "暗色",
                "夜间",
                "主題",
            ),
        ),
        SearchableSettingEntry(
            id = "appearance-amoled",
            section = SettingsSection.Appearance,
            title = strings.settingsThemeAmoledTitle,
            subtitle = strings.settingsThemeAmoledSubtitle,
            keywords = listOf("amoled", "pure black", "oled", "black", "纯黑", "純黑", "极黑", "省电"),
        ),
        SearchableSettingEntry(
            id = "appearance-color-scheme",
            section = SettingsSection.Appearance,
            title = strings.settingsAppThemeTitle,
            subtitle = null,
            keywords = listOf("color", "scheme", "palette", "accent", "lavender", "sakura", "色彩", "配色", "主题色", "顏色"),
        ),

        // Library
        SearchableSettingEntry(
            id = "library-update-interval",
            section = SettingsSection.Library,
            title = strings.libraryUpdateInterval,
            subtitle = strings.libraryUpdating,
            keywords = listOf(
                "library update",
                "interval",
                "schedule",
                "frequency",
                "书库更新",
                "書庫更新",
                "自动更新",
                "频率",
                "週期",
            ),
        ),
        SearchableSettingEntry(
            id = "library-skip-completed",
            section = SettingsSection.Library,
            title = strings.libraryUpdateSkipCompleted,
            subtitle = null,
            keywords = listOf("skip", "completed", "finished", "完结", "已完结", "跳过", "略過"),
        ),
        SearchableSettingEntry(
            id = "library-skip-unread",
            section = SettingsSection.Library,
            title = strings.libraryUpdateSkipUnread,
            subtitle = null,
            keywords = listOf("skip", "unread", "跳过未读", "未讀"),
        ),
        SearchableSettingEntry(
            id = "library-skip-started",
            section = SettingsSection.Library,
            title = strings.text(UiText.SkipNotStarted),
            subtitle = null,
            keywords = listOf("skip", "started", "not started", "未开始", "未開始"),
        ),
        SearchableSettingEntry(
            id = "library-skip-outside-release-period",
            section = SettingsSection.Library,
            title = strings.libraryUpdateSkipOutsideReleasePeriod,
            keywords = listOf("release period", "update cycle", "smart update", "更新周期", "更新週期"),
        ),
        SearchableSettingEntry(
            id = "library-categories",
            section = SettingsSection.Library,
            title = strings.text(UiText.IncludeCategoryIds),
            subtitle = strings.text(UiText.ExcludeCategoryIds),
            keywords = listOf("category", "categories", "include", "exclude", "分类", "分類", "包含", "排除"),
        ),
        SearchableSettingEntry(
            id = "library-auto-download",
            section = SettingsSection.Library,
            title = strings.libraryAutoDownloadNew,
            subtitle = null,
            keywords = listOf("auto download", "new chapters", "自动下载", "自動下載", "新章节", "新章節"),
        ),
        SearchableSettingEntry(
            id = "library-update-only-ac",
            section = SettingsSection.Library,
            title = strings.libraryUpdateOnlyOnAcPower,
            subtitle = null,
            keywords = listOf("charging", "power", "battery", "ac", "供电", "充电", "电源", "電池"),
        ),
        SearchableSettingEntry(
            id = "library-notifications",
            section = SettingsSection.Library,
            title = strings.notificationsDesktopEnabled,
            subtitle = strings.text(UiText.HideNotificationContent),
            keywords = listOf("notification", "desktop", "alert", "toast", "spoiler", "通知", "桌面通知", "提醒", "防剧透"),
        ),

        // Reader
        SearchableSettingEntry(
            id = "reader-reading-mode",
            section = SettingsSection.Reader,
            title = strings.settingsDefaultReadingMode,
            subtitle = "${strings.readerModeSingleLtr}, ${strings.readerModeSingleRtl}, ${strings.readerModeWebtoon}",
            keywords = listOf(
                "reading mode",
                "webtoon",
                "vertical",
                "horizontal",
                "ltr",
                "rtl",
                "阅读模式",
                "閱讀模式",
                "条漫",
                "條漫",
                "单页",
            ),
        ),
        SearchableSettingEntry(
            id = "reader-scale-type",
            section = SettingsSection.Reader,
            title = strings.settingsDefaultScaleMode,
            subtitle = listOf(
                strings.readerScaleFitWidth,
                strings.readerScaleFitHeight,
                strings.readerScaleOriginal,
            ).joinToString(),
            keywords = listOf("scale", "fit", "stretch", "original", "缩放", "縮放", "适应宽度", "拉伸"),
        ),
        SearchableSettingEntry(
            id = "reader-color-filter",
            section = SettingsSection.Reader,
            title = strings.readerColorFilter,
            subtitle = listOf(
                strings.readerFilterInvert,
                strings.readerFilterGrayscale,
                strings.readerFilterSepia,
                strings.readerFilterNight,
                strings.readerFilterCustom,
            ).joinToString(),
            keywords = listOf(
                "color filter",
                "filter",
                "grayscale",
                "sepia",
                "night",
                "custom",
                "invert",
                "滤镜",
                "濾鏡",
                "反转",
                "灰阶",
                "黑白",
                "夜间",
            ),
        ),
        SearchableSettingEntry(
            id = "reader-custom-filter",
            section = SettingsSection.Reader,
            title = strings.readerFilterCustom,
            subtitle = listOf(
                strings.readerCustomHue,
                strings.readerCustomBrightness,
                strings.readerCustomContrast,
                strings.readerDimming,
            ).joinToString(),
            keywords = listOf("hue", "brightness", "contrast", "dimming", "色相", "亮度", "对比度", "变暗", "屏幕变暗", "自订", "自定义"),
        ),
        SearchableSettingEntry(
            id = "reader-background-color",
            section = SettingsSection.Reader,
            title = strings.readerBackgroundColor,
            subtitle = listOf(
                strings.readerBgDarkGray,
                strings.readerBgBlack,
                strings.readerBgWhite,
                strings.readerBgWarmCream,
            ).joinToString(),
            keywords = listOf("background", "bg color", "canvas", "black", "white", "背景", "背景色", "纯黑", "暖白", "深灰"),
        ),
        SearchableSettingEntry(
            id = "reader-page-transitions",
            section = SettingsSection.Reader,
            title = strings.readerPageTransitions,
            subtitle = mihon.desktop.reader.ReaderPageTransition.entries.joinToString {
                strings.readerPageTransitionLabel(it)
            },
            keywords = listOf("transition", "animation", "fade", "slide", "flip", "过渡", "過渡", "动画", "淡入淡出", "滑动"),
        ),
        SearchableSettingEntry(
            id = "reader-dual-page-split",
            section = SettingsSection.Reader,
            title = strings.readerDualPageSplit,
            subtitle = strings.readerDualPageRotateToFit,
            keywords = listOf(
                "dual page",
                "split",
                "wide page",
                "spread",
                "rotate",
                "跨页",
                "跨頁",
                "双页",
                "雙頁",
                "分割",
                "拆分",
                "旋转",
            ),
        ),
        SearchableSettingEntry(
            id = "reader-double-spread",
            section = SettingsSection.Reader,
            title = strings.settingsDoubleSpread,
            subtitle = null,
            keywords = listOf("spread", "double spread", "dual", "双页", "雙頁"),
        ),
        SearchableSettingEntry(
            id = "reader-crop-borders",
            section = SettingsSection.Reader,
            title = strings.readerCropBorders,
            subtitle = strings.readerCropBordersWebtoon,
            keywords = listOf("crop", "borders", "trim", "margins", "white space", "裁剪", "切边", "白边", "裁邊", "留白"),
        ),
        SearchableSettingEntry(
            id = "reader-webtoon-layout",
            section = SettingsSection.Reader,
            title = strings.readerWebtoonMaxWidth,
            subtitle = strings.readerWebtoonSidePadding,
            keywords = listOf("webtoon width", "max width", "side padding", "margin", "条漫宽度", "條漫寬度", "侧边距", "邊距"),
        ),
        SearchableSettingEntry(
            id = "reader-keep-screen-on",
            section = SettingsSection.Reader,
            title = strings.readerKeepScreenOn,
            subtitle = null,
            keywords = listOf("keep screen on", "sleep", "awake", "display", "常亮", "屏幕常亮", "螢幕常亮", "睡眠", "熄屏"),
        ),
        SearchableSettingEntry(
            id = "reader-page-flash",
            section = SettingsSection.Reader,
            title = strings.readerPageFlash,
            subtitle = null,
            keywords = listOf("flash", "e-ink", "page flash", "闪烁", "閃爍", "水墨屏"),
        ),
        SearchableSettingEntry(
            id = "reader-webtoon-zoom",
            section = SettingsSection.Reader,
            title = strings.readerWebtoonPreventDownsizing,
            subtitle = strings.readerWebtoonDoubleTapZoom,
            keywords = listOf("downsizing", "double tap", "zoom", "webtoon zoom", "双击缩放", "禁用缩小"),
        ),

        // Downloads
        SearchableSettingEntry(
            id = "downloads-location",
            section = SettingsSection.Downloads,
            title = strings.settingsDownloadLocation,
            subtitle = strings.settingsDefaultStorageFolder,
            keywords = listOf(
                "download location",
                "storage path",
                "folder",
                "directory",
                "下载位置",
                "下載目錄",
                "保存路径",
                "存储路径",
            ),
        ),
        SearchableSettingEntry(
            id = "downloads-parallel",
            section = SettingsSection.Downloads,
            title = strings.settingsParallelDownloads,
            subtitle = strings.text(UiText.ParallelPages),
            keywords = listOf("parallel", "concurrent", "threads", "simultaneous", "并发", "並發", "同时下载", "多线程"),
        ),
        SearchableSettingEntry(
            id = "downloads-ahead",
            section = SettingsSection.Downloads,
            title = strings.settingsDownloadAheadTitle,
            subtitle = null,
            keywords = listOf("download ahead", "preload", "buffer", "cache", "预下载", "預先下載", "自动预载"),
        ),
        SearchableSettingEntry(
            id = "downloads-delete-read",
            section = SettingsSection.Downloads,
            title = strings.settingsDeleteReadChaptersTitle,
            subtitle = null,
            keywords = listOf("delete read", "auto delete", "remove", "clean", "已读删除", "已讀刪除", "自动清理", "阅读后删除"),
        ),
        SearchableSettingEntry(
            id = "downloads-save-as-cbz",
            section = SettingsSection.Downloads,
            title = strings.settingsSaveChapterAsCbzTitle,
            subtitle = strings.settingsSaveChapterAsCbzDesc,
            keywords = listOf(
                "cbz",
                "zip",
                "archive",
                "compressed",
                "folder",
                "单文件",
                "壓縮包",
                "打包",
                "資料夾",
            ),
        ),
        SearchableSettingEntry(
            id = "downloads-split-tall-images",
            section = SettingsSection.Downloads,
            title = strings.settingsSplitTallImagesTitle,
            subtitle = strings.settingsSplitTallImagesDesc,
            keywords = listOf(
                "split",
                "tall",
                "long image",
                "webtoon",
                "strip",
                "长图",
                "長圖",
                "拆分",
                "分割",
            ),
        ),
        SearchableSettingEntry(
            id = "downloads-auto-download-new",
            section = SettingsSection.Downloads,
            title = strings.libraryAutoDownloadNew,
            subtitle = null,
            keywords = listOf("auto download", "new chapters", "自动下载新章节", "下載新章節"),
        ),
        SearchableSettingEntry(
            id = "downloads-unread-only",
            section = SettingsSection.Downloads,
            title = strings.downloadNewUnreadOnly,
            keywords = listOf("unread only", "new chapters", "仅未读", "僅未讀"),
        ),
        SearchableSettingEntry(
            id = "downloads-categories",
            section = SettingsSection.Downloads,
            title = strings.downloadNewCategories,
            keywords = listOf("download categories", "include", "exclude", "分类选择", "分類選擇"),
        ),

        // Tracking
        SearchableSettingEntry(
            id = "tracking-services",
            section = SettingsSection.Tracking,
            title = strings.settingsTrackingTitle,
            subtitle = strings.settingsTrackingDescription,
            keywords = listOf(
                "tracking",
                "tracker",
                "anilist",
                "mal",
                "myanimelist",
                "bangumi",
                "kitsu",
                "shikimori",
                "mangaupdates",
                "sync progress",
                "跟踪",
                "追番",
                "进度同步",
                "追蹤",
            ),
        ),

        // Backup
        SearchableSettingEntry(
            id = "backup-exchange",
            section = SettingsSection.Backup,
            title = strings.settingsBackupTitle,
            subtitle = strings.settingsBackupDescription,
            keywords = listOf(
                "backup",
                "restore",
                "export",
                "import",
                "tachiyomi",
                "suwayomi",
                "json",
                "proto",
                "备份",
                "備份",
                "还原",
                "匯出",
                "匯入",
                "导入",
                "导出",
            ),
        ),
        SearchableSettingEntry(
            id = "backup-schedule",
            section = SettingsSection.Backup,
            title = strings.backupAutoTitle,
            subtitle = "${strings.backupInterval}, ${strings.backupRetention}",
            keywords = listOf("auto backup", "schedule", "retention", "interval", "自动备份", "自動備份", "备份保留", "定时备份"),
        ),
        SearchableSettingEntry(
            id = "backup-location",
            section = SettingsSection.Backup,
            title = strings.backupLocation,
            subtitle = null,
            keywords = listOf("backup location", "backup folder", "path", "directory", "备份目录", "備份目錄", "存储路径"),
        ),
        SearchableSettingEntry(
            id = "backup-sync-server",
            section = SettingsSection.Backup,
            title = strings.syncCardTitle,
            subtitle = strings.syncCardDescription,
            keywords = listOf("sync", "server", "cloud", "multi-device", "pairing", "同步", "同步服务器", "配对", "云同步"),
        ),

        // Advanced
        SearchableSettingEntry(
            id = "advanced-background-tasks",
            section = SettingsSection.Advanced,
            title = backgroundTasksTitle,
            subtitle = null,
            keywords = listOf("background tasks", "task scheduler", "windows", "后台任务", "工作排程", "计划任务"),
        ),
        SearchableSettingEntry(
            id = "advanced-network-proxy",
            section = SettingsSection.Advanced,
            title = strings.networkSettingsTitle,
            subtitle = strings.networkSettingsDescription,
            keywords = listOf("network", "proxy", "http", "socks", "port", "host", "timeout", "网络", "代理", "網絡", "超時"),
        ),
        SearchableSettingEntry(
            id = "advanced-storage-cleaner",
            section = SettingsSection.Advanced,
            title = strings.storageCleanerTitle,
            subtitle = strings.storageCleanerDescription,
            keywords = listOf("cleaner", "cache", "disk", "storage", "image cache", "清理", "缓存", "快取", "空间"),
        ),
        SearchableSettingEntry(
            id = "advanced-cookie-manager",
            section = SettingsSection.Advanced,
            title = strings.cookieManagerTitle,
            subtitle = strings.cookieManagerDescription,
            keywords = listOf(
                "cookie",
                "cookies",
                "webview",
                "cloudflare",
                "session",
                "login",
                "cookie 管理器",
                "浏览器",
                "验证",
            ),
        ),
        SearchableSettingEntry(
            id = "advanced-diagnostics",
            section = SettingsSection.Advanced,
            title = strings.settingsDiagnosticsTitle,
            subtitle = strings.settingsRunIntegrityCheck,
            keywords = listOf(
                "database",
                "integrity",
                "vacuum",
                "sqlite",
                "diagnostics",
                "bundle",
                "export",
                "logs",
                "数据库",
                "完整性",
                "诊断",
                "日志",
            ),
        ),
    )
}

@Composable
private fun GeneralSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    onOpenOnboarding: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionGeneral,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.settingsLanguageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        AppLanguage.System to strings.settingsLanguageSystem,
                        AppLanguage.SimplifiedChinese to strings.settingsLanguageSimplifiedChinese,
                        AppLanguage.TraditionalChinese to strings.settingsLanguageTraditionalChinese,
                        AppLanguage.English to strings.settingsLanguageEnglish,
                    ).forEach { (lang, label) ->
                        val isSelected = preferences.language == lang
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferenceStore.updatePreferences { it.copy(language = lang) }
                                    preferences = updated

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Incognito Mode Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.incognitoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.incognitoDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.incognitoMode,
                    onCheckedChange = { isChecked ->
                        val updated = preferenceStore.updatePreferences { it.copy(incognitoMode = isChecked) }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("incognito-switch"),
                )
            }
        }

        // Run In Background on Close Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.runInBackgroundTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.runInBackgroundSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.runInBackgroundOnClose,
                    onCheckedChange = { isChecked ->
                        val updated = preferenceStore.updatePreferences {
                            it.copy(runInBackgroundOnClose = isChecked)
                        }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("run-in-background-switch"),
                )
            }
        }

        // Show NSFW Sources Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsShowNsfw,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsShowNsfwDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.showNsfwSources,
                    onCheckedChange = { isChecked ->
                        val updated = preferenceStore.updatePreferences { it.copy(showNsfwSources = isChecked) }
                        preferences = updated
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("settings-show-nsfw-switch"),
                )
            }
        }

        // Reset Hidden Sources Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsResetHiddenSources,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsResetHiddenSourcesDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val updated = preferenceStore.updatePreferences { it.copy(hiddenSourceIds = emptySet()) }
                        preferences = updated
                        onPreferencesChanged?.invoke(updated)
                        scope.launch {
                            snackbarHostState?.showSnackbar(strings.settingsResetHiddenSourcesSuccess)
                        }
                    },
                    modifier = Modifier.testTag("settings-reset-hidden-sources"),
                ) {
                    Text(strings.settingsResetHiddenSources)
                }
            }
        }

        // Re-show Onboarding Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsReshowOnboarding,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsReshowOnboardingDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenOnboarding,
                    modifier = Modifier.testTag("settings-reshow-onboarding-btn"),
                ) {
                    Text(strings.settingsReshowOnboarding)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    strings.settingsAppInfoTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.settingsVersionLabel(mihon.desktop.updates.DesktopAppUpdateService.CURRENT_VERSION),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    strings.settingsPlatformLabel("Windows x64"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SecuritySettingsPane(
    preferenceStore: DesktopPreferenceStore,
    appLockController: DesktopAppLockController,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
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
        preferences = preferenceStore.load()
        appLockController.refresh()
        onPreferencesChanged?.invoke(preferences)
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.text(UiText.ChangePin), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = it
                            pinError = null
                        },
                        label = { Text(strings.text(UiText.CurrentPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-current-pin-field"),
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it
                            pinError = null
                        },
                        label = { Text(strings.text(UiText.NewPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-new-pin-field"),
                    )
                    OutlinedTextField(
                        value = newPinConfirm,
                        onValueChange = {
                            newPinConfirm = it
                            pinError = null
                        },
                        label = { Text(strings.text(UiText.ConfirmNewPin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("security-new-pin-confirm-field"),
                    )
                    pinError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("security-change-pin-error"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                pinError = null
                                pinMessage = null
                                when {
                                    currentPin.length < MIN_PIN_LENGTH -> {
                                        pinError = strings.text(UiText.EnterCurrentPin)
                                    }
                                    newPin.length < MIN_PIN_LENGTH -> {
                                        pinError = strings.text(UiText.NewPinMinimum, MIN_PIN_LENGTH)
                                    }
                                    newPin != newPinConfirm -> {
                                        pinError = strings.text(UiText.NewPinsMismatch)
                                    }
                                    else -> {
                                        when (appLockController.changePin(currentPin, newPin)) {
                                            ChangePinResult.Success -> {
                                                currentPin = ""
                                                newPin = ""
                                                newPinConfirm = ""
                                                refreshPreferences()
                                                pinMessage = strings.text(UiText.PinChanged)
                                            }
                                            ChangePinResult.WrongCurrentPin -> {
                                                pinError = strings.text(UiText.WrongCurrentPin)
                                            }
                                            ChangePinResult.InvalidNewPin -> {
                                                pinError =
                                                    strings.text(UiText.NewPinMinimum, MIN_PIN_LENGTH)
                                            }
                                            ChangePinResult.NotConfigured -> {
                                                pinError = strings.text(UiText.LockNotConfigured)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("security-change-pin-button"),
                        ) {
                            Text(strings.text(UiText.ChangePin))
                        }
                        OutlinedButton(
                            onClick = {
                                pinError = null
                                appLockController.disableLock()
                                refreshPreferences()
                                pinMessage = strings.text(UiText.LockDisabled)
                            },
                            modifier = Modifier.testTag("security-disable-button"),
                        ) {
                            Text(strings.text(UiText.DisableLock))
                        }
                    }
                }
            }

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

@Composable
private fun AppearanceSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionAppearance,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // 1. Theme Mode (System / Light / Dark)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsThemeModeTitle, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        ThemeMode.System to strings.settingsThemeSystem,
                        ThemeMode.Light to strings.settingsThemeLight,
                        ThemeMode.Dark to strings.settingsThemeDark,
                    ).forEach { (mode, label) ->
                        val isSelected = preferences.themeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferenceStore.updatePreferences { it.copy(themeMode = mode) }
                                    preferences = updated

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // 2. AMOLED Theme Switch
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
                    Text(strings.settingsThemeAmoledTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsThemeAmoledSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.themeDarkAmoled,
                    onCheckedChange = { isChecked ->
                        val updated = preferenceStore.updatePreferences { it.copy(themeDarkAmoled = isChecked) }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("amoled-switch"),
                )
            }
        }

        // 3. Theme Palette Grid Selector
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(strings.settingsAppThemeTitle, fontWeight = FontWeight.Bold)

                val themes = DesktopAppTheme.entries
                val isDark = when (preferences.themeMode) {
                    ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowThemes.forEach { theme ->
                                val isSelected = preferences.appTheme == theme
                                val scheme = ThemeRegistry.getColorScheme(theme)
                                val previewColors = scheme.getColorScheme(
                                    isDark = isDark,
                                    isAmoled = preferences.themeDarkAmoled,
                                )

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val updated = preferenceStore.updatePreferences {
                                                it.copy(appTheme = theme)
                                            }
                                            preferences = updated

                                            onPreferencesChanged?.invoke(updated)
                                        }
                                        .testTag("theme-palette-${theme.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    },
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = strings.appThemeName(theme),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Primary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.primary),
                                            )
                                            // Secondary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.secondary),
                                            )
                                            // Tertiary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.tertiary),
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowThemes.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsPane(readerSettingsStore: DesktopReaderSettingsStore) {
    val strings = LocalStrings.current
    var settings by remember { mutableStateOf(readerSettingsStore.load()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionReader,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDefaultReadingMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReadingMode.SINGLE_LTR to strings.readerModeSingleLtr,
                        ReadingMode.SINGLE_RTL to strings.readerModeSingleRtl,
                        ReadingMode.WEBTOON to strings.readerModeWebtoon,
                    ).forEach { (mode, label) ->
                        val isSelected = settings.mode == mode
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(mode = mode)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsDefaultScaleMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScaleMode.FIT_WIDTH to strings.readerScaleFitWidth,
                        ScaleMode.FIT_HEIGHT to strings.readerScaleFitHeight,
                        ScaleMode.ORIGINAL to strings.readerScaleOriginal,
                    ).forEach { (scale, label) ->
                        val isSelected = settings.scaleMode == scale
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(scaleMode = scale)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(strings.settingsDoubleSpread, fontWeight = FontWeight.Bold)
                        Text(
                            strings.settingsDoubleSpread,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.coverOffset,
                        onCheckedChange = { checked ->
                            settings = settings.copy(coverOffset = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsMouseWheelBehavior, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderWheelBehavior.PAGE_NAVIGATION to strings.settingsWheelFlipPage,
                        ReaderWheelBehavior.SCROLL to strings.settingsWheelScrollPage,
                    ).forEach { (behavior, label) ->
                        val isSelected = settings.wheelBehavior == behavior
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(wheelBehavior = behavior)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerColorFilter, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderColorFilter.NONE to strings.readerFilterNone,
                        ReaderColorFilter.INVERT to strings.readerFilterInvert,
                        ReaderColorFilter.GRAYSCALE to strings.readerFilterGrayscale,
                        ReaderColorFilter.SEPIA to strings.readerFilterSepia,
                        ReaderColorFilter.NIGHT to strings.readerFilterNight,
                        ReaderColorFilter.CUSTOM to strings.readerFilterCustom,
                    ).forEach { (filter, label) ->
                        val isSelected = settings.colorFilter == filter
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(colorFilter = filter)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerBackgroundColor, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderBackgroundColor.DARK_GRAY to strings.readerBgDarkGray,
                        ReaderBackgroundColor.BLACK to strings.readerBgBlack,
                        ReaderBackgroundColor.WHITE to strings.readerBgWhite,
                        ReaderBackgroundColor.WARM_CREAM to strings.readerBgWarmCream,
                    ).forEach { (bg, label) ->
                        val isSelected = settings.backgroundColor == bg
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(backgroundColor = bg)
                                    readerSettingsStore.save(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBorders, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBorders,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBorders = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBordersWebtoon, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBordersWebtoon,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBordersWebtoon = checked)
                            readerSettingsStore.save(settings)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerKeepScreenOn, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.keepScreenOn,
                        onCheckedChange = { checked ->
                            settings = settings.copy(keepScreenOn = checked)
                            readerSettingsStore.save(settings)
                        },
                        modifier = Modifier.testTag("reader-keep-screen-on"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerPageFlash, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.pageFlash,
                        onCheckedChange = { checked ->
                            settings = settings.copy(pageFlash = checked)
                            readerSettingsStore.save(settings)
                        },
                        modifier = Modifier.testTag("reader-page-flash"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerWebtoonPreventDownsizing, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.webtoonPreventDownsizing,
                        onCheckedChange = { checked ->
                            settings = settings.copy(webtoonPreventDownsizing = checked)
                            readerSettingsStore.save(settings)
                        },
                        modifier = Modifier.testTag("reader-webtoon-prevent-downsizing"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerWebtoonDoubleTapZoom, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.webtoonDoubleTapZoom,
                        onCheckedChange = { checked ->
                            settings = settings.copy(webtoonDoubleTapZoom = checked)
                            readerSettingsStore.save(settings)
                        },
                        modifier = Modifier.testTag("reader-webtoon-double-tap-zoom"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    downloadsDir: Path?,
    categories: List<mihon.desktop.category.DesktopCategory> = emptyList(),
) {
    val strings = LocalStrings.current
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
    var pathDraft by remember { mutableStateOf(preferences.downloadStoragePath) }
    var pathError by remember { mutableStateOf<UiText?>(null) }
    var pathSaved by remember { mutableStateOf(false) }
    var isSavingPath by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionDownloads,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Storage path card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.settingsDownloadLocation, fontWeight = FontWeight.Bold)
                Text(
                    text = downloadsDir?.let { strings.settingsDownloadActivePath(it.toString()) }
                        ?: strings.settingsDefaultStorageFolder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pathDraft,
                        onValueChange = { path ->
                            pathDraft = path
                            pathError = null
                            pathSaved = false
                        },
                        label = { Text(strings.settingsDownloadCustomPath) },
                        placeholder = { Text(strings.settingsDownloadCustomPathPlaceholder) },
                        modifier = Modifier.weight(1f).testTag("download-storage-input"),
                        singleLine = true,
                        enabled = !isSavingPath,
                        isError = pathError != null,
                    )
                    OutlinedButton(
                        onClick = {
                            chooseStorageDirectory(
                                initialPath = pathDraft,
                                activePath = downloadsDir,
                                title = strings.settingsDownloadChooseFolder,
                            )?.let { selected ->
                                pathDraft = selected.toString()
                                pathError = null
                                pathSaved = false
                            }
                        },
                        modifier = Modifier.testTag("download-storage-choose"),
                        enabled = !isSavingPath,
                    ) {
                        Text(strings.settingsDownloadChooseFolder)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val draft = pathDraft
                            isSavingPath = true
                            scope.launch {
                                try {
                                    val validation = withContext(Dispatchers.IO) { validateStoragePath(draft) }
                                    pathError = validation.error
                                    if (validation.error == null) {
                                        val updated = withContext(Dispatchers.IO) {
                                            preferenceStore.updatePreferences {
                                                it.copy(downloadStoragePath = validation.path)
                                            }
                                        }
                                        preferences = updated
                                        pathDraft = updated.downloadStoragePath
                                        pathSaved = true
                                        onPreferencesChanged?.invoke(updated)
                                    }
                                } catch (error: kotlinx.coroutines.CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    pathError = UiText.DownloadPathSaveFailed
                                } finally {
                                    isSavingPath = false
                                }
                            }
                        },
                        enabled = !isSavingPath && pathDraft != preferences.downloadStoragePath,
                        modifier = Modifier.testTag("download-storage-save"),
                    ) { Text(strings.text(if (isSavingPath) UiText.CheckingDownloadPath else UiText.SaveDownloadPath)) }
                    if (pathDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                pathDraft = ""
                                pathError = null
                                pathSaved = false
                            },
                            modifier = Modifier.testTag("download-storage-default"),
                            enabled = !isSavingPath,
                        ) {
                            Text(strings.settingsDownloadUseDefault)
                        }
                    }
                }
                pathError?.let {
                    Text(
                        strings.text(it),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("download-storage-error"),
                    )
                }
                if (pathSaved) {
                    Text(
                        strings.text(UiText.DownloadPathSaved),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("download-storage-saved"),
                    )
                }
                Text(
                    strings.text(UiText.DownloadPathHint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Parallel downloads card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsParallelDownloads, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        val isSelected = preferences.downloadParallelCount == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferenceStore.updatePreferences {
                                        it.copy(downloadParallelCount = count)
                                    }
                                    preferences = updated

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }

                Text(strings.text(UiText.ParallelPages), fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        val isSelected = preferences.downloadPageParallelCount == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("parallel-pages-$count"),
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferenceStore.updatePreferences {
                                        it.copy(downloadPageParallelCount = count)
                                    }
                                    preferences = updated

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("parallel-pages-$count"),
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }
            }
        }

        // Download ahead card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDownloadAheadTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsDownloadAheadDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to strings.settingsDownloadAheadDisabled,
                        1 to strings.settingsDownloadAheadChapters(1),
                        2 to strings.settingsDownloadAheadChapters(2),
                        3 to strings.settingsDownloadAheadChapters(3),
                        5 to strings.settingsDownloadAheadChapters(5),
                    ).forEach { (count, label) ->
                        val isSelected = preferences.downloadAhead == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = preferenceStore.updatePreferences { it.copy(downloadAhead = count) }
                                    preferences = updated

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Auto-download filters use the same category membership as library updates.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.libraryAutoDownloadNew, fontWeight = FontWeight.Bold)
                Switch(
                    checked = preferences.autoDownloadNewChapters,
                    onCheckedChange = { checked ->
                        val updated = preferenceStore.updatePreferences { it.copy(autoDownloadNewChapters = checked) }
                        preferences = updated
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("download-auto-new-switch"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.downloadNewUnreadOnly, modifier = Modifier.weight(1f))
                    Switch(
                        checked = preferences.autoDownloadUnreadOnly,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(autoDownloadUnreadOnly = checked)
                            }
                            preferences = updated
                            onPreferencesChanged?.invoke(updated)
                        },
                        enabled = preferences.autoDownloadNewChapters,
                        modifier = Modifier.testTag("download-new-unread-only-switch"),
                    )
                }
                Text(strings.downloadNewCategories, fontWeight = FontWeight.Medium)
                val selectableCategories = categories.filter { it.id > 0L }
                if (selectableCategories.isEmpty()) {
                    Text(strings.downloadNoCategories, style = MaterialTheme.typography.bodySmall)
                }
                selectableCategories.forEach { category ->
                    val selection = when (category.id) {
                        in preferences.autoDownloadCategories -> strings.downloadCategoryInclude
                        in preferences.autoDownloadCategoriesExclude -> strings.downloadCategoryExclude
                        else -> strings.downloadCategoryAny
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.name, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                val updated = preferenceStore.updatePreferences { current ->
                                    when (category.id) {
                                        in current.autoDownloadCategories -> current.copy(
                                            autoDownloadCategories = current.autoDownloadCategories - category.id,
                                            autoDownloadCategoriesExclude =
                                            current.autoDownloadCategoriesExclude + category.id,
                                        )
                                        in current.autoDownloadCategoriesExclude -> current.copy(
                                            autoDownloadCategoriesExclude =
                                            current.autoDownloadCategoriesExclude - category.id,
                                        )
                                        else -> current.copy(
                                            autoDownloadCategories = current.autoDownloadCategories + category.id,
                                        )
                                    }
                                }
                                preferences = updated
                                onPreferencesChanged?.invoke(updated)
                            },
                            enabled = preferences.autoDownloadNewChapters,
                            modifier = Modifier.testTag("download-category-${category.id}"),
                        ) { Text(selection) }
                    }
                }
            }
        }

        // Delete read chapters card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsDeleteReadChaptersTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsDeleteReadChaptersDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.deleteDownloadedRead,
                    onCheckedChange = { checked ->
                        val updated = preferenceStore.updatePreferences { it.copy(deleteDownloadedRead = checked) }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("delete-downloaded-read-switch"),
                )
            }
        }

        // CBZ archive card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsSaveChapterAsCbzTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsSaveChapterAsCbzDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = preferences.saveChapterAsCbz,
                    onCheckedChange = { checked ->
                        val updated = preferenceStore.updatePreferences { it.copy(saveChapterAsCbz = checked) }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("save-downloaded-chapter-as-cbz-checkbox"),
                )
            }
        }

        // Tall image split card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsSplitTallImagesTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsSplitTallImagesDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = preferences.splitTallImages,
                    onCheckedChange = { checked ->
                        val updated = preferenceStore.updatePreferences { it.copy(splitTallImages = checked) }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("split-tall-images-checkbox"),
                )
            }
        }
    }
}

internal fun chooseStorageDirectory(initialPath: String, activePath: Path?, title: String): Path? {
    val initial = runCatching {
        initialPath.takeIf(String::isNotBlank)?.let(Path::of)
    }.getOrNull() ?: activePath
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initial?.toFile()?.let { selected ->
            currentDirectory = when {
                selected.isDirectory -> selected
                selected.parentFile?.isDirectory == true -> selected.parentFile
                else -> null
            }
        }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val selected = chooser.selectedFile?.toPath() ?: return null
    return selected.toAbsolutePath().normalize().takeIf { chooser.selectedFile.isDirectory }
}

@Composable
private fun TrackingSettingsPane(trackerManager: DesktopTrackerManager?) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var loginTracker by remember { mutableStateOf<DesktopTracker?>(null) }
    val trackers = trackerManager?.trackers ?: emptyList()
    val loggedInCount = trackers.count { it.isLoggedIn }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionTracking,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    strings.settingsTrackingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.settingsTrackingDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    strings.settingsConnectedTrackers(loggedInCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val selfHostedNames = setOf("Komga", "Kavita", "Suwayomi")
        val publicTrackers = trackers.filter { it.name !in selfHostedNames }
        val selfHostedTrackers = trackers.filter { it.name in selfHostedNames }

        @Composable
        fun TrackerCard(tracker: DesktopTracker) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("tracker-setting-card-${tracker.id}"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tracker.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tracker.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (tracker.isLoggedIn) {
                                Text(
                                    strings.settingsTrackerLoggedInAs(tracker.username ?: "User", tracker.serverUrl),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    strings.trackingNotLoggedIn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }

                    if (tracker.isLoggedIn) {
                        FilledTonalButton(
                            onClick = { trackerManager?.logout(tracker.id) },
                            modifier = Modifier.testTag("tracker-logout-${tracker.id}"),
                        ) {
                            Text(strings.settingsTrackerLogout)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { loginTracker = tracker },
                            modifier = Modifier.testTag("tracker-login-${tracker.id}"),
                        ) {
                            Text(
                                if (tracker.authType == TrackerAuthType.SERVER) {
                                    strings.trackerConnect
                                } else {
                                    strings.trackerLogin
                                },
                            )
                        }
                    }
                }
            }
        }

        if (publicTrackers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.settingsTrackingPublicServices,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                publicTrackers.forEach { tracker ->
                    TrackerCard(tracker)
                }
            }
        }

        if (selfHostedTrackers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.settingsTrackingSelfHosted,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selfHostedTrackers.forEach { tracker ->
                    TrackerCard(tracker)
                }
            }
        }
    }

    loginTracker?.let { tracker ->
        TrackerLoginDialog(
            tracker = tracker,
            onDismiss = { loginTracker = null },
            onLogin = { creds ->
                trackerManager?.login(tracker.id, creds) == true
            },
        )
    }
}

@Composable
private fun BackupSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler?,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    syncServerManager: mihon.desktop.sync.DesktopSyncServerManager? = null,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
    var backupInProgress by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var syncInProgress by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionBackup,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsBackupTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsBackupDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.testTag("settings-export-backup-button"),
                    ) {
                        Text(strings.settingsExportBackupButton)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.testTag("settings-import-backup-button"),
                    ) {
                        Text(strings.settingsImportBackupButton)
                    }
                }
            }
        }

        // Automated Periodic Backups Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.backupAutoTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.backupAutoDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency / Interval
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.backupIntervalOff,
                            6 to strings.backupInterval6Hours,
                            12 to strings.backupInterval12Hours,
                            24 to strings.backupIntervalDaily,
                            48 to strings.backupInterval2Days,
                            168 to strings.backupIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.backupIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferenceStore.updatePreferences {
                                            it.copy(backupIntervalHours = hours)
                                        }
                                        preferences = updated

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                BackupStorageLocation(preferenceStore, preferences.backupStoragePath) { updated ->
                    preferences = updated
                    onPreferencesChanged?.invoke(updated)
                }

                // Retention Count
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupRetention, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 20).forEach { count ->
                            val isSelected = preferences.backupRetentionCount == count
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferenceStore.updatePreferences {
                                            it.copy(backupRetentionCount = count)
                                        }
                                        preferences = updated

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            }
                        }
                    }
                }

                // Last auto backup info & Trigger Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timestampFormatter = remember(strings) {
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDateTime(
                                java.time.format.FormatStyle.MEDIUM,
                                java.time.format.FormatStyle.SHORT,
                            )
                            .withLocale(strings.locale)
                    }
                    val lastBackupStr = if (preferences.lastAutoBackupEpochMillis > 0L) {
                        java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(preferences.lastAutoBackupEpochMillis),
                            java.time.ZoneId.systemDefault(),
                        ).format(timestampFormatter)
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.backupLastAutoBackup} $lastBackupStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (backupScheduler != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    backupInProgress = true
                                    try {
                                        val path = backupScheduler.performBackup(isManual = false)
                                        preferences = preferenceStore.load()
                                        backupMessage = strings.backupExportSuccess(path.toString())
                                    } catch (e: Exception) {
                                        backupMessage = strings.text(UiText.BackupFailed, e.message.orEmpty())
                                    } finally {
                                        backupInProgress = false
                                    }
                                }
                            },
                            enabled = !backupInProgress,
                            modifier = Modifier.testTag("backup-now-button"),
                        ) {
                            Text(strings.backupNow)
                        }
                    }
                }

                backupMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Library Sync Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.syncCardTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.syncCardDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Enable Sync Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.syncEnableTitle, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = preferences.syncEnabled,
                        onCheckedChange = { enabled ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(syncEnabled = enabled)
                            }
                            preferences = updated
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("sync-enabled-switch"),
                    )
                }

                // Builtin Server Controls
                var isServerRunning by remember { mutableStateOf(syncServerManager?.isRunning == true) }
                var serverError by remember { mutableStateOf(syncServerManager?.lastError) }
                var copiedFeedback by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. Enable Builtin Server Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(strings.syncServerEnableTitle, fontWeight = FontWeight.Medium)
                            val statusText = if (isServerRunning) {
                                strings.syncServerRunning
                            } else {
                                strings.syncServerStopped
                            }
                            val statusColor = if (isServerRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = "${strings.syncServerStatus} $statusText",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                            )
                            serverError?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Switch(
                            checked = preferences.syncServerEnabled,
                            onCheckedChange = { enabled ->
                                var token = preferences.syncServerToken
                                if (token.isBlank()) {
                                    token = DesktopPreferenceStore.generateSyncToken()
                                }
                                if (enabled) {
                                    syncServerManager?.start(
                                        port = preferences.syncServerPort,
                                        token = token,
                                        deviceName = preferences.syncServerDeviceName,
                                        quickPairEnabled = preferences.syncServerQuickPair,
                                    )
                                } else {
                                    syncServerManager?.stop()
                                }
                                isServerRunning = syncServerManager?.isRunning == true
                                serverError = syncServerManager?.lastError
                                val updated = preferenceStore.updatePreferences {
                                    it.copy(syncServerEnabled = enabled, syncServerToken = token)
                                }
                                preferences = updated
                                onPreferencesChanged?.invoke(updated)
                            },
                            modifier = Modifier.testTag("sync-server-enabled-switch"),
                        )
                    }

                    // 2. Port configuration & Token regeneration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = preferences.syncServerPort.toString(),
                            onValueChange = { portStr ->
                                val port = portStr.toIntOrNull()
                                if (port != null && port in 1024..65535) {
                                    val updated = preferenceStore.updatePreferences {
                                        it.copy(syncServerPort = port)
                                    }
                                    preferences = updated
                                    onPreferencesChanged?.invoke(updated)
                                    if (preferences.syncServerEnabled && syncServerManager?.isRunning == true) {
                                        syncServerManager.start(
                                            port = port,
                                            token = preferences.syncServerToken,
                                            deviceName = preferences.syncServerDeviceName,
                                            quickPairEnabled = preferences.syncServerQuickPair,
                                        )
                                        isServerRunning = syncServerManager.isRunning
                                        serverError = syncServerManager.lastError
                                    }
                                }
                            },
                            label = { Text(strings.syncServerPort) },
                            modifier = Modifier.width(160.dp).testTag("sync-server-port-field"),
                            singleLine = true,
                        )

                        Button(
                            onClick = {
                                val newToken = DesktopPreferenceStore.generateSyncToken()
                                val updated = preferenceStore.updatePreferences {
                                    it.copy(syncServerToken = newToken)
                                }
                                preferences = updated
                                onPreferencesChanged?.invoke(updated)
                                if (preferences.syncServerEnabled && syncServerManager?.isRunning == true) {
                                    syncServerManager.start(
                                        port = preferences.syncServerPort,
                                        token = newToken,
                                        deviceName = preferences.syncServerDeviceName,
                                        quickPairEnabled = preferences.syncServerQuickPair,
                                    )
                                    isServerRunning = syncServerManager.isRunning
                                    serverError = syncServerManager.lastError
                                }
                            },
                            modifier = Modifier.testTag("sync-server-regen-token-button"),
                        ) {
                            Text(strings.syncServerRegenerateToken)
                        }
                    }

                    // 3. Pairing Code & Copy
                    // Network-interface enumeration can block for hundreds of ms on machines
                    // with VPN/TUN adapters, so load it off the UI thread.
                    val localIps by produceState(
                        initialValue = preferences.syncServerSelectedIp
                            .takeIf { it.isNotBlank() }
                            ?.let { listOf(it) }
                            ?: listOf("127.0.0.1"),
                    ) {
                        value = withContext(Dispatchers.IO) {
                            mihon.desktop.sync.DesktopSyncServerManager.getLocalIpAddresses()
                        }
                    }
                    var selectedIp by remember(preferences.syncServerSelectedIp, localIps) {
                        mutableStateOf(
                            mihon.desktop.sync.DesktopSyncServerManager.effectiveSelectedIp(
                                preferences.syncServerSelectedIp,
                                localIps,
                            ),
                        )
                    }
                    var ipMenuExpanded by remember { mutableStateOf(false) }
                    val pairingUri = remember(selectedIp, preferences.syncServerPort, preferences.syncServerToken) {
                        if (preferences.syncServerToken.isNotBlank()) {
                            mihon.sync.transport.http.SyncPairingCode(
                                selectedIp,
                                preferences.syncServerPort,
                                preferences.syncServerToken,
                            ).toUriString()
                        } else {
                            ""
                        }
                    }

                    // Address selector: machines with VPN/TUN/virtual adapters expose many IPs;
                    // the phone can only reach the real LAN one, so let the user pick.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.syncServerAddress, fontWeight = FontWeight.Medium)
                        Box {
                            OutlinedButton(onClick = { ipMenuExpanded = true }) {
                                Text(selectedIp)
                            }
                            DropdownMenu(
                                expanded = ipMenuExpanded,
                                onDismissRequest = { ipMenuExpanded = false },
                            ) {
                                localIps.forEach { ip ->
                                    DropdownMenuItem(
                                        text = { Text(ip) },
                                        onClick = {
                                            selectedIp = ip
                                            ipMenuExpanded = false
                                            val updated = preferenceStore.updatePreferences {
                                                it.copy(syncServerSelectedIp = ip)
                                            }
                                            preferences = updated
                                            onPreferencesChanged?.invoke(updated)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.syncServerPairingCode, fontWeight = FontWeight.Medium)

                        // QR code (above the text field, same pairingUri source)
                        if (pairingUri.isNotBlank()) {
                            mihon.desktop.ui.common.QrCodeImage(
                                uri = pairingUri,
                                sizeInDp = 200.dp,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = pairingUri,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.weight(1f).testTag("sync-server-pairing-code-field"),
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    if (pairingUri.isNotBlank()) {
                                        try {
                                            val selection = java.awt.datatransfer.StringSelection(pairingUri)
                                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(selection, selection)
                                            copiedFeedback = true
                                        } catch (_: Throwable) {}
                                    }
                                },
                                enabled = pairingUri.isNotBlank(),
                                modifier = Modifier.testTag("sync-server-copy-button"),
                            ) {
                                Text(strings.syncServerCopyPairingCode)
                            }
                        }
                        if (copiedFeedback) {
                            Text(
                                strings.syncServerCopied,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 4. Firewall hint
                    Text(
                        strings.syncServerFirewallHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 5. Quick-pair toggle (security-gated, default off)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.syncServerQuickPairTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = preferences.syncServerQuickPair,
                                onCheckedChange = { enabled ->
                                    val updated = preferenceStore.updatePreferences {
                                        it.copy(syncServerQuickPair = enabled)
                                    }
                                    preferences = updated
                                    onPreferencesChanged?.invoke(updated)
                                    if (syncServerManager?.isRunning == true) {
                                        syncServerManager.updateAdvertisement(
                                            port = preferences.syncServerPort,
                                            deviceName = preferences.syncServerDeviceName,
                                            quickPairEnabled = enabled,
                                            token = preferences.syncServerToken,
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("sync-server-quick-pair-switch"),
                            )
                        }
                        Text(
                            strings.syncServerQuickPairWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // 6. Device name
                    var deviceNameDraft by remember(preferences.syncServerDeviceName) {
                        mutableStateOf(preferences.syncServerDeviceName)
                    }

                    fun commitDeviceName(name: String) {
                        if (name != preferences.syncServerDeviceName) {
                            val updated = preferenceStore.updatePreferences {
                                it.copy(syncServerDeviceName = name)
                            }
                            preferences = updated
                            onPreferencesChanged?.invoke(updated)
                            if (syncServerManager?.isRunning == true) {
                                syncServerManager.updateAdvertisement(
                                    port = preferences.syncServerPort,
                                    deviceName = name,
                                    quickPairEnabled = preferences.syncServerQuickPair,
                                    token = preferences.syncServerToken,
                                )
                            }
                        }
                    }

                    LaunchedEffect(deviceNameDraft) {
                        if (deviceNameDraft != preferences.syncServerDeviceName) {
                            delay(500)
                            commitDeviceName(deviceNameDraft)
                        }
                    }

                    OutlinedTextField(
                        value = deviceNameDraft,
                        onValueChange = { name ->
                            deviceNameDraft = name
                        },
                        label = { Text(strings.syncServerDeviceNameLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    commitDeviceName(deviceNameDraft)
                                }
                            }
                            .testTag("sync-server-device-name-field"),
                        singleLine = true,
                    )
                }

                // Sync status & Sync Now button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timestampFormatter = remember(strings) {
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDateTime(
                                java.time.format.FormatStyle.MEDIUM,
                                java.time.format.FormatStyle.SHORT,
                            )
                            .withLocale(strings.locale)
                    }
                    val lastSyncStr = if (preferences.lastSyncEpochMillis > 0L) {
                        val timeStr = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(preferences.lastSyncEpochMillis),
                            java.time.ZoneId.systemDefault(),
                        ).format(timestampFormatter)
                        val msg = preferences.lastSyncMessage
                        if (msg.isNotBlank()) "$timeStr ($msg)" else timeStr
                    } else {
                        strings.syncNever
                    }
                    Text(
                        text = "${strings.syncLastResult} $lastSyncStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (syncScheduler != null) {
                        val isSyncReady = preferences.syncServerToken.isNotBlank() &&
                            (syncServerManager?.isRunning == true)
                        Button(
                            onClick = {
                                scope.launch {
                                    syncInProgress = true
                                    try {
                                        val report = syncScheduler.syncNow()
                                        preferences = preferenceStore.load()
                                        syncMessage = if (report.success) {
                                            "${strings.syncSuccess}: ${preferences.lastSyncMessage}"
                                        } else {
                                            "${strings.syncFailed}: ${report.errorMessage}"
                                        }
                                    } catch (e: Exception) {
                                        syncMessage = "${strings.syncFailed}: ${e.message}"
                                    } finally {
                                        syncInProgress = false
                                    }
                                }
                            },
                            enabled = !syncInProgress && isSyncReady,
                            modifier = Modifier.testTag("sync-now-button"),
                        ) {
                            Text(if (syncInProgress) strings.syncInProgress else strings.syncNowButton)
                        }
                    }
                }

                syncMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySettingsPane(
    preferenceStore: DesktopPreferenceStore,
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler?,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(preferenceStore.load()) }
    var updateInProgress by remember { mutableStateOf(false) }
    var updateResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.libraryTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.libraryUpdateTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.libraryUpdating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.libraryUpdateInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.libraryUpdateIntervalManual,
                            6 to strings.libraryUpdateInterval6Hours,
                            12 to strings.libraryUpdateInterval12Hours,
                            24 to strings.libraryUpdateIntervalDaily,
                            48 to strings.libraryUpdateInterval2Days,
                            168 to strings.libraryUpdateIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.libraryUpdateIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = preferenceStore.updatePreferences {
                                            it.copy(libraryUpdateIntervalHours = hours)
                                        }
                                        preferences = updated

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Filter switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipCompleted)
                    Switch(
                        checked = preferences.libraryUpdateSkipCompleted,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(libraryUpdateSkipCompleted = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-completed-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipUnread)
                    Switch(
                        checked = preferences.libraryUpdateSkipUnread,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(libraryUpdateSkipUnread = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-unread-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.text(UiText.SkipNotStarted))
                    Switch(
                        checked = preferences.libraryUpdateSkipStarted,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(libraryUpdateSkipStarted = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-started-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipOutsideReleasePeriod)
                    Switch(
                        checked = preferences.libraryUpdateSkipOutsideReleasePeriod,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(libraryUpdateSkipOutsideReleasePeriod = checked)
                            }
                            preferences = updated
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-outside-release-period-switch"),
                    )
                }

                OutlinedTextField(
                    value = preferences.libraryUpdateCategories.sorted().joinToString(","),
                    onValueChange = { value ->
                        val updated = preferenceStore.updatePreferences {
                            it.copy(libraryUpdateCategories = parsePositiveLongSet(value))
                        }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    label = { Text(strings.text(UiText.IncludeCategoryIds)) },
                    modifier = Modifier.fillMaxWidth().testTag("update-include-categories"),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = preferences.libraryUpdateCategoriesExclude.sorted().joinToString(","),
                    onValueChange = { value ->
                        val updated = preferenceStore.updatePreferences {
                            it.copy(libraryUpdateCategoriesExclude = parsePositiveLongSet(value))
                        }
                        preferences = updated

                        onPreferencesChanged?.invoke(updated)
                    },
                    label = { Text(strings.text(UiText.ExcludeCategoryIds)) },
                    modifier = Modifier.fillMaxWidth().testTag("update-exclude-categories"),
                    singleLine = true,
                )

                HorizontalDivider()

                // Auto download new chapters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryAutoDownloadNew)
                    Switch(
                        checked = preferences.autoDownloadNewChapters,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(autoDownloadNewChapters = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("auto-download-new-switch"),
                    )
                }

                // Only run scheduled library updates while on AC power (desktops count as AC)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateOnlyOnAcPower)
                    Switch(
                        checked = preferences.libraryUpdateOnlyOnAcPower,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(libraryUpdateOnlyOnAcPower = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("library-update-only-on-ac-power"),
                    )
                }

                // Desktop notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.notificationsDesktopEnabled)
                    Switch(
                        checked = preferences.desktopNotificationsEnabled,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(desktopNotificationsEnabled = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("desktop-notifications-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.text(UiText.HideNotificationContent))
                    Switch(
                        checked = preferences.desktopNotificationsHideContent,
                        onCheckedChange = { checked ->
                            val updated = preferenceStore.updatePreferences {
                                it.copy(desktopNotificationsHideContent = checked)
                            }
                            preferences = updated

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("desktop-notifications-hide-content-switch"),
                    )
                }

                HorizontalDivider()

                // Last update & trigger button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lastUpdateStr = if (preferences.lastLibraryUpdateEpochMillis > 0) {
                        val timestampFormatter = remember(strings) {
                            java.time.format.DateTimeFormatter
                                .ofLocalizedDateTime(
                                    java.time.format.FormatStyle.MEDIUM,
                                    java.time.format.FormatStyle.SHORT,
                                )
                                .withLocale(strings.locale)
                        }
                        java.time.Instant.ofEpochMilli(preferences.lastLibraryUpdateEpochMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(timestampFormatter)
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.libraryLastUpdate} $lastUpdateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            if (updateScheduler != null && !updateInProgress) {
                                scope.launch {
                                    updateInProgress = true
                                    updateResultMessage = null
                                    try {
                                        val report = withContext(Dispatchers.IO) {
                                            updateScheduler.triggerUpdateNow()
                                        }
                                        preferences = preferenceStore.load()
                                        updateResultMessage = if (report != null) {
                                            strings.settingsLibraryUpdateResult(
                                                report.totalMangaChecked,
                                                report.newChaptersTotal,
                                            )
                                        } else {
                                            strings.settingsLibraryUpdateCompleted
                                        }
                                    } catch (e: Exception) {
                                        updateResultMessage = strings.settingsLibraryUpdateFailed(e.message ?: "")
                                    } finally {
                                        updateInProgress = false
                                    }
                                }
                            }
                        },
                        enabled = !updateInProgress,
                        modifier = Modifier.testTag("update-library-now-btn"),
                    ) {
                        if (updateInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(strings.libraryUpdateNow)
                    }
                }

                updateResultMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler?,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    diagnosticService: DiagnosticBundleService?,
    onOpenCookieManager: () -> Unit = {},
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: Path? = null,
    diskCacheDir: Path? = null,
    profileRoot: Path? = null,
    databaseCleaner: mihon.desktop.library.repository.LibraryDatabaseCleaner? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<DiagnosticSummary?>(null) }
    var checkingIntegrity by remember { mutableStateOf(false) }
    var bundleExportPath by remember { mutableStateOf<String?>(null) }
    var nonLibrarySources by remember {
        mutableStateOf(emptyList<mihon.desktop.library.repository.NonLibrarySourceCount>())
    }
    var selectedSources by remember { mutableStateOf(emptySet<Long>()) }
    var keepReadManga by remember { mutableStateOf(true) }
    var confirmDatabaseClear by remember { mutableStateOf(false) }
    var clearingDatabase by remember { mutableStateOf(false) }
    var databaseClearMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(databaseCleaner) {
        nonLibrarySources = if (databaseCleaner == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { databaseCleaner.nonLibrarySourceCounts() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionAdvanced,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        BackgroundSettingsCard(preferenceStore, backgroundScheduler, onPreferencesChanged)
        NetworkSettingsCard(preferenceStore)

        // Storage & Cache Cleaner Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("storage-cleaner-card"),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.storageCleanerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.storageCleanerDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var downloadSizeBytes by remember { mutableStateOf<Long?>(null) }
                var storageUsage by remember { mutableStateOf<mihon.desktop.storage.StorageUsage?>(null) }
                var usageRefresh by remember { mutableStateOf(0) }
                var isCleaning by remember { mutableStateOf(false) }
                var cleanerMessage by remember { mutableStateOf<String?>(null) }

                androidx.compose.runtime.LaunchedEffect(downloadsDir, profileRoot, usageRefresh) {
                    if (downloadCacheCleaner != null) {
                        downloadSizeBytes = withContext(Dispatchers.IO) {
                            downloadCacheCleaner.calculateDownloadSize()
                        }
                    }
                    if (profileRoot != null && downloadsDir != null) {
                        storageUsage = withContext(Dispatchers.IO) {
                            val configuredBackup = preferenceStore.load().backupStoragePath
                            val backupDir = configuredBackup.takeIf { it.isNotBlank() }
                                ?.let { runCatching { Path.of(it) }.getOrNull() }
                                ?: profileRoot.resolve("backups")
                            mihon.desktop.storage.StorageUsageCalculator().calculate(
                                profileRoot,
                                downloadsDir,
                                backupDir,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.storageCleanerDownloadSize}: ${formatStorageSize(downloadSizeBytes ?: 0L)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("storage-cleaner-size-label"),
                    )
                }

                storageUsage?.let { usage ->
                    listOf(
                        strings.storageUsageCache to usage.imageCache,
                        strings.storageUsageDatabase to usage.database,
                        strings.storageUsageExtensions to usage.extensions,
                        strings.storageUsageCovers to usage.covers,
                        strings.storageUsageBackups to usage.backups,
                        strings.storageUsageLogs to usage.logs,
                    ).forEach { (label, bytes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(formatStorageSize(bytes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (downloadCacheCleaner != null) {
                                scope.launch {
                                    isCleaning = true
                                    val report = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.deleteReadChapters()
                                    }
                                    downloadSizeBytes = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.calculateDownloadSize()
                                    }
                                    isCleaning = false
                                    val freed = formatStorageSize(report.freedBytes)
                                    cleanerMessage =
                                        "${strings.storageCleanerClearReadSuccess}: " +
                                        "${report.deletedChaptersCount} ($freed)"
                                    usageRefresh++
                                }
                            }
                        },
                        enabled = !isCleaning && downloadCacheCleaner != null,
                        modifier = Modifier.testTag("clean-read-chapters-button"),
                    ) {
                        Text(strings.storageCleanerClearRead)
                    }

                    OutlinedButton(
                        onClick = {
                            if (diskCacheDir != null && downloadCacheCleaner != null) {
                                scope.launch {
                                    isCleaning = true
                                    val cleared = withContext(Dispatchers.IO) {
                                        downloadCacheCleaner.clearImageDiskCache(diskCacheDir)
                                    }
                                    isCleaning = false
                                    val freed = formatStorageSize(cleared)
                                    cleanerMessage =
                                        "${strings.storageCleanerClearImageCacheSuccess}: $freed"
                                    usageRefresh++
                                }
                            }
                        },
                        enabled = !isCleaning && diskCacheDir != null,
                        modifier = Modifier.testTag("clean-image-cache-button"),
                    ) {
                        Text(strings.storageCleanerClearImageCache)
                    }
                }

                cleanerMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("storage-cleaner-message"),
                    )
                }
            }
        }

        if (databaseCleaner != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("clear-database-card"),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.clearDatabaseTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(strings.clearDatabaseDescription, style = MaterialTheme.typography.bodySmall)
                    if (nonLibrarySources.isEmpty()) {
                        Text(strings.clearDatabaseEmpty, modifier = Modifier.testTag("clear-database-empty"))
                    } else {
                        nonLibrarySources.forEach { source ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${source.sourceName} (${source.mangaCount})", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = source.sourceId in selectedSources,
                                    onCheckedChange = { checked ->
                                        selectedSources = if (checked) {
                                            selectedSources + source.sourceId
                                        } else {
                                            selectedSources - source.sourceId
                                        }
                                    },
                                    modifier = Modifier.testTag("clear-database-source-${source.sourceId}"),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(strings.clearDatabaseKeepRead, modifier = Modifier.weight(1f))
                            Switch(
                                checked = keepReadManga,
                                onCheckedChange = { keepReadManga = it },
                                modifier = Modifier.testTag("clear-database-keep-read"),
                            )
                        }
                        Button(
                            onClick = { confirmDatabaseClear = true },
                            enabled = selectedSources.isNotEmpty() && !clearingDatabase,
                            modifier = Modifier.testTag("clear-database-button"),
                        ) {
                            Text(strings.clearDatabaseAction)
                        }
                    }
                    databaseClearMessage?.let { Text(it, modifier = Modifier.testTag("clear-database-message")) }
                }
            }
            if (confirmDatabaseClear) {
                AlertDialog(
                    onDismissRequest = { confirmDatabaseClear = false },
                    title = { Text(strings.clearDatabaseTitle) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.clearDatabaseConfirm)
                            if (!keepReadManga) {
                                Text(strings.clearDatabaseReadWarning, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDatabaseClear = false
                                clearingDatabase = true
                                scope.launch {
                                    try {
                                        val removed = withContext(Dispatchers.IO) {
                                            databaseCleaner.clearNonLibraryManga(selectedSources, keepReadManga)
                                        }
                                        nonLibrarySources = withContext(Dispatchers.IO) {
                                            databaseCleaner.nonLibrarySourceCounts()
                                        }
                                        selectedSources = emptySet()
                                        databaseClearMessage = strings.clearDatabaseResult(removed)
                                    } catch (error: Exception) {
                                        databaseClearMessage =
                                            "${strings.clearDatabaseFailed}: ${error.message.orEmpty()}"
                                    } finally {
                                        clearingDatabase = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("clear-database-confirm"),
                        ) { Text(strings.clearDatabaseAction) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDatabaseClear = false }) {
                            Text(strings.text(UiText.AppUpdateCancel))
                        }
                    },
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.cookieManagerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.cookieManagerDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenCookieManager,
                    modifier = Modifier.testTag("open-cookie-manager-button"),
                ) {
                    Text(strings.cookieManagerButton)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.settingsDiagnosticsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    checkingIntegrity = true
                                    summary = withContext(Dispatchers.Default) { it.createSummary() }
                                    checkingIntegrity = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("check-integrity-button"),
                    ) {
                        if (checkingIntegrity) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                        } else {
                            Text(strings.settingsRunIntegrityCheck)
                        }
                    }

                    Button(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    val tempZip = Path.of(System.getProperty("java.io.tmpdir"))
                                        .resolve("mihon-diagnostics-${System.currentTimeMillis()}.zip")
                                    withContext(Dispatchers.IO) { it.exportBundle(tempZip) }
                                    bundleExportPath = tempZip.toString()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export-diagnostic-bundle-button"),
                    ) {
                        Text(strings.settingsExportDiagnosticBundle)
                    }
                }

                summary?.let { s ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        strings.settingsDatabaseIntegrity(s.databaseIntegrity.joinToString(", ")),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(strings.settingsOsInfo("${s.osName} ${s.osVersion} (${s.osArch})"))
                    Text(strings.settingsJavaInfo("${s.javaVersion} (${s.javaVendor})"))
                    Text(strings.settingsLogFilesCount(s.logFileCount))
                    if (s.snapshotCount > 0) {
                        val sizeKb = (s.snapshotSizeBytes + 1023) / 1024
                        Text(strings.text(UiText.MigrationSnapshotsSummary, s.snapshotCount, sizeKb))
                    }
                }

                bundleExportPath?.let { path ->
                    Text(
                        strings.settingsBundleExportedTo(path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}

private fun parsePositiveLongSet(value: String): Set<Long> = value
    .split(',', ';', ' ')
    .asSequence()
    .mapNotNull { it.trim().toLongOrNull() }
    .filter { it > 0L }
    .toSet()
