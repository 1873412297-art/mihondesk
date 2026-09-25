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
        get() = if (isZh) "自动或手动同步书架状态到桌面端" else "Sync library state automatically or manually with desktop"

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

    val pairingCode: String
        get() = if (isZh) "配对码" else "Pairing code"

    val pairingCodeSummary: String
        get() = if (isZh) "粘贴桌面端生成的配对码 (mihonsync://...)" else "Paste pairing code from desktop (mihonsync://...)"

    val pairingCodeInvalid: String
        get() = if (isZh) "配对码格式无效" else "Invalid pairing code"

    val testConnection: String
        get() = if (isZh) "测试连接" else "Test connection"

    val testingConnection: String
        get() = if (isZh) "正在测试连接…" else "Testing connection…"

    val connectionSuccess: String
        get() = if (isZh) "连接成功" else "Connection successful"

    val connectionFailed: String
        get() = if (isZh) "连接失败" else "Connection failed"

    val pairingExpired: String
        get() = if (isZh) "配对已失效，请在桌面端重新获取配对码" else "Pairing expired, please re-pair from desktop"

    val scanPairing: String
        get() = if (isZh) "扫码配对" else "Scan QR code to pair"

    val scanPairingHint: String
        get() = if (isZh) "对准桌面端二维码以完成配对" else "Point at the desktop QR code to pair"

    val scanPairingInvalidCode: String
        get() = if (isZh) "不是有效的 Mihon 同步二维码" else "Not a valid Mihon sync QR code"

    val scanPairingSuccess: String
        get() = if (isZh) "配对成功" else "Pairing successful"

    val scanPairingCameraPermissionDenied: String
        get() = if (isZh) {
            "需要相机权限才能扫码。请前往系统设置开启。"
        } else {
            "Camera permission required. Please enable it in system Settings."
        }

    val scanPairingManualInput: String
        get() = if (isZh) "手动输入配对码" else "Enter code manually"

    val scanPairingManualHint: String
        get() = if (isZh) "粘贴 mihonsync:// 开头的配对码" else "Paste the mihonsync:// pairing code"

    val discoverDevices: String
        get() = if (isZh) "搜索局域网设备" else "Search LAN devices"

    val discoverDevicesSearching: String
        get() = if (isZh) "正在搜索…" else "Searching…"

    val discoverDevicesRefresh: String
        get() = if (isZh) "重新搜索" else "Refresh"

    val discoverDevicesEmpty: String
        get() = if (isZh) "未发现设备，请确保桌面端已开启同步服务器" else "No devices found. Make sure the desktop sync server is running."

    val discoverDevicesPairSuccess: String
        get() = if (isZh) "已配对到 %s" else "Paired with %s"

    val discoverDevicesNoQuickPair: String
        get() = if (isZh) "该设备未开启免输入配对，请扫码或粘贴配对码" else "Quick pairing not enabled. Scan QR or paste pairing code."

    val cancel: String
        get() = if (isZh) "取消" else "Cancel"

    val confirmPairingTitle: String
        get() = if (isZh) "确认同步配对" else "Confirm sync pairing"

    val confirmPairingTarget: String
        get() = if (isZh) "目标地址" else "Target address"

    val confirmPairingButton: String
        get() = if (isZh) "确认配对" else "Confirm pairing"

    val confirmPairingMessage: String
        get() = if (isZh) "是否将同步服务器连接至该地址？" else "Connect sync server to this address?"
}
