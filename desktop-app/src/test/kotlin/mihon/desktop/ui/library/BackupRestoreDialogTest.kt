package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.backup.BackupImportProgress
import mihon.desktop.library.backup.BackupImportStage
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class BackupRestoreDialogTest {
    @ParameterizedTest
    @CsvSource(
        "1024,English,false",
        "480,English,true",
        "1024,SimplifiedChinese,true",
        "480,SimplifiedChinese,false",
        "1024,TraditionalChinese,false",
        "480,TraditionalChinese,true",
    )
    fun `progress cancel and final commit are localized with safe actions`(
        width: Int,
        language: AppLanguage,
        dark: Boolean,
    ) = runSkikoComposeUiTest(size = Size(width.toFloat(), 650f)) {
        System.getenv("MIHON_RESTORE_APP")?.let { directory ->
            val origin = Path.of(
                Class.forName(
                    "mihon.desktop.ui.library.BackupRestoreDialogKt",
                ).protectionDomain.codeSource.location.toURI(),
            )
            check(origin.startsWith(Path.of(directory))) { "Expected packaged restore UI: $origin" }
            println("PACKAGED_RESTORE_UI $origin")
        }
        val progress = BackupImportProgress(BackupImportStage.RESTORING, 123, 456)
        val state = mutableStateOf<BackupRestoreState>(BackupRestoreState.Running(progress))
        var cancellations = 0
        val strings = DesktopStrings.resolve(language)
        setContent {
            ProvideDesktopStrings(language) {
                MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, isAmoled = dark) {
                    Box(Modifier.requiredSize(width.dp, 650.dp)) {
                        BackupRestoreDialog(state.value, {
                            cancellations++
                            state.value = BackupRestoreState.Running(progress, cancelling = true)
                        }, { state.value = BackupRestoreState.Idle })
                    }
                }
            }
        }
        onNodeWithText(strings.text(UiText.RestoreManga, 123, 456)).assertIsDisplayed()
        onNodeWithTag("backup-restore-cancel").assertIsDisplayed()
        assertTrue(onNodeWithTag("backup-restore-dialog").fetchSemanticsNode().boundsInRoot.width <= width)
        System.getenv("MIHON_RESTORE_EVIDENCE")?.let { directory ->
            val target = Files.createDirectories(Path.of(directory))
            Image.makeFromBitmap(onNodeWithTag("backup-restore-dialog").captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png ->
                    Files.write(target.resolve("restore-$width-$language.png"), png.bytes)
                }
            }
        }
        onNodeWithTag("backup-restore-cancel").performClick()
        onNodeWithText(strings.text(UiText.RestoreCancelling)).assertIsDisplayed()
        onNodeWithTag("backup-restore-cancel").assertIsNotEnabled()
        assertEquals(1, cancellations)
        runOnIdle {
            state.value =
                BackupRestoreState.Running(BackupImportProgress(BackupImportStage.COMMITTING, 456, 456))
        }
        onNodeWithText(strings.text(UiText.RestoreCommitting)).assertIsDisplayed()
        onNodeWithTag("backup-restore-cancel").assertIsNotEnabled()
        runOnIdle { state.value = BackupRestoreState.Cancelled }
        onNodeWithText(strings.text(UiText.RestoreCancelledMessage)).assertIsDisplayed()
        onNodeWithText(strings.dialogClose).performClick()
        runOnIdle { assertEquals(BackupRestoreState.Idle, state.value) }
    }
}
