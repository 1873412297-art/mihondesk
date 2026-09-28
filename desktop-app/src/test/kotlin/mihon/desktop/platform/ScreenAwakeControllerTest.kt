package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScreenAwakeControllerTest {

    @Test
    fun `first acquire requests display required and final release restores continuous only`() {
        val calls = mutableListOf<Int>()
        val controller = ScreenAwakeController(setExecutionState = { flags ->
            calls += flags
            true
        })

        controller.isDisplayKeptOn shouldBe false
        controller.acquireDisplay()
        controller.isDisplayKeptOn shouldBe true
        calls shouldBe listOf(
            DesktopScreenAwake.ES_CONTINUOUS or DesktopScreenAwake.ES_DISPLAY_REQUIRED,
        )

        controller.releaseDisplay()
        controller.isDisplayKeptOn shouldBe false
        calls shouldBe listOf(
            DesktopScreenAwake.ES_CONTINUOUS or DesktopScreenAwake.ES_DISPLAY_REQUIRED,
            DesktopScreenAwake.ES_CONTINUOUS,
        )
    }

    @Test
    fun `nested holders only touch the native API on the outermost transitions`() {
        val calls = mutableListOf<Int>()
        val controller = ScreenAwakeController(setExecutionState = { flags ->
            calls += flags
            true
        })

        controller.acquireDisplay()
        controller.acquireDisplay()
        controller.acquireDisplay()
        calls.size shouldBe 1

        controller.releaseDisplay()
        controller.isDisplayKeptOn shouldBe true
        calls.size shouldBe 1

        controller.releaseDisplay()
        controller.releaseDisplay()
        controller.isDisplayKeptOn shouldBe false
        calls shouldBe listOf(
            DesktopScreenAwake.ES_CONTINUOUS or DesktopScreenAwake.ES_DISPLAY_REQUIRED,
            DesktopScreenAwake.ES_CONTINUOUS,
        )
    }

    @Test
    fun `release without acquire is a no-op`() {
        val calls = mutableListOf<Int>()
        val controller = ScreenAwakeController(setExecutionState = { flags ->
            calls += flags
            true
        })

        controller.releaseDisplay()
        controller.isDisplayKeptOn shouldBe false
        calls shouldBe emptyList()
    }
}
