package mihon.desktop.ui.library

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import org.jetbrains.skia.Image
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class MangaDetailLoadingTest {
    @ParameterizedTest
    @CsvSource("1024,English,Back,false", "480,SimplifiedChinese,返回,false", "1024,TraditionalChinese,返回,true")
    fun `loading offers localized mouse and keyboard exit when navigation is available`(
        width: Int,
        language: AppLanguage,
        label: String,
        dark: Boolean,
    ) = runComposeUiTest {
        val showBack = mutableStateOf(true)
        var exits = 0
        setContent {
            ProvideDesktopStrings(language) {
                MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                    Surface(Modifier.requiredSize(width.dp, 700.dp)) {
                        MangaDetailScreen(MangaDetailUiState(loading = true), { exits++ }, showBack = showBack.value)
                    }
                }
            }
        }
        onNodeWithTag("manga-detail-loading").assertIsDisplayed()
        onNodeWithText(label).assertIsDisplayed().performClick()
        exits shouldBe 1
        onNodeWithTag("manga-detail-pane").performKeyInput { pressKey(Key.Escape) }
        exits shouldBe 2
        System.getenv("MIHON_DETAIL_EVIDENCE")?.let { location ->
            val target = Files.createDirectories(Path.of(location))
            Image.makeFromBitmap(onNodeWithTag("manga-detail-pane").captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png ->
                    Files.write(target.resolve("detail-loading-$width-$language.png"), png.bytes)
                }
            }
        }
        runOnIdle { showBack.value = false }
        onNodeWithTag("manga-detail-back").assertDoesNotExist()
        onNodeWithTag("manga-detail-loading").assertIsDisplayed()
    }
}
