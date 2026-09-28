package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ExtensionUpdateCheckerTest {

    @Test
    fun `checkNow reports zero and never notifies when nothing is installed or published`(@TempDir tempDir: Path) =
        runBlocking {
            val preferences = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("extensions").toFile(),
                preferenceStore = preferences,
            )
            val storeService = ExtensionStoreService(preferenceStore = preferences)

            var notifications = 0
            val checker = ExtensionUpdateChecker(
                installer = installer,
                storeService = storeService,
                onUpdatesAvailable = { notifications++ },
            )

            checker.checkNow() shouldBe 0
            notifications shouldBe 0

            checker.stop()
        }
}
