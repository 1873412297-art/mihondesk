package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionStoreItem
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
class ExtensionInstallStatusTest {
    @ParameterizedTest
    @CsvSource(
        "1024,English,false",
        "480,English,true",
        "1024,SimplifiedChinese,true",
        "480,SimplifiedChinese,false",
        "1024,TraditionalChinese,false",
        "480,TraditionalChinese,true",
    )
    fun `screen exposes cancellation and prevents competing installs`(
        width: Int,
        language: AppLanguage,
        dark: Boolean,
    ) =
        runComposeUiTest {
            System.getenv("MIHON_EXTENSION_CONTROLS_APP")?.let { directory ->
                for (name in listOf(
                    "mihon.desktop.ui.browse.BrowsePresenter",
                    "mihon.desktop.ui.browse.BrowseScreenKt",
                    "mihon.desktop.ui.browse.ExtensionInstallStatusKt",
                    "mihon.desktop.extension.DesktopExtensionInstaller",
                )) {
                    val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                    check(origin.startsWith(Path.of(directory))) { "Expected packaged $name, got $origin" }
                    println("PACKAGED_EXTENSION_CONTROLS $name $origin")
                }
            }
            val state = mutableStateOf(
                BrowseUiState(
                    selectedTab = BrowseTab.Extensions,
                    isInstalling = true,
                    installingPkg = "ext.active",
                    installingName = "Manga catalogue / 漫画目录 / 漫畫目錄",
                    installPhase = ExtensionInstallPhase.Downloading,
                    availableExtensions = listOf(ExtensionStoreItem("ext.other", "Another extension", "1.0", 1)),
                ),
            )
            var cancels = 0
            setContent {
                ProvideDesktopStrings(language) {
                    MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                        Surface(Modifier.requiredSize(width.dp, 700.dp).testTag("install-render")) {
                            BrowseScreen(state.value, {}, {}, onCancelInstallation = { cancels++ })
                        }
                    }
                }
            }
            onNodeWithTag("extension-install-cancel").assertIsDisplayed().performClick()
            cancels shouldBe 1
            onNodeWithTag("install-file-button").assertIsDisplayed().assertIsNotEnabled()
            onNodeWithTag("manage-repos-button").assertIsDisplayed()
            onNodeWithTag("refresh-browse-button").assertIsDisplayed()
            onNodeWithTag("install-btn-ext.other").assertIsNotEnabled()
            System.getenv("MIHON_EXTENSION_CONTROLS_EVIDENCE")?.let { location ->
                val target = Files.createDirectories(Path.of(location))
                Image.makeFromBitmap(onNodeWithTag("install-render").captureToImage().asSkiaBitmap()).use { image ->
                    image.encodeToData()!!.use { png ->
                        Files.write(target.resolve("install-$width-$language.png"), png.bytes)
                    }
                }
            }
            runOnIdle { state.value = state.value.copy(installPhase = ExtensionInstallPhase.Cancelling) }
            onNodeWithTag("extension-install-cancel").assertIsNotEnabled()
            runOnIdle { state.value = state.value.copy(installPhase = ExtensionInstallPhase.Installing) }
            onNodeWithTag("extension-install-cancel").assertDoesNotExist()
            runOnIdle {
                state.value = state.value.copy(isInstalling = false, installPhase = null, installationCancelled = true)
            }
            onNodeWithTag("extension-install-cancelled").assertIsDisplayed()
            onNodeWithTag("install-btn-ext.other").assertIsEnabled()
            onNodeWithTag("install-file-button").assertIsEnabled()
        }
}
