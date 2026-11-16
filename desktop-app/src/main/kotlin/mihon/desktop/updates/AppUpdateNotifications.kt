package mihon.desktop.updates

import kotlinx.coroutines.CoroutineScope
import mihon.desktop.activity.ActivityAction
import mihon.desktop.activity.ActivityCenter
import mihon.desktop.activity.ActivityItem
import mihon.desktop.activity.ActivityKind
import mihon.desktop.activity.ActivityState
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.notification.LibraryUpdateAlerts
import mihon.desktop.notification.LibraryUpdateTarget
import mihon.desktop.platform.DesktopPlatform
import mihon.desktop.preferences.DesktopPreferenceStore

internal const val APP_UPDATE_ACTIVITY_ID = "app-update"

/**
 * Activity-center entry for a new release; its action opens the update card in Settings > About.
 * A release that was already downloaded says it is ready to install.
 */
internal fun appUpdateActivity(
    discovery: AppUpdateDiscovery,
    strings: DesktopStrings,
    onView: () -> Unit,
): ActivityItem {
    val release = discovery.update
    val detail = if (discovery.download != null) UiText.AppUpdateDownloadedDetail else UiText.AppUpdateAvailableDetail
    return ActivityItem(
        id = APP_UPDATE_ACTIVITY_ID,
        kind = ActivityKind.APP_UPDATE,
        state = ActivityState.SUCCEEDED,
        subject = "MihonDesk ${release.release.version}",
        detail = strings.text(detail, release.release.version, release.currentVersion),
        actions = listOf(ActivityAction("view", strings.text(UiText.AppUpdateView), onView)),
    )
}

/**
 * The background release check of the running app: a system notification plus an activity-center
 * entry, both leading to the update card. With automatic downloads on, the package is fetched first
 * and the notification offers to install it. Null where no release package exists for this platform
 * (releases publish Windows packages only) or when no UI is launched.
 */
internal fun createAppUpdateWatcher(
    launchUi: Boolean,
    service: DesktopAppUpdateService,
    preferences: DesktopPreferenceStore,
    alerts: LibraryUpdateAlerts,
    activityCenter: ActivityCenter,
    scope: CoroutineScope,
): AppUpdateWatcher? {
    if (!launchUi || !DesktopPlatform.isWindows) return null
    return AppUpdateWatcher(
        check = service::checkForUpdates,
        preferences = preferences,
        scope = scope,
        onNewRelease = { discovery ->
            val strings = DesktopStrings.resolve(preferences.load().language)
            val downloaded = discovery.download != null
            alerts.onAppUpdate(
                title = strings.text(
                    if (downloaded) UiText.AppUpdateDownloadedTitle else UiText.AppUpdateNotificationTitle,
                ),
                message = strings.text(
                    if (downloaded) UiText.AppUpdateDownloadedMessage else UiText.AppUpdateNotificationMessage,
                    discovery.update.release.version,
                ),
            )
            activityCenter.publish(
                appUpdateActivity(discovery, strings) { alerts.open(LibraryUpdateTarget.AppUpdate) },
            )
        },
        download = { update -> downloadAppUpdateInBackground(service, update, appUpdateDownloadDirectory()) },
    )
}
