package eu.kanade.tachiyomi.data.sync

import java.util.Locale

/** Sync UI strings in English, Simplified Chinese and Traditional Chinese. */
object SyncStrings {
    enum class Variant { EN, HANS, HANT }

    fun variantFor(locale: Locale): Variant {
        if (locale.language != "zh") return Variant.EN
        return when (locale.script) {
            "Hant" -> Variant.HANT
            "Hans" -> Variant.HANS
            else -> if (locale.country in setOf("TW", "HK", "MO")) Variant.HANT else Variant.HANS
        }
    }

    private fun t(en: String, hans: String, hant: String, locale: Locale = Locale.getDefault()): String =
        when (variantFor(locale)) {
            Variant.EN -> en
            Variant.HANS -> hans
            Variant.HANT -> hant
        }

    fun driveError(error: DriveSyncError, locale: Locale = Locale.getDefault()): String = when (error) {
        DriveSyncError.AUTH_REQUIRED -> t(
            "Google sign-in expired. Sign in again to keep syncing.",
            "Google 登录已失效，请重新登录以继续同步。",
            "Google 登入已失效，請重新登入以繼續同步。",
            locale,
        )
        DriveSyncError.CANCELLED -> t("Sign-in was cancelled.", "已取消登录。", "已取消登入。", locale)
        DriveSyncError.NETWORK -> t(
            "Couldn't reach Google Drive. Check your connection and try again.",
            "无法连接 Google 云端硬盘，请检查网络后重试。",
            "無法連線 Google 雲端硬碟，請檢查網路後重試。",
            locale,
        )
        DriveSyncError.CORRUPT -> t(
            "The sync data in Google Drive is damaged. It will be rebuilt on the next sync.",
            "Google 云端硬盘中的同步数据已损坏，将在下次同步时重建。",
            "Google 雲端硬碟中的同步資料已損毀，將在下次同步時重建。",
            locale,
        )
        DriveSyncError.UNKNOWN -> t(
            "Sync failed. Please try again later.",
            "同步失败，请稍后重试。",
            "同步失敗，請稍後重試。",
            locale,
        )
    }

    fun summary(pulled: Int, pushed: Int, changed: Int, locale: Locale = Locale.getDefault()): String = t(
        "Pulled $pulled, pushed $pushed, changed $changed",
        "拉取 $pulled，推送 $pushed，变更 $changed",
        "拉取 $pulled，推送 $pushed，變更 $changed",
        locale,
    )

    val librarySync: String
        get() = t("Library sync", "书架同步", "書櫃同步")

    val googleDrive: String
        get() = t("Google Drive", "Google 云端硬盘", "Google 雲端硬碟")

    val googleDriveSummary: String
        get() = t(
            "Sync library, progress and categories through your Google Drive (private app folder)",
            "通过 Google 云端硬盘（应用私有文件夹）同步收藏、进度与分类",
            "透過 Google 雲端硬碟（應用程式私人資料夾）同步收藏、進度與分類",
        )

    val statusSignedIn: String
        get() = t("Signed in", "已登录", "已登入")

    val statusSignedOut: String
        get() = t("Not signed in", "未登录", "未登入")

    val signIn: String
        get() = t("Sign in with Google", "使用 Google 登录", "使用 Google 登入")

    val signingIn: String
        get() = t("Signing in…", "正在登录…", "正在登入…")

    val signOut: String
        get() = t("Sign out", "退出登录", "登出")

    val signOutSummary: String
        get() = t(
            "Stops syncing on this device. Data in Drive is kept.",
            "停止本设备同步，云端数据会保留。",
            "停止本裝置同步，雲端資料會保留。",
        )

    val signInSuccess: String
        get() = t("Signed in to Google Drive", "已登录 Google 云端硬盘", "已登入 Google 雲端硬碟")

    val syncFrequency: String
        get() = t("Sync frequency", "自动同步频率", "自動同步頻率")

    val syncFreqManual: String
        get() = t("Manual only", "仅手动", "僅手動")

    val syncFreq15m: String
        get() = t("Every 15 minutes", "每 15 分钟", "每 15 分鐘")

    val syncFreq1h: String
        get() = t("Every 1 hour", "每 1 小时", "每 1 小時")

    val syncFreq6h: String
        get() = t("Every 6 hours", "每 6 小时", "每 6 小時")

    val syncFreq24h: String
        get() = t("Every 24 hours", "每 24 小时", "每 24 小時")

    val syncOnlyOnWifi: String
        get() = t("Sync only on Wi-Fi", "仅在 Wi-Fi 下同步", "僅在 Wi-Fi 下同步")

    val syncOnlyOnWifiSummary: String
        get() = t("Save mobile data", "避免移动流量消耗", "避免行動數據消耗")

    val syncNow: String
        get() = t("Sync now", "立即同步", "立即同步")

    val syncing: String
        get() = t("Syncing…", "正在同步…", "正在同步…")

    val lastSync: String
        get() = t("Last sync", "最近同步", "最近同步")

    val lastSyncNever: String
        get() = t("Never", "从未", "從未")

    val lastSyncResult: String
        get() = t("Last sync result", "最近同步结果", "最近同步結果")

    val disabled: String
        get() = t("Off", "未开启", "未開啟")

    val localMangaNotice: String
        get() = t(
            "Note: Local manga entries do not have cross-device identities and are excluded.",
            "说明：本地漫画（Local source）天然无跨端全局身份，不参与同步。",
            "說明：本機漫畫（Local source）沒有跨裝置的全域身分，不參與同步。",
        )
}
