package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Backup
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.i18n.TraditionalChineseStrings

private fun backgroundTasksTitle(strings: DesktopStrings): String = when (strings) {
    SimplifiedChineseStrings -> "关闭应用后继续更新与备份"
    TraditionalChineseStrings -> "關閉應用程式後繼續更新與備份"
    else -> "Run updates and backups while the app is closed"
}

internal fun searchableMoreEntries(strings: DesktopStrings): List<SearchableSettingEntry> =
    searchableMoreEntriesPart1(strings) +
        searchableMoreEntriesPart2(strings) +
        searchableMoreEntriesPart3(strings)

private fun searchableMoreEntriesPart1(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
)

private fun searchableMoreEntriesPart2(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "backup-location",
        section = SettingsSection.Backup,
        title = strings.backupLocation,
        subtitle = null,
        keywords = listOf("backup location", "backup folder", "path", "directory", "备份目录", "備份目錄", "存储路径"),
    ),
    SearchableSettingEntry(
        id = "backup-google-drive-sync",
        section = SettingsSection.Backup,
        title = strings.gdriveTitle,
        subtitle = strings.gdriveDescription,
        keywords = listOf("sync", "google drive", "cloud", "multi-device", "同步", "云同步", "雲端同步", "谷歌云端硬盘"),
    ),
    SearchableSettingEntry(
        id = "advanced-background-tasks",
        section = SettingsSection.Advanced,
        title = backgroundTasksTitle(strings),
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
)

private fun searchableMoreEntriesPart3(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
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
