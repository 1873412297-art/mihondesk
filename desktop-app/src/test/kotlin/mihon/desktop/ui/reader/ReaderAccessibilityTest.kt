package mihon.desktop.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderAccessibilityTest {
    @Test
    fun `page scrubber announces a label and the current page instead of a raw value`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ReaderChapterNavigator(
                    currentPage = 3,
                    totalPages = 12,
                    isRtl = false,
                    onPageSelected = {},
                    onPreviousChapter = {},
                    onNextChapter = {},
                    hasPreviousChapter = true,
                    hasNextChapter = true,
                )
            }
        }

        onNodeWithTag("reader-scrubber-slider", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Page")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 3 of 12"))
    }
}
