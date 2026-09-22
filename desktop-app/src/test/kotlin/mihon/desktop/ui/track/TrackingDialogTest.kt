package mihon.desktop.ui.track

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.track.DesktopTrackRecord
import mihon.desktop.track.TrackerTestFakeTracker
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class TrackingDialogTest {

    @Test
    fun `clicking edit button on bound tracker row opens edit details dialog`() = runComposeUiTest {
        val tracker = TrackerTestFakeTracker(id = 1L, name = "MyAnimeList")
        val track = DesktopTrackRecord(
            id = 1L,
            mangaId = 100L,
            trackerId = 1L,
            remoteId = 1001L,
            title = "Test Manga",
            lastChapterRead = 5.0,
            totalChapters = 20,
        )

        setContent {
            MaterialTheme {
                TrackingDialog(
                    mangaTitle = "Test Manga",
                    trackers = listOf(tracker),
                    currentTracks = listOf(track),
                    onDismiss = {},
                    onSaveTrack = {},
                    onUnbindTrack = {},
                    onSearchTrack = { _, _ -> emptyList() },
                )
            }
        }

        onNodeWithTag("tracker-edit-1").performClick()
        onNodeWithTag("track-chapter-read-input").assertExists()
        onNodeWithTag("track-save-details-button").assertExists()
    }
}
