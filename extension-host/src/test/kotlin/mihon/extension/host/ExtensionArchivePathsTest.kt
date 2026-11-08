package mihon.extension.host

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.extension.validator.ExtensionValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExtensionArchivePathsTest {
    @TempDir
    lateinit var workingDir: Path

    @Test
    fun `nested package entries resolve inside the working directory`() {
        val destination = extensionEntryFile(workingDir.toFile(), "assets/icon.png")

        destination shouldBe File(workingDir.toFile().canonicalFile, "assets/icon.png")
    }

    @Test
    fun `parent traversal is rejected`() {
        shouldThrow<ExtensionValidationException> {
            extensionEntryFile(workingDir.toFile(), "../escape.jar")
        }
        shouldThrow<ExtensionValidationException> {
            extensionEntryFile(workingDir.toFile(), "assets/../../escape.jar")
        }
    }

    @Test
    fun `empty and NUL entry names are rejected`() {
        shouldThrow<ExtensionValidationException> { extensionEntryFile(workingDir.toFile(), "") }
        shouldThrow<ExtensionValidationException> { extensionEntryFile(workingDir.toFile(), "a\u0000b.jar") }
    }
}
