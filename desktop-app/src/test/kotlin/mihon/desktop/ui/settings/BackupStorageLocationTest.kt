package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import org.jetbrains.skia.Image
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class BackupStorageLocationTest {
    @TempDir lateinit var dir: Path

    @ParameterizedTest
    @CsvSource(
        "1024,English,false,Save backup location",
        "480,English,true,Save backup location",
        "1024,SimplifiedChinese,true,保存备份位置",
        "480,SimplifiedChinese,false,保存备份位置",
        "1024,TraditionalChinese,false,儲存備份位置",
        "480,TraditionalChinese,true,儲存備份位置",
    )
    fun `backup folder actions are localized and visible at narrow and desktop widths`(
        width: Int,
        language: AppLanguage,
        dark: Boolean,
        saveLabel: String,
    ) = runComposeUiTest {
        System.getenv("MIHON_BACKUP_APP")?.let { directory ->
            val origin = Path.of(
                Class.forName(
                    "mihon.desktop.ui.settings.BackupStorageLocationKt",
                ).protectionDomain.codeSource.location.toURI(),
            )
            check(origin.startsWith(Path.of(directory))) { "Expected packaged backup UI, got $origin" }
            println("PACKAGED_BACKUP_UI $origin")
        }
        val store = DesktopPreferenceStore(dir.resolve("preferences.properties"))
        setContent {
            ProvideDesktopStrings(language) {
                MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, isAmoled = dark) {
                    Surface(modifier = Modifier.requiredSize(width.dp, 500.dp).testTag("backup-location-render")) {
                        Box(Modifier.padding(24.dp)) {
                            var savedPath by remember { mutableStateOf("") }
                            BackupStorageLocation(store, savedPath) { savedPath = it.backupStoragePath }
                        }
                    }
                }
            }
        }
        onNodeWithTag("backup-storage-input").performTextReplacement(dir.resolve("备份 folder").toString())
        onNodeWithText(saveLabel).assertIsDisplayed()
        onNodeWithTag("backup-storage-choose").assertIsDisplayed()
        onNodeWithTag("backup-storage-default").assertIsDisplayed()
        store.load().backupStoragePath shouldBe ""
        System.getenv("MIHON_BACKUP_EVIDENCE")?.let { location ->
            val target = Files.createDirectories(Path.of(location))
            Image.makeFromBitmap(onNodeWithTag("backup-location-render").captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png ->
                    Files.write(target.resolve("backup-$width-$language.png"), png.bytes)
                }
            }
        }
    }
}
