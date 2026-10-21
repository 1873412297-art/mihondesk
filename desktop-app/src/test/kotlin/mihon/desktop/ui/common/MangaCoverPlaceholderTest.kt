package mihon.desktop.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import mihon.desktop.image.DesktopImageLoader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class MangaCoverPlaceholderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `pending covers show a static placeholder instead of a spinner`() = runComposeUiTest {
        val loader = DesktopImageLoader(tempDir)
        setContent {
            // Unreachable host: the request stays pending for the duration of the assertion.
            MangaCover(
                thumbnailUrl = "http://10.255.255.1/cover.jpg",
                mangaId = 7L,
                imageLoader = loader,
                modifier = Modifier.size(120.dp, 180.dp),
            )
        }
        onNodeWithTag("manga-cover-placeholder", useUnmergedTree = true).assertIsDisplayed()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
            .fetchSemanticsNodes().size.let { check(it == 0) { "expected no progress indicator, found $it" } }
    }
}
