package mihon.desktop.activity

import kotlinx.coroutines.launch
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.update.LibraryUpdateReport

fun missingSourceActivityId(sourceId: Long) = "missing-source-$sourceId"

/** Manga titles per source id whose extension is not installed, in report order. */
fun missingSourceGroups(report: LibraryUpdateReport): Map<Long, List<String>> = report.results
    .filter { it.missingSourceId != null }
    .groupBy({ it.missingSourceId!! }, { it.title })

/**
 * Activity-center entry for a source whose extension is not installed. Never installs on its own:
 * [onInstall] (only when the extension is available in a configured repo) and [onMigrate] run on click.
 */
fun missingSourceActivity(
    sourceId: Long,
    sourceName: String?,
    mangaTitles: List<String>,
    strings: DesktopStrings,
    onInstall: (() -> Unit)?,
    onMigrate: () -> Unit,
): ActivityItem = ActivityItem(
    id = missingSourceActivityId(sourceId),
    kind = ActivityKind.EXTENSION_INSTALL,
    state = ActivityState.FAILED,
    subject = sourceName?.takeIf { it.isNotBlank() } ?: sourceId.toString(),
    failedCount = mangaTitles.size,
    failureReason = strings.text(UiText.MissingSourceReason, mangaTitles.joinToString("、")),
    actions = listOfNotNull(
        onInstall?.let { ActivityAction("install", strings.text(UiText.ActivityInstallExtension), it) },
        ActivityAction("migrate", strings.text(UiText.ActivityMigrateSource), onMigrate),
    ),
)

/**
 * Turns "source extension not installed" library-update failures into activity-center entries with
 * one-click "install extension" (when a configured repo has it) and "migrate" actions. Nothing is
 * installed until the user clicks; the click is routed to the Browse tab through [requests].
 */
object MissingSourceAlerts {
    sealed interface Request {
        data class Install(val item: mihon.desktop.extension.ExtensionStoreItem) : Request
        data class Migrate(val sourceId: Long, val sourceName: String, val mangaCount: Int) : Request
    }

    private val _requests = kotlinx.coroutines.flow.MutableSharedFlow<Request>(extraBufferCapacity = 4)
    val requests: kotlinx.coroutines.flow.SharedFlow<Request> = _requests
    private val published = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun publish(
        report: LibraryUpdateReport,
        center: ActivityCenter,
        scope: kotlinx.coroutines.CoroutineScope,
        strings: () -> DesktopStrings,
        findInRepos: suspend (Long) -> mihon.desktop.extension.ExtensionStoreItem?,
    ) {
        val groups = missingSourceGroups(report)
        // Clear entries for sources that no longer fail (extension installed or manga migrated).
        published.filter { it !in groups.keys }
            .forEach { id ->
                published.remove(id)
                center.remove(missingSourceActivityId(id))
            }
        groups.forEach { (sourceId, titles) ->
            scope.launch {
                val match = runCatching { findInRepos(sourceId) }.getOrNull()
                val name = match?.sources?.firstOrNull { it.id == sourceId }?.name ?: match?.name
                val item = missingSourceActivity(
                    sourceId = sourceId,
                    sourceName = name,
                    mangaTitles = titles,
                    strings = strings(),
                    onInstall = match?.let { { _requests.tryEmit(Request.Install(it)) } },
                    onMigrate = {
                        _requests.tryEmit(Request.Migrate(sourceId, name ?: sourceId.toString(), titles.size))
                    },
                )
                published.add(sourceId)
                center.publish(item)
            }
        }
    }
}
