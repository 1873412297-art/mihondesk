package mihon.desktop.reader.codec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class ProcessCodecCommandRunnerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `timed out process has actually exited before caller can remove its working directory`(): Unit = runTest {
        val process = AsynchronousExitProcess()
        shouldThrow<ReaderFailure.CorruptImage> {
            ProcessCodecCommandRunner.run(command(temporaryDirectory.resolve("codec.exe"), emptyList())) { process }
        }
        process.isAlive shouldBe false
    }

    @Test
    fun `missing executable becomes a typed page failure`() = runTest {
        shouldThrow<ReaderFailure.CorruptImage> {
            ProcessCodecCommandRunner.run(
                command(temporaryDirectory.resolve("missing-codec.exe"), listOf("-version")),
            )
        }
    }

    @Test
    fun `timeout kills request and a later process still succeeds`() = runTest {
        val commandPrompt = Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe")
        shouldThrow<ReaderFailure.CorruptImage> {
            ProcessCodecCommandRunner.run(
                command(
                    commandPrompt,
                    listOf("/d", "/c", "ping -n 6 127.0.0.1 >nul"),
                    timeoutMillis = 100,
                ),
            )
        }

        val result = ProcessCodecCommandRunner.run(
            command(commandPrompt, listOf("/d", "/c", "echo recovered")),
        )
        result.exitCode shouldBe 0
        result.stdout.decodeToString().trim() shouldBe "recovered"
    }

    @Test
    fun `cancellation interrupts and destroys a running request`(): Unit = runBlocking {
        val powerShell = Path.of(
            System.getenv("SystemRoot"),
            "System32",
            "WindowsPowerShell",
            "v1.0",
            "powershell.exe",
        )
        val started = CompletableDeferred<Process>()
        val startedAt = System.nanoTime()
        val job = launch(Dispatchers.Default) {
            ProcessCodecCommandRunner.run(
                command(
                    powerShell,
                    listOf("-NoProfile", "-Command", "Start-Sleep -Seconds 30"),
                    timeoutMillis = 60_000,
                ),
            ) { builder -> builder.start().also { started.complete(it) } }
        }
        val process = withTimeout(5_000) { started.await() }
        process.isAlive shouldBe true
        delay(50)
        job.cancelAndJoin()
        process.isAlive shouldBe false
        // On shared CI runners process teardown is asynchronous; descendants can
        // outlive the direct child briefly and keep the @TempDir files locked when
        // JUnit cleans up. Wait for the whole tree to exit first.
        withTimeoutOrNull(5_000) {
            while (process.descendants().anyMatch { it.isAlive }) delay(50)
        }
        delay(100)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        (elapsedMillis < 5_000) shouldBe true
    }

    private fun command(
        executable: Path,
        arguments: List<String>,
        timeoutMillis: Long = 2_000,
    ) = CodecCommand(
        executable = executable,
        arguments = arguments,
        workingDirectory = temporaryDirectory,
        timeoutMillis = timeoutMillis,
        maxStdoutBytes = 1024,
    )

    /** Models Windows TerminateProcess: requesting termination does not synchronously release handles. */
    private class AsynchronousExitProcess : Process() {
        private var alive = true
        private var terminationRequested = false
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun isAlive() = alive
        override fun descendants(): Stream<ProcessHandle> = Stream.empty()
        override fun destroy() {
            terminationRequested = true
        }
        override fun destroyForcibly(): Process {
            destroy()
            return this
        }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (terminationRequested) alive = false
            return !alive
        }
        override fun waitFor(): Int {
            check(terminationRequested)
            alive = false
            return 1
        }
        override fun exitValue(): Int {
            check(!alive)
            return 1
        }
    }
}
