package mihon.desktop.reader.codec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihon.reader.source.ReaderFailure
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class CodecCommand(
    val executable: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
    val timeoutMillis: Long,
    val maxStdoutBytes: Int,
    val maxStderrBytes: Int = 64 * 1024,
)

data class CodecCommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

fun interface CodecCommandRunner {
    suspend fun run(command: CodecCommand): CodecCommandResult
}

object ProcessCodecCommandRunner : CodecCommandRunner {
    override suspend fun run(command: CodecCommand): CodecCommandResult = run(command, ProcessBuilder::start)

    internal suspend fun run(
        command: CodecCommand,
        startProcess: (ProcessBuilder) -> Process,
    ): CodecCommandResult = runInterruptible(Dispatchers.IO) {
        val executable = command.executable.toAbsolutePath().normalize()
        val codecHome = requireNotNull(executable.parent) { "codec executable must have a parent directory" }
        val processBuilder = ProcessBuilder(listOf(executable.toString()) + command.arguments)
            .directory(command.workingDirectory.toFile())
        processBuilder.environment().apply {
            this["MAGICK_HOME"] = codecHome.toString()
            this["MAGICK_CONFIGURE_PATH"] = codecHome.toString()
            this["MAGICK_OCL_DEVICE"] = "OFF"
            this["PATH"] = codecHome.toString()
        }
        val process = try {
            startProcess(processBuilder)
        } catch (error: IOException) {
            throw ReaderFailure.CorruptImage(error)
        }
        val stdout = CompletableFuture.supplyAsync {
            process.inputStream.readBounded(command.maxStdoutBytes, "codec stdout")
        }
        val stderr = CompletableFuture.supplyAsync {
            process.errorStream.readBounded(command.maxStderrBytes, "codec stderr")
        }
        var failure: Throwable? = null
        try {
            if (!process.waitFor(command.timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw ReaderFailure.CorruptImage(IllegalStateException("codec process timed out"))
            }
            CodecCommandResult(process.exitValue(), stdout.get(), stderr.get())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (process.isAlive) {
                try {
                    terminateAndWait(process)
                } catch (cleanup: Throwable) {
                    if (failure == null) throw cleanup
                    failure.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun terminateAndWait(process: Process) {
        val children = process.descendants().use { it.toList() }
        children.forEach { it.destroyForcibly() }
        process.destroyForcibly()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        var interrupted = Thread.interrupted()
        fun awaitExit(wait: (Long) -> Boolean) {
            while (true) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw IOException("codec process did not exit after termination")
                try {
                    if (wait(remaining)) return
                } catch (_: InterruptedException) {
                    // Cancellation interrupts the worker, but its child must relinquish the request
                    // directory before withEncodedInput removes it. Restore the interrupt afterwards.
                    interrupted = true
                }
            }
        }
        try {
            awaitExit { process.waitFor(it, TimeUnit.NANOSECONDS) }
            children.forEach { child ->
                if (child.isAlive) {
                    awaitExit { remaining ->
                        try {
                            child.onExit().get(remaining, TimeUnit.NANOSECONDS)
                            true
                        } catch (_: TimeoutException) {
                            false
                        }
                    }
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun InputStream.readBounded(limit: Int, name: String): ByteArray = use {
        val bytes = readNBytes(Math.addExact(limit, 1))
        if (bytes.size > limit) throw ReaderFailure.LimitExceeded(name, limit.toLong(), bytes.size.toLong())
        bytes
    }
}
