package mihon.desktop.ui.browse

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * Browse tests point the extension repository at a dead local port. Its fetch fails
 * asynchronously (about 2 s for a refused connect on Windows, longer under load) and then inserts
 * the error banner above the source list. A click sent before that lands while the rows shift
 * down, misses the source and the test waits forever for the catalogue. Opening the source only
 * after the refresh has settled (banner present) removes the race instead of padding timeouts.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openSourceWhenSettled(sourceId: Long, timeoutMillis: Long = 60_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithTag("browse-error-retry").fetchSemanticsNodes().isNotEmpty() &&
            onAllNodesWithTag("source-item-$sourceId").fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
    onNodeWithTag("source-item-$sourceId").performClick()
}
