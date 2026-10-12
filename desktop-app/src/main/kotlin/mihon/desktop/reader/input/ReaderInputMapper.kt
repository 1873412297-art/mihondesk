package mihon.desktop.reader.input

import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import kotlin.math.abs

enum class ReaderInputKey {
    LEFT,
    RIGHT,
    A,
    D,
    SPACE,
    PAGE_UP,
    PAGE_DOWN,
    HOME,
    END,
    PLUS,
    MINUS,
    ZERO,
    F,
    F11,
    B,
    ESCAPE,
    X,
    UP,
    DOWN,
    N,
    P,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    M,
    V,
}

/**
 * Every rebindable reader keyboard action. Physical keys are mapped to these through the persisted
 * key map (see `mihon.desktop.ui.shortcut.DesktopKeyBindings`), then [ReaderInputMapper.mapAction]
 * turns them into commands for the current reading context.
 */
enum class ReaderShortcutAction {
    /** Left arrow semantics: previous page in left-to-right modes, next page in right-to-left ones. */
    PAGE_LEFT,

    /** Right arrow semantics: next page in left-to-right modes, previous page in right-to-left ones. */
    PAGE_RIGHT,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    FIRST_PAGE,
    LAST_PAGE,
    ZOOM_IN,
    ZOOM_OUT,
    RESET_ZOOM,
    FULLSCREEN,
    BORDERLESS,
    EXIT,
    PAGE_ACTIONS,
    PREVIOUS_CHAPTER,
    NEXT_CHAPTER,
    TOGGLE_BOOKMARK,
    CYCLE_READING_MODE,
    SHOW_HELP,
}

enum class ReaderInputFocus { READER, TEXT, MENU }
enum class ReaderSideButton { BACK, FORWARD }
data class InputPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "input point must be finite" }
    }
}

data class ReaderInputContext(
    val mode: ReadingMode,
    val wheelBehavior: ReaderWheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION,
    val focus: ReaderInputFocus = ReaderInputFocus.READER,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val pageCount: Int = 1,
    val anchor: ReaderPosition = ReaderPosition(0),
) {
    init {
        require(pageCount > 0) { "page count must be positive" }
    }
}

sealed interface ReaderInputCommand {
    data class Core(val action: ReaderAction) : ReaderInputCommand
    data class ZoomBy(val factor: Float, val centroid: InputPoint) : ReaderInputCommand
    data object PageActions : ReaderInputCommand
    data object Fullscreen : ReaderInputCommand
    data object Borderless : ReaderInputCommand
    data object Escape : ReaderInputCommand
    data object PreviousChapter : ReaderInputCommand
    data object NextChapter : ReaderInputCommand
    data object ToggleBookmark : ReaderInputCommand
    data object CycleReadingMode : ReaderInputCommand
    data object ShowHelp : ReaderInputCommand
}

/** The reading mode the reader switches to when the mode key (V) is pressed: the next one, wrapping around. */
fun ReadingMode.nextForShortcut(): ReadingMode = ReadingMode.entries[(ordinal + 1) % ReadingMode.entries.size]

data class ReaderInputResult(val action: ReaderInputCommand?) {
    val consumed get() = action != null
}

/** Desktop-only translation layer; Compose/AWT events are adapted at the UI edge. */
class ReaderInputMapper(private val nowMillis: () -> Long = System::currentTimeMillis) {
    private var wheelPixels = 0f
    private var lastPagedWheelAt = Long.MIN_VALUE

    fun mapKey(key: ReaderInputKey, context: ReaderInputContext): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER || context.ctrl) return ReaderInputResult(null)
        return mapAction(key.toShortcutAction(context.shift), context)
    }

    /** Translates a (possibly user-rebound) reader action into a command for the current context. */
    fun mapAction(action: ReaderShortcutAction, context: ReaderInputContext): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER) return ReaderInputResult(null)
        val previous = ReaderInputCommand.Core(ReaderAction.Previous)
        val next = ReaderInputCommand.Core(ReaderAction.Next)
        val command = when (action) {
            ReaderShortcutAction.PAGE_LEFT -> if (context.mode.isRightToLeft) next else previous
            ReaderShortcutAction.PAGE_RIGHT -> if (context.mode.isRightToLeft) previous else next
            ReaderShortcutAction.PREVIOUS_PAGE -> previous
            ReaderShortcutAction.NEXT_PAGE -> next
            ReaderShortcutAction.FIRST_PAGE -> ReaderInputCommand.Core(ReaderAction.SelectPage(0))
            ReaderShortcutAction.LAST_PAGE -> ReaderInputCommand.Core(ReaderAction.SelectPage(context.pageCount - 1))
            ReaderShortcutAction.ZOOM_IN -> ReaderInputCommand.ZoomBy(1.1f, CENTER)
            ReaderShortcutAction.ZOOM_OUT -> ReaderInputCommand.ZoomBy(1f / 1.1f, CENTER)
            ReaderShortcutAction.RESET_ZOOM -> ReaderInputCommand.ZoomBy(0f, CENTER)
            ReaderShortcutAction.FULLSCREEN -> ReaderInputCommand.Fullscreen
            ReaderShortcutAction.BORDERLESS -> ReaderInputCommand.Borderless
            ReaderShortcutAction.EXIT -> ReaderInputCommand.Escape
            ReaderShortcutAction.PAGE_ACTIONS -> ReaderInputCommand.PageActions
            ReaderShortcutAction.PREVIOUS_CHAPTER -> ReaderInputCommand.PreviousChapter
            ReaderShortcutAction.NEXT_CHAPTER -> ReaderInputCommand.NextChapter
            ReaderShortcutAction.TOGGLE_BOOKMARK -> ReaderInputCommand.ToggleBookmark
            ReaderShortcutAction.CYCLE_READING_MODE -> ReaderInputCommand.CycleReadingMode
            ReaderShortcutAction.SHOW_HELP -> ReaderInputCommand.ShowHelp
        }
        return ReaderInputResult(command)
    }

    fun mapWheel(
        deltaPixels: Float,
        context: ReaderInputContext,
        ctrl: Boolean = false,
        centroid: InputPoint = CENTER,
    ): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER || deltaPixels == 0f) return ReaderInputResult(null)
        if (ctrl) {
            return ReaderInputResult(
                ReaderInputCommand.ZoomBy(
                    if (deltaPixels >
                        0
                    ) {
                        1.1f
                    } else {
                        1f / 1.1f
                    },
                    centroid,
                ),
            )
        }
        if (context.mode == ReadingMode.VERTICAL || context.mode == ReadingMode.WEBTOON ||
            context.wheelBehavior == ReaderWheelBehavior.SCROLL
        ) {
            val offset = (context.anchor.offsetPixels + deltaPixels.toInt()).coerceAtLeast(0)
            return ReaderInputResult(
                ReaderInputCommand.Core(ReaderAction.SetViewportAnchor(context.anchor.copy(offsetPixels = offset))),
            )
        }
        wheelPixels += deltaPixels
        if (
            abs(wheelPixels) < PAGE_WHEEL_THRESHOLD ||
            (lastPagedWheelAt != Long.MIN_VALUE && nowMillis() - lastPagedWheelAt < PAGE_WHEEL_RATE_LIMIT_MILLIS)
        ) {
            return ReaderInputResult(null)
        }
        val forward = wheelPixels > 0f
        wheelPixels = 0f
        lastPagedWheelAt = nowMillis()
        return ReaderInputResult(ReaderInputCommand.Core(if (forward) ReaderAction.Next else ReaderAction.Previous))
    }

    fun mapSideButton(button: ReaderSideButton, context: ReaderInputContext): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER) return ReaderInputResult(null)
        val forward = button == ReaderSideButton.FORWARD
        val action = when {
            !context.mode.isRightToLeft -> if (forward) ReaderAction.Next else ReaderAction.Previous
            forward -> ReaderAction.Previous
            else -> ReaderAction.Next
        }
        return ReaderInputResult(ReaderInputCommand.Core(action))
    }

    fun mapPinch(scale: Float, centroid: InputPoint): ReaderInputResult {
        require(scale.isFinite() && scale > 0f) { "pinch scale must be positive and finite" }
        return ReaderInputResult(ReaderInputCommand.ZoomBy(scale, centroid))
    }

    private fun ReaderInputKey.toShortcutAction(shift: Boolean): ReaderShortcutAction = when (this) {
        ReaderInputKey.LEFT, ReaderInputKey.A -> ReaderShortcutAction.PAGE_LEFT
        ReaderInputKey.RIGHT, ReaderInputKey.D -> ReaderShortcutAction.PAGE_RIGHT
        ReaderInputKey.SPACE -> if (shift) ReaderShortcutAction.PREVIOUS_PAGE else ReaderShortcutAction.NEXT_PAGE
        ReaderInputKey.PAGE_UP, ReaderInputKey.UP -> ReaderShortcutAction.PREVIOUS_PAGE
        ReaderInputKey.PAGE_DOWN, ReaderInputKey.DOWN -> ReaderShortcutAction.NEXT_PAGE
        ReaderInputKey.HOME -> ReaderShortcutAction.FIRST_PAGE
        ReaderInputKey.END -> ReaderShortcutAction.LAST_PAGE
        ReaderInputKey.PLUS -> ReaderShortcutAction.ZOOM_IN
        ReaderInputKey.MINUS -> ReaderShortcutAction.ZOOM_OUT
        ReaderInputKey.ZERO -> ReaderShortcutAction.RESET_ZOOM
        ReaderInputKey.F, ReaderInputKey.F11 -> ReaderShortcutAction.FULLSCREEN
        ReaderInputKey.B -> ReaderShortcutAction.BORDERLESS
        ReaderInputKey.ESCAPE -> ReaderShortcutAction.EXIT
        ReaderInputKey.X -> ReaderShortcutAction.PAGE_ACTIONS
        ReaderInputKey.P, ReaderInputKey.LEFT_BRACKET -> ReaderShortcutAction.PREVIOUS_CHAPTER
        ReaderInputKey.N, ReaderInputKey.RIGHT_BRACKET -> ReaderShortcutAction.NEXT_CHAPTER
        ReaderInputKey.M -> ReaderShortcutAction.TOGGLE_BOOKMARK
        ReaderInputKey.V -> ReaderShortcutAction.CYCLE_READING_MODE
    }

    private companion object {
        val CENTER = InputPoint(.5f, .5f)
        const val PAGE_WHEEL_THRESHOLD = 1f
        const val PAGE_WHEEL_RATE_LIMIT_MILLIS = 150L
    }
}
