package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.launch
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.security.MIN_PIN_LENGTH

internal fun searchableGeneralEntries(strings: DesktopStrings): List<SearchableSettingEntry> =
    searchableGeneralEntriesPart1(strings) +
        searchableGeneralEntriesPart2(strings) +
        searchableGeneralEntriesPart3(strings) +
        searchableGeneralEntriesPart4(strings) +
        searchableGeneralEntriesPart5(strings) +
        searchableGeneralEntriesPart6(strings)

private fun searchableGeneralEntriesPart1(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)

private fun searchableGeneralEntriesPart2(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)

private fun searchableGeneralEntriesPart3(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)

private fun searchableGeneralEntriesPart4(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "appearance-interface-density",
        section = SettingsSection.Appearance,
        title = strings.text(UiText.InterfaceDensity),
        subtitle = "${strings.text(UiText.DensityStandard)}, ${strings.text(UiText.DensityCompact)}",
        keywords = listOf(
            "density",
            "compact",
            "standard",
            "spacing",
            "padding",
            "interface",
            "密度",
            "紧凑",
            "緊湊",
            "间距",
            "間距",
            "界面",
            "介面",
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
        id = "appearance-splash-every-launch",
        section = SettingsSection.Appearance,
        title = strings.settingsSplashEveryLaunchTitle,
        subtitle = strings.settingsSplashEveryLaunchSubtitle,
        keywords = listOf("splash", "startup", "animation", "launch", "启动", "啟動", "动画", "動畫", "开屏", "開屏"),
    ),
    SearchableSettingEntry(
        id = "appearance-color-scheme",
        section = SettingsSection.Appearance,
        title = strings.settingsAppThemeTitle,
        subtitle = null,
        keywords = listOf("color", "scheme", "palette", "accent", "lavender", "sakura", "色彩", "配色", "主题色", "顏色"),
    ),
)

private fun searchableGeneralEntriesPart5(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)

private fun searchableGeneralEntriesPart6(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)
