package mihon.desktop.ui.browse

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Install failures must name the step that failed, so each outcome gets its own message. */
class ExtensionInstallFailurePresenterTest {

    @Test
    fun `a download step failure is reported as a download failure`(@TempDir root: Path): Unit = runBlocking {
        val store = serveStore(root) { respond(it, 503, null) }
        try {
            awaitAvailable(store.presenter)

            store.presenter.installExtension(store.presenter.state.value.availableExtensions.single())
            awaitInstallFailure(store.presenter)

            store.presenter.state.value.installFailure shouldBe ExtensionInstallFailure.DownloadFailed
            store.presenter.state.value.errorMessage!!.contains("HTTP 503") shouldBe true
        } finally {
            store.close()
        }
    }

    @Test
    fun `a signature or trust rejection is reported as a signature rejection`(@TempDir root: Path): Unit = runBlocking {
        val store = serveStore(root) { respond(it, 200, unsignedPackageBytes()) }
        try {
            awaitAvailable(store.presenter)

            store.presenter.installExtension(store.presenter.state.value.availableExtensions.single())
            awaitInstallFailure(store.presenter)

            store.presenter.state.value.installFailure shouldBe ExtensionInstallFailure.SignatureRejected
            store.presenter.state.value.errorMessage!!.contains("trust", ignoreCase = true) shouldBe true
            store.installer.getInstalledExtensions() shouldBe emptyList()
        } finally {
            store.close()
        }
    }

    @Test
    fun `a package no configured store lists anymore is reported as a store loss`(@TempDir root: Path): Unit =
        runBlocking {
            val store = serveStore(root) { respond(it, 404, null) }
            try {
                awaitAvailable(store.presenter)

                store.presenter.installExtension(
                    ExtensionStoreItem(
                        pkg = "ext.gone",
                        name = "Gone",
                        version = "1.0.0",
                        versionCode = 1L,
                        downloadUrl = "${store.repositoryUrl}/ext.gone",
                        repoUrl = store.repositoryUrl,
                    ),
                )
                awaitInstallFailure(store.presenter)

                store.presenter.state.value.installFailure shouldBe ExtensionInstallFailure.StoreUnavailable
                store.presenter.state.value.errorMessage!!.contains("ext.gone") shouldBe true
            } finally {
                store.close()
            }
        }

    @Test
    fun `a candidate whose own store was removed is reported as a store loss`(@TempDir root: Path): Unit = runBlocking {
        val store = serveStore(root) { respond(it, 404, null) }
        try {
            awaitAvailable(store.presenter)

            store.presenter.installExtension(
                ExtensionStoreItem(
                    pkg = PKG,
                    name = "Fail Extension",
                    version = "1.0.0",
                    versionCode = 1L,
                    downloadUrl = "${store.repositoryUrl}/$PKG",
                    repoUrl = "https://example.com/removed-store",
                ),
            )
            awaitInstallFailure(store.presenter)

            store.presenter.state.value.installFailure shouldBe ExtensionInstallFailure.StoreUnavailable
            store.presenter.state.value.errorMessage!!.contains(PKG) shouldBe true
        } finally {
            store.close()
        }
    }

    private class Store(
        val presenter: BrowsePresenter,
        val installer: DesktopExtensionInstaller,
        val repositoryUrl: String,
        private val parentJob: Job,
        private val database: SqlDelightLibraryRepository,
        private val server: HttpServer,
        private val executor: ExecutorService,
    ) {
        suspend fun close() {
            parentJob.cancelAndJoin()
            server.stop(0)
            executor.shutdownNow()
            database.close()
        }
    }

    private companion object {
        const val PKG = "ext.fail"

        /** Serves a one-extension store index; `/$PKG` is answered by [download]. */
        suspend fun serveStore(root: Path, download: (HttpExchange) -> Unit): Store {
            val executor = Executors.newCachedThreadPool()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.executor = executor
            var baseUrl = ""
            server.createContext("/") { exchange ->
                try {
                    when (exchange.requestURI.path) {
                        "/index.min.json" -> respond(exchange, 200, indexBody("$baseUrl/$PKG"))
                        "/$PKG" -> download(exchange)
                        else -> respond(exchange, 404, null)
                    }
                } finally {
                    exchange.close()
                }
            }
            server.start()
            baseUrl = "http://127.0.0.1:${server.address.port}"

            val parentJob = SupervisorJob()
            val scope = CoroutineScope(parentJob + Dispatchers.Default)
            val database = DesktopLibraryDatabaseFactory.open(root.resolve("library.db"))
            val preferences = DesktopPreferenceStore(root.resolve("preferences"))
            val installer = DesktopExtensionInstaller(root.resolve("extensions").toFile(), preferences)
            val storeService = ExtensionStoreService(preferences)
            storeService.addRepository(baseUrl)
            val presenter = BrowsePresenter(
                DesktopSourceManager(installer, WindowsExtensionProcessManager(root.toFile())),
                installer,
                storeService,
                database,
                preferences,
                scope,
            )
            return Store(presenter, installer, baseUrl, parentJob, database, server, executor)
        }

        fun indexBody(downloadUrl: String): ByteArray = """
            [{"pkg":"$PKG","name":"Fail Extension","version":"1.0.0","code":1,"lang":"en",
              "downloadUrl":"$downloadUrl"}]
        """.trimIndent().toByteArray()

        /** An unsigned extension package: it installs only after explicit user trust. */
        fun unsignedPackageBytes(): ByteArray {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    """
                    {"id":"$PKG","name":"Fail Extension","version":"1.0.0","versionCode":1,
                     "libVersion":1.4,"lang":"en",
                     "sources":[{"id":1,"name":"Source","lang":"en","className":"ext.Source"}]}
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            return output.toByteArray()
        }

        fun respond(exchange: HttpExchange, status: Int, body: ByteArray?) {
            if (body == null) {
                exchange.sendResponseHeaders(status, -1)
                return
            }
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        suspend fun awaitAvailable(presenter: BrowsePresenter) {
            var tries = 0
            while (presenter.state.value.availableExtensions.isEmpty() && tries++ < 100) {
                delay(50)
            }
            presenter.state.value.availableExtensions.isEmpty() shouldBe false
        }

        suspend fun awaitInstallFailure(presenter: BrowsePresenter) {
            withTimeout(5000) { presenter.state.first { it.errorMessage != null } }
        }
    }
}
