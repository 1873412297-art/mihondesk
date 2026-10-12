package mihon.desktop.ui

import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test

class DesktopTrayFontTest {
    @Test
    fun `ascii tray labels keep the default font`() {
        DesktopTray.trayMenuFont("Show mihondesk" + "Quit").shouldBeNull()
    }

    @Test
    fun `cjk tray labels get a font that can display them when one exists`() {
        val text = "\u663e\u793a mihondesk"
        DesktopTray.trayMenuFont(text)?.let { check(it.canDisplayUpTo(text) == -1) }
    }
}
