package mihon.desktop.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.update.LibraryUpdateReport

/** Where clicking a library-update notification takes the user. */
sealed interface LibraryUpdateTarget {
    data class Chapter(val chapterId: Long, val mangaId: Long) : LibraryUpdateTarget
    data object Updates : LibraryUpdateTarget
}

data class LibraryUpdateNotice(
    val title: String,
    val message: String,
    val target: LibraryUpdateTarget,
    val newChapterCount: Int,
)

/**
 * The system notification for a finished library update, or null when nothing new arrived.
 * One updated manga opens its earliest new chapter; several open the Updates feed.
 */
fun libraryUpdateNotice(
    report: LibraryUpdateReport,
    strings: DesktopStrings,
    hideContent: Boolean,
): LibraryUpdateNotice? {
    val updated = report.results.filter { it.newChapters.isNotEmpty() }
    val count = updated.sumOf { it.newChapters.size }
    if (count == 0) return null
    val title = strings.text(UiText.NotificationLibrary)
    val single = updated.singleOrNull()
    val target = if (single != null) {
        val earliest = single.newChapters.minWith(compareBy({ it.chapterNumber }, { it.sourceOrder }, { it.id }))
        LibraryUpdateTarget.Chapter(chapterId = earliest.id, mangaId = single.mangaId)
    } else {
        LibraryUpdateTarget.Updates
    }
    val message = when {
        hideContent -> strings.text(UiText.NotificationLibraryHidden, count)
        single != null -> {
            val earliest = single.newChapters.minWith(compareBy({ it.chapterNumber }, { it.sourceOrder }, { it.id }))
            strings.text(UiText.NotificationLibraryOne, single.title, count, earliest.name)
        }
        else -> strings.text(UiText.NotificationNewChapters, count, updated.size) +
            " · " + updated.take(3).joinToString("、") { it.title }
    }
    return LibraryUpdateNotice(title = title, message = message, target = target, newChapterCount = count)
}

/**
 * Turns update reports into a balloon + tray badge, and remembers what the balloon points at so
 * a click on it (delivered by the tray icon) can open that chapter.
 */
class LibraryUpdateAlerts(
    private val display: (title: String, message: String) -> Unit,
    private val badge: (count: Int) -> Unit,
    private val strings: () -> DesktopStrings,
    private val hideContent: () -> Boolean,
    private val enabled: () -> Boolean,
) {
    private val _unseenCount = MutableStateFlow(0)
    val unseenCount: StateFlow<Int> = _unseenCount.asStateFlow()

    private val _clicks = MutableStateFlow<LibraryUpdateTarget?>(null)

    /** The most recent click target not yet handled by the UI. */
    val clicks: StateFlow<LibraryUpdateTarget?> = _clicks.asStateFlow()

    @Volatile
    private var pendingTarget: LibraryUpdateTarget? = null

    @Synchronized
    fun onReport(report: LibraryUpdateReport) {
        val notice = libraryUpdateNotice(report, strings(), hideContent()) ?: return
        _unseenCount.value += notice.newChapterCount
        badge(_unseenCount.value)
        pendingTarget = notice.target
        if (enabled()) display(notice.title, notice.message)
    }

    /** Called by the tray when the balloon is clicked: hands the target to the UI once. */
    @Synchronized
    fun consumeClick(): LibraryUpdateTarget? {
        val target = pendingTarget ?: return null
        pendingTarget = null
        _clicks.value = target
        return target
    }

    fun clickHandled() {
        _clicks.value = null
    }

    @Synchronized
    fun markSeen() {
        if (_unseenCount.value == 0) return
        _unseenCount.value = 0
        badge(0)
    }
}
