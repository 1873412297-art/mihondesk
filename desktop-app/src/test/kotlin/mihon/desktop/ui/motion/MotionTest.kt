package mihon.desktop.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MotionTest {

    @Test
    fun `reduced motion turns every shared transition into an instant change`() {
        fadeThrough(reduced = true).targetContentEnter shouldBe EnterTransition.None
        fadeThrough(reduced = true).initialContentExit shouldBe ExitTransition.None
        crossFade(reduced = true).targetContentEnter shouldBe EnterTransition.None
    }

    @Test
    fun `normal motion animates`() {
        fadeThrough(reduced = false).targetContentEnter shouldNotBe EnterTransition.None
        crossFade(reduced = false).initialContentExit shouldNotBe ExitTransition.None
    }

    @Test
    fun `motionTween snaps under reduced motion`() = runComposeUiTest {
        var reducedSpec: Any? = null
        var normalSpec: Any? = null
        setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                reducedSpec = motionTween<Float>(MotionTokens.MEDIUM)
            }
            CompositionLocalProvider(LocalReducedMotion provides false) {
                normalSpec = motionTween<Float>(MotionTokens.MEDIUM)
            }
        }
        reducedSpec.shouldBeInstanceOf<SnapSpec<Float>>()
        normalSpec.shouldBeInstanceOf<TweenSpec<Float>>()
    }
}
