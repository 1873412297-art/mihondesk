package mihon.desktop.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StorageUsageCalculatorTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `separates profile categories and counts external downloads`() {
        val profile = tempDir.resolve("profile")
        val downloads = tempDir.resolve("external-downloads")
        val backups = tempDir.resolve("backups")
        Files.createDirectories(profile.resolve("database"))
        Files.createDirectories(profile.resolve("cache"))
        Files.createDirectories(downloads)
        Files.createDirectories(backups)
        Files.write(profile.resolve("database/library.db"), ByteArray(7))
        Files.write(profile.resolve("cache/image"), ByteArray(3))
        Files.write(downloads.resolve("page"), ByteArray(11))
        Files.write(backups.resolve("backup"), ByteArray(5))

        val usage = StorageUsageCalculator().calculate(profile, downloads, backups)
        usage.database shouldBe 7
        usage.imageCache shouldBe 3
        usage.downloads shouldBe 11
        usage.backups shouldBe 5
        usage.extensions shouldBe 0
    }
}
