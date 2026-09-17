package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.validator.ExtensionValidationException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionDownloadCancellationTest {
    @BeforeEach
    fun verifyPackagedInstallerWhenRequested() {
        val packagedRoot = System.getenv("MIHON_EXTENSION_APP")?.let(Path::of)?.toAbsolutePath()?.normalize() ?: return
        val origin = Path.of(DesktopExtensionInstaller::class.java.protectionDomain.codeSource.location.toURI())
            .toAbsolutePath().normalize()
        origin.startsWith(packagedRoot) shouldBe true
        println("PACKAGED_EXTENSION_INSTALLER $origin")
    }

    @ParameterizedTest
    @ValueSource(strings = ["valid", "wrong-hash", "http-error"])
    fun `download retains successful installation and validation failures`(mode: String, @TempDir root: Path): Unit =
        runBlocking {
            val bytes = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(
                        """{"id":"ext.download","name":"Download","version":"1.0","versionCode":1,
                        "libVersion":1.4,"lang":"en","sources":[{"id":7,"name":"Source","lang":"en",
                        "className":"ext.Source"}]}""".toByteArray(),
                    )
                    zip.closeEntry()
                }
            }.toByteArray()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/extension") { exchange ->
                try {
                    exchange.sendResponseHeaders(if (mode == "http-error") 503 else 200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val client = OkHttpClient()
            val installer = DesktopExtensionInstaller(
                root.resolve("extensions").toFile(),
                DesktopPreferenceStore(root.resolve("preferences")),
                httpClient = client,
            )
            val expected = if (mode == "wrong-hash") {
                "0".repeat(64)
            } else {
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            try {
                suspend fun install() = installer.downloadAndInstall(
                    "http://127.0.0.1:${server.address.port}/extension",
                    expected,
                    trustOnInstall = true,
                )
                if (mode == "valid") {
                    install().pkg shouldBe "ext.download"
                    installer.getInstalledExtensions().single().pkg shouldBe "ext.download"
                } else {
                    val error = assertThrows<ExtensionValidationException> { install() }
                    error.message!!.contains(if (mode == "http-error") "HTTP 503" else "SHA-256 mismatch") shouldBe true
                    installer.getInstalledExtensions() shouldBe emptyList()
                    root.resolve("extensions").toFile().listFiles()!!.toList() shouldBe emptyList()
                }
            } finally {
                server.stop(0)
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `cancellation closes header and body waits without installing`(
        sendHeaders: Boolean,
        @TempDir root: Path,
    ): Unit =
        runBlocking {
            val received = CountDownLatch(1)
            val bodyStarted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val activeCall = AtomicReference<Call>()
            val downloadFailure = AtomicReference<Throwable>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/extension") { exchange ->
                try {
                    if (sendHeaders) {
                        exchange.sendResponseHeaders(200, 1024)
                        exchange.responseBody.write(byteArrayOf(0x50, 0x4b))
                        exchange.responseBody.flush()
                    }
                    received.countDown()
                    release.await(15, TimeUnit.SECONDS)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .eventListener(object : EventListener() {
                    override fun callStart(call: Call) {
                        activeCall.set(call)
                    }
                    override fun responseBodyStart(call: Call) {
                        bodyStarted.countDown()
                    }
                })
                .build()
            val installer = DesktopExtensionInstaller(
                root.resolve("extensions").toFile(),
                DesktopPreferenceStore(root.resolve("preferences")),
                httpClient = client,
            )
            val operation = launch(Dispatchers.IO) {
                try {
                    installer.downloadAndInstall("http://127.0.0.1:${server.address.port}/extension", null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    downloadFailure.set(failure)
                }
            }
            try {
                withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) } shouldBe true
                if (sendHeaders) {
                    withContext(Dispatchers.IO) { bodyStarted.await(5, TimeUnit.SECONDS) } shouldBe true
                }
                withTimeout(1000) { operation.cancelAndJoin() }
                activeCall.get().isCanceled() shouldBe true
                downloadFailure.get() shouldBe null
                installer.getInstalledExtensions() shouldBe emptyList()
                root.resolve("extensions").toFile().listFiles()!!.toList() shouldBe emptyList()
            } finally {
                release.countDown()
                operation.cancelAndJoin()
                server.stop(0)
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
}
