package mihon.desktop

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.platform.SingleInstanceRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

class MainProfileInUseTest {
    @TempDir lateinit var tempDir: Path

    private fun run(vararg args: String, forwardResult: Boolean): Triple<Int, String, List<SingleInstanceRequest>> {
        val array = arrayOf(*args)
        val sent = mutableListOf<SingleInstanceRequest>()
        val buffer = ByteArrayOutputStream()
        val code = handleProfileInUse(
            args = array,
            command = DesktopCommandParser.parse(array, emptyMap()),
            profileRoot = tempDir,
            workingDirectory = tempDir,
            out = PrintStream(buffer, true, Charsets.UTF_8),
            forward = { _, request ->
                sent += request
                forwardResult
            },
        )
        return Triple(code, buffer.toString(Charsets.UTF_8), sent)
    }

    @Test
    fun `second UI launch activates the running instance`() {
        val (code, output, sent) = run(forwardResult = true)
        code shouldBe 0
        output shouldContain "FORWARDED"
        sent shouldBe listOf(SingleInstanceRequest.Activate)
    }

    @Test
    fun `double-clicked file is forwarded as open`() {
        val (code, _, sent) = run("book.cbz", forwardResult = true)
        code shouldBe 0
        sent shouldBe listOf(SingleInstanceRequest.Open(tempDir.resolve("book.cbz").toAbsolutePath()))
    }

    @Test
    fun `failed forward keeps exit 75`() {
        val (code, output, _) = run(forwardResult = false)
        code shouldBe 75
        output shouldContain "SKIPPED"
    }

    @Test
    fun `background commands still skip silently without forwarding`() {
        listOf("--background-update", "--background-backup").forEach { argument ->
            val (code, output, sent) = run(argument, forwardResult = true)
            code shouldBe 0
            output shouldContain "SKIPPED"
            sent shouldBe emptyList()
        }
    }

    @Test
    fun `headless commands are not forwarded`() {
        val (code, _, sent) = run("--version", forwardResult = true)
        code shouldBe 75
        sent shouldBe emptyList()
        handleProfileInUse(arrayOf(), null, tempDir, tempDir, PrintStream(ByteArrayOutputStream())) { _, _ ->
            error("must not forward without a parsed command")
        } shouldBe 75
        DesktopCommand.LaunchUi shouldBe DesktopCommandParser.parse(arrayOf(), emptyMap())
    }
}
