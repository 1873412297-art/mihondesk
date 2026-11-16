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

/** Activity-center entry for a new release; its action opens the update card in Settings > About. */
internal fun appUpdateActivity(
    release: UpdateCheckResult.UpdateAvailable,
    strings: DesktopStrings,
    onView: () -> Unit,
): ActivityItem = ActivityItem(
    id = APP_UPDATE_ACTIVITY_ID,
    kind = ActivityKind.APP_UPDATE,
    state = ActivityState.SUCCEEDED,
    subject = "MihonDesk ${release.release.version}",
    detail = strings.text(UiText.AppUpdateAvailableDetail, release.release.version, release.currentVersion),
    actions = listOf(ActivityAction("view", strings.text(UiText.AppUpdateView), onView)),
)

/**
 * The background release check of the running app: a system notification plus an activity-center
 * entry, both leading to the update card. Null where no release package exists for this platform
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
        onNewRelease = { release ->
            val strings = DesktopStrings.resolve(preferences.load().language)
            alerts.onAppUpdate(
                title = strings.text(UiText.AppUpdateNotificationTitle),
                message = strings.text(UiText.AppUpdateNotificationMessage, release.release.version),
            )
            activityCenter.publish(
                appUpdateActivity(release, strings) { alerts.open(LibraryUpdateTarget.AppUpdate) },
            )
        },
    )
}
