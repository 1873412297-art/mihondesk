package mihon.desktop.ui.browse

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ExtensionInstallControlsTest {
    @Test
    fun `duplicate install requests do not start concurrent downloads`(@TempDir root: Path): Unit = runBlocking {
        val firstRequest = CountDownLatch(1)
        val secondRequest = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/extension") { exchange ->
            try {
                if (requests.incrementAndGet() == 1) firstRequest.countDown() else secondRequest.countDown()
                release.await(10, TimeUnit.SECONDS)
                exchange.sendResponseHeaders(503, -1)
            } finally {
                exchange.close()
            }
        }
        server.start()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Default)
        val db = DesktopLibraryDatabaseFactory.open(root.resolve("library.db"))
        val preferences = DesktopPreferenceStore(root.resolve("preferences"))
        val installer = DesktopExtensionInstaller(root.resolve("extensions").toFile(), preferences)
        val presenter = BrowsePresenter(
            DesktopSourceManager(installer, WindowsExtensionProcessManager(root.toFile())),
            installer,
            ExtensionStoreService(preferences),
            db,
            preferences,
            scope,
        )
        val item =
            ExtensionStoreItem(
                "ext.test",
                "Test",
                "1.0",
                1,
                downloadUrl = "http://127.0.0.1:${server.address.port}/extension",
            )
        try {
            presenter.installExtension(item)
            firstRequest.await(5, TimeUnit.SECONDS) shouldBe true
            presenter.installExtension(item)
            secondRequest.await(300, TimeUnit.MILLISECONDS) shouldBe false
            requests.get() shouldBe 1
            presenter.cancelInstallation()
            withTimeout(1500) { presenter.state.first { !it.isInstalling } }
            presenter.state.value.installationCancelled shouldBe true
            presenter.state.value.errorMessage shouldBe null
            installer.getInstalledExtensions() shouldBe emptyList()
            presenter.installExtension(item)
            secondRequest.await(5, TimeUnit.SECONDS) shouldBe true
            presenter.state.value.installationCancelled shouldBe false
            release.countDown()
            withTimeout(5000) { presenter.state.first { !it.isInstalling } }
            presenter.state.value.errorMessage!!.contains("HTTP 503") shouldBe true
            requests.get() shouldBe 2
        } finally {
            release.countDown()
            parent.cancelAndJoin()
            server.stop(0)
            executor.shutdownNow()
            db.close()
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("false,false", "true,false", "false,true")
    fun `batch serializes commit and cancellation preserves finished installs`(
        cancelSecond: Boolean,
        failSecond: Boolean,
        @TempDir root: Path,
    ): Unit = runBlocking {
        fun archive(number: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(output).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(
                    """{"id":"ext.item$number","name":"Item $number","version":"2.0",
                        "versionCode":2,"libVersion":1.4,"lang":"en",
                        "sources":[{"id":$number,"name":"Source","lang":"en","className":"ext.Source"}]}
                    """.toByteArray(),
                )
                zip.closeEntry()
            }
            return output.toByteArray()
        }
        val verifying = CountDownLatch(1)
        val finishVerification = CountDownLatch(1)
        val secondReceived = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val seen = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val verified = AtomicInteger()
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                val number = exchange.requestURI.path.removePrefix("/").toInt()
                seen.add(number)
                if (number == 2) {
                    secondReceived.countDown()
                    releaseSecond.await(10, TimeUnit.SECONDS)
                    if (failSecond) {
                        exchange.sendResponseHeaders(503, -1)
                        return@createContext
                    }
                }
                val data = archive(number)
                exchange.sendResponseHeaders(200, data.size.toLong())
                exchange.responseBody.use { it.write(data) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Default)
        val db = DesktopLibraryDatabaseFactory.open(root.resolve("library.db"))
        val preferences = DesktopPreferenceStore(root.resolve("preferences"))
        val verifier = mihon.desktop.extension.ExtensionSignatureVerifier()
        val installer =
            DesktopExtensionInstaller(
                root.resolve("extensions").toFile(),
                preferences,
                verifier = mihon.desktop.extension.ExtensionVerifier { file ->
                    if (verified.incrementAndGet() == 1) {
                        verifying.countDown()
                        finishVerification.await(10, TimeUnit.SECONDS)
                    }
                    verifier.verify(file)
                },
            )
        val presenter =
            BrowsePresenter(
                DesktopSourceManager(installer, WindowsExtensionProcessManager(root.toFile())),
                installer,
                ExtensionStoreService(preferences),
                db,
                preferences,
                scope,
            )
        try {
            presenter.installItems(
                (1..3).map {
                    ExtensionStoreItem(
                        "ext.item$it",
                        "Item $it",
                        "2.0",
                        2,
                        downloadUrl = "http://127.0.0.1:${server.address.port}/$it",
                        trustOnInstall = true,
                    )
                },
            )
            verifying.await(5, TimeUnit.SECONDS) shouldBe true
            presenter.state.value.installPhase shouldBe ExtensionInstallPhase.Installing
            presenter.cancelInstallation()
            presenter.state.value.installPhase shouldBe ExtensionInstallPhase.Installing
            seen.toList() shouldBe listOf(1)
            finishVerification.countDown()
            secondReceived.await(5, TimeUnit.SECONDS) shouldBe true
            installer.getInstalledExtensions().map { it.pkg } shouldBe listOf("ext.item1")
            if (cancelSecond) presenter.cancelInstallation() else releaseSecond.countDown()
            withTimeout(5000) { presenter.state.first { !it.isInstalling } }
            if (failSecond) {
                presenter.state.value.errorMessage!!.contains("HTTP 503") shouldBe true
            } else {
                presenter.state.value.errorMessage shouldBe null
            }
            presenter.state.value.installationCancelled shouldBe cancelSecond
            seen.toList() shouldBe if (cancelSecond || failSecond) listOf(1, 2) else listOf(1, 2, 3)
            installer.getInstalledExtensions().map { it.pkg }.sorted() shouldBe
                if (cancelSecond || failSecond) listOf("ext.item1") else listOf("ext.item1", "ext.item2", "ext.item3")
        } finally {
            finishVerification.countDown()
            releaseSecond.countDown()
            parent.cancelAndJoin()
            server.stop(0)
            executor.shutdownNow()
            db.close()
        }
    }
}
