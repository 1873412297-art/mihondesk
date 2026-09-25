package eu.kanade.tachiyomi.data.sync

import java.util.Locale

object SyncStrings {
    private val isZh: Boolean
        get() = Locale.getDefault().language.startsWith("zh")

    val librarySync: String
        get() = if (isZh) "书架同步" else "Library sync"

    val librarySyncSummary: String
        get() = if (isZh) "双端跨设备自动同步收藏、进度与分类" else "Sync library, progress, and categories across devices"

    val enableSync: String
        get() = if (isZh) "启用同步" else "Enable sync"

    val enableSyncSummary: String
        get() = if (isZh) "自动或手动同步书架状态到同步目录" else "Sync library state automatically or manually"

    val syncDirectory: String
        get() = if (isZh) "同步目录" else "Sync directory"

    val syncDirectorySummary: String
        get() = if (isZh) "云盘或 WebDAV 挂载的本地文件夹" else "Cloud drive or WebDAV mounted folder"

    val noDirectorySet: String
        get() = if (isZh) "未设置同步目录" else "No directory selected"

    val syncFrequency: String
        get() = if (isZh) "自动同步频率" else "Sync frequency"

    val syncFreqManual: String
        get() = if (isZh) "仅手动" else "Manual only"

    val syncFreq15m: String
        get() = if (isZh) "每 15 分钟" else "Every 15 minutes"

    val syncFreq1h: String
        get() = if (isZh) "每 1 小时" else "Every 1 hour"

    val syncFreq6h: String
        get() = if (isZh) "每 6 小时" else "Every 6 hours"

    val syncFreq24h: String
        get() = if (isZh) "每 24 小时" else "Every 24 hours"

    val syncOnlyOnWifi: String
        get() = if (isZh) "仅在 Wi-Fi 下同步" else "Sync only on Wi-Fi"

    val syncOnlyOnWifiSummary: String
        get() = if (isZh) "避免移动流量消耗" else "Save mobile data"

    val syncNow: String
        get() = if (isZh) "立即同步" else "Sync now"

    val syncing: String
        get() = if (isZh) "正在同步…" else "Syncing…"

    val lastSync: String
        get() = if (isZh) "最近同步" else "Last sync"

    val lastSyncNever: String
        get() = if (isZh) "从未" else "Never"

    val lastSyncResult: String
        get() = if (isZh) "最近同步结果" else "Last sync result"

    val localMangaNotice: String
        get() = if (isZh) {
            "说明：本地漫画（Local source）天然无跨端全局身份，不参与同步。"
        } else {
            "Note: Local manga entries do not have cross-device identities and are excluded."
        }
}
