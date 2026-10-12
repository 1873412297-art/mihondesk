package mihon.desktop.reader.input

import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import org.junit.jupiter.api.Test

class ReaderInputMapperTest {
    @Test fun `directional keys map every LTR and RTL navigation key`() {
        val mapper = ReaderInputMapper()
        val ltr = ReaderInputContext(ReadingMode.SINGLE_LTR)
        val rtl = ReaderInputContext(ReadingMode.SINGLE_RTL)
        listOf(ReaderInputKey.LEFT, ReaderInputKey.A).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
            mapper.mapKey(key, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        }
        listOf(ReaderInputKey.RIGHT, ReaderInputKey.D).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
            mapper.mapKey(key, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
        }
        mapper.mapKey(ReaderInputKey.PAGE_UP, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapKey(ReaderInputKey.PAGE_DOWN, rtl).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapKey(ReaderInputKey.HOME, ltr).action shouldBe ReaderInputCommand.Core(ReaderAction.SelectPage(0))
        mapper.mapKey(ReaderInputKey.END, ReaderInputContext(ReadingMode.SINGLE_LTR, pageCount = 8)).action shouldBe
            ReaderInputCommand.Core(ReaderAction.SelectPage(7))
    }

    @Test fun `modifiers and focused controls take precedence and mapped events alone consume`() {
        val mapper = ReaderInputMapper()
        mapper.mapKey(
            ReaderInputKey.RIGHT,
            ReaderInputContext(ReadingMode.SINGLE_LTR, focus = ReaderInputFocus.TEXT),
        ).consumed shouldBe
            false
        mapper.mapKey(ReaderInputKey.RIGHT, ReaderInputContext(ReadingMode.SINGLE_LTR, ctrl = true)).consumed shouldBe
            false
        mapper.mapKey(ReaderInputKey.F, ReaderInputContext(ReadingMode.SINGLE_LTR, ctrl = true)).consumed shouldBe false
        mapper.mapKey(ReaderInputKey.F, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Fullscreen
        mapper.mapKey(ReaderInputKey.B, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Borderless
        mapper.mapKey(ReaderInputKey.ESCAPE, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Escape
        mapper.mapKey(ReaderInputKey.F11, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.Fullscreen
        mapper.mapKey(ReaderInputKey.X, ReaderInputContext(ReadingMode.SINGLE_LTR)).action shouldBe
            ReaderInputCommand.PageActions
        mapper.mapKey(
            ReaderInputKey.SPACE,
            ReaderInputContext(ReadingMode.SINGLE_LTR, focus = ReaderInputFocus.MENU),
        ).consumed shouldBe false
    }

    @Test fun `default reader keys cover arrows, chapter jumps, bookmark and mode switch`() {
        val mapper = ReaderInputMapper()
        val ltr = ReaderInputContext(ReadingMode.SINGLE_LTR)
        val rtl = ReaderInputContext(ReadingMode.SINGLE_RTL)
        listOf(ltr, rtl, ReaderInputContext(ReadingMode.WEBTOON)).forEach { context ->
            mapper.mapKey(ReaderInputKey.UP, context).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
            mapper.mapKey(ReaderInputKey.DOWN, context).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        }
        listOf(ReaderInputKey.P, ReaderInputKey.LEFT_BRACKET).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.PreviousChapter
            mapper.mapKey(key, rtl).action shouldBe ReaderInputCommand.PreviousChapter
        }
        listOf(ReaderInputKey.N, ReaderInputKey.RIGHT_BRACKET).forEach { key ->
            mapper.mapKey(key, ltr).action shouldBe ReaderInputCommand.NextChapter
        }
        mapper.mapKey(ReaderInputKey.M, ltr).action shouldBe ReaderInputCommand.ToggleBookmark
        mapper.mapKey(ReaderInputKey.V, ltr).action shouldBe ReaderInputCommand.CycleReadingMode
        mapper.mapKey(ReaderInputKey.N, ltr.copy(ctrl = true)).consumed shouldBe false
        mapper.mapKey(ReaderInputKey.M, ltr.copy(focus = ReaderInputFocus.TEXT)).consumed shouldBe false
    }

    @Test fun `mode shortcut visits every reading mode and wraps around`() {
        val visited = generateSequence(ReadingMode.SINGLE_LTR) { it.nextForShortcut() }
            .take(ReadingMode.entries.size + 1)
            .toList()
        visited.dropLast(1).toSet() shouldBe ReadingMode.entries.toSet()
        visited.last() shouldBe ReadingMode.SINGLE_LTR
    }

    @Test fun `space navigation follows desktop convention while preserving explicit page keys`() {
        val mapper = ReaderInputMapper()
        val ltr = ReaderInputContext(ReadingMode.SINGLE_LTR)

        mapper.mapKey(ReaderInputKey.SPACE, ltr).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapKey(ReaderInputKey.SPACE, ltr.copy(shift = true)).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapKey(ReaderInputKey.PAGE_UP, ltr.copy(shift = true)).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapKey(ReaderInputKey.PAGE_DOWN, ltr.copy(shift = true)).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
    }

    @Test fun `wheel uses zoom then accumulates touchpad units and rate limits page changes`() {
        val mapper = ReaderInputMapper(nowMillis = { now })
        val paged = ReaderInputContext(ReadingMode.SINGLE_LTR, wheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION)
        mapper.mapWheel(40f, paged, ctrl = true, centroid = InputPoint(.4f, .6f)).action shouldBe
            ReaderInputCommand.ZoomBy(1.1f, InputPoint(.4f, .6f))
        mapper.mapWheel(.4f, paged).consumed shouldBe false
        mapper.mapWheel(.6f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapWheel(1f, paged).consumed shouldBe false
        now += 200
        mapper.mapWheel(1f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
    }

    @Test fun `one desktop wheel notch flips exactly one paged image`() {
        val mapper = ReaderInputMapper(nowMillis = { now })
        val paged = ReaderInputContext(ReadingMode.SINGLE_LTR, wheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION)

        mapper.mapWheel(1f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Next)
        now += 200
        mapper.mapWheel(-1f, paged).action shouldBe ReaderInputCommand.Core(ReaderAction.Previous)
    }

    @Test fun `continuous wheel moves the anchor and touchpad pinch preserves centroid`() {
        val mapper = ReaderInputMapper()
        val continuous = ReaderInputContext(
            mode = ReadingMode.VERTICAL,
            wheelBehavior = ReaderWheelBehavior.SCROLL,
            anchor = ReaderPosition(2, 40),
        )
        mapper.mapWheel(32f, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.SetViewportAnchor(ReaderPosition(2, 72)))
        mapper.mapSideButton(ReaderSideButton.BACK, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapSideButton(ReaderSideButton.FORWARD, continuous).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapPinch(1.25f, InputPoint(.2f, .7f)).action shouldBe
            ReaderInputCommand.ZoomBy(1.25f, InputPoint(.2f, .7f))
    }

    @Test fun `mouse side buttons follow visual reading direction`() {
        val mapper = ReaderInputMapper()
        val ltr = ReaderInputContext(ReadingMode.SINGLE_LTR)
        val rtl = ReaderInputContext(ReadingMode.SINGLE_RTL)

        mapper.mapSideButton(ReaderSideButton.BACK, ltr).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
        mapper.mapSideButton(ReaderSideButton.FORWARD, ltr).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapSideButton(ReaderSideButton.BACK, rtl).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Next)
        mapper.mapSideButton(ReaderSideButton.FORWARD, rtl).action shouldBe
            ReaderInputCommand.Core(ReaderAction.Previous)
    }

    private var now = 0L
}
