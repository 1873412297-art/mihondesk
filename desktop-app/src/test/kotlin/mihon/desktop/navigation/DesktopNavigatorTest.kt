package mihon.desktop.navigation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopNavigatorTest {

    @Test
    fun `navigator maintains a true back stack with push and pop`() {
        val navigator = DesktopNavigator(DesktopDestination.Library) {}

        navigator.current shouldBe DesktopDestination.Library
        navigator.canPop shouldBe false

        navigator.navigate(DesktopDestination.Updates)
        navigator.current shouldBe DesktopDestination.Updates
        navigator.canPop shouldBe true

        navigator.navigate(DesktopDestination.History)
        navigator.current shouldBe DesktopDestination.History

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Updates

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Library
        navigator.canPop shouldBe false

        navigator.back() shouldBe false
        navigator.current shouldBe DesktopDestination.Library
    }

    @Test
    fun `replacing route replaces the top of the stack`() {
        val navigator = DesktopNavigator(DesktopDestination.Library) {}

        navigator.navigate(DesktopDestination.Updates)
        navigator.replace(DesktopDestination.History)

        navigator.current shouldBe DesktopDestination.History
        navigator.canPop shouldBe true

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Library
        navigator.canPop shouldBe false
    }

    @Test
    fun `navigating to upcoming pushes it to the stack and back returns to previous route`() {
        val navigator = DesktopNavigator(DesktopDestination.Updates) {}

        navigator.navigate(DesktopDestination.Upcoming)
        navigator.current shouldBe DesktopDestination.Upcoming
        navigator.currentDestination shouldBe DesktopDestination.Updates
        navigator.canPop shouldBe true

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Updates
        navigator.currentDestination shouldBe DesktopDestination.Updates
    }

    @Test
    fun `registered back handlers take precedence over back stack pop`() {
        val navigator = DesktopNavigator(DesktopDestination.Library) {}
        navigator.navigate(DesktopDestination.Browse)

        var handlerInvoked = false
        val unregister = navigator.registerBackHandler {
            handlerInvoked = true
            true
        }

        navigator.back() shouldBe true
        handlerInvoked shouldBe true
        navigator.current shouldBe DesktopDestination.Browse

        unregister()

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Library
    }

    @Test
    fun `unhandled back handler falls through to stack pop`() {
        val navigator = DesktopNavigator(DesktopDestination.Library) {}
        navigator.navigate(DesktopDestination.Browse)

        val unregister = navigator.registerBackHandler {
            false
        }

        navigator.back() shouldBe true
        navigator.current shouldBe DesktopDestination.Library
        unregister()
    }

    @Test
    fun `navigating to an existing MangaDetails route fires onDestinationChanged`() {
        val fired = mutableListOf<DesktopDestination>()
        val navigator = DesktopNavigator(DesktopDestination.Library) { fired += it }

        navigator.navigate(DesktopDestination.MangaDetails(1L))
        navigator.navigate(DesktopDestination.MangaDetails(2L))
        fired.clear()

        // Re-navigating to a route already in the back stack pops back to it; observers must
        // still hear about the pop (previously neither MangaDetails branch fired the callback).
        navigator.navigate(DesktopDestination.MangaDetails(1L))

        navigator.current shouldBe DesktopDestination.MangaDetails(1L)
        navigator.canPop shouldBe true
        // A transient detail route is not itself a persistable destination, so the callback
        // reports the effective destination now back on top of the stack.
        fired shouldBe listOf(DesktopDestination.Library)
    }
}
