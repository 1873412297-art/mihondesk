package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class AppUpdatePresenterTest {
    @Test
    fun `cancel waits for blocked read cleanup and rejects a second operation`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val releaseRead = java.util.concurrent.CountDownLatch(1)
        val closed = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val service = DesktopAppUpdateService(
            fetchText = {
                calls.incrementAndGet()
                if (it.endsWith("latest")) json else "$hash  $name"
            },
            downloadStream = {
                object : ByteArrayInputStream(data) {
                    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                        entered.complete(Unit)
                        check(releaseRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        return super.read(buffer, off, len)
                    }
                    override fun close() {
                        closed.complete(Unit)
                        super.close()
                    }
                }
            },
        )
        val presenter = AppUpdatePresenter(service, this)
        try {
            presenter.check()
            presenter.await(AppUpdatePhase.Available)
            val destination = root.resolve(name)
            Files.writeString(destination, "old")
            presenter.download(destination)
            withTimeout(5000) { entered.await() }
            presenter.cancel()
            presenter.state.value.phase shouldBe AppUpdatePhase.Cancelling
            presenter.check()
            presenter.download(destination)
            calls.get() shouldBe 2
            releaseRead.countDown()
            presenter.await(AppUpdatePhase.Cancelled)
            closed.isCompleted shouldBe true
            Files.readString(destination) shouldBe "old"
            Files.list(root).use { it.count() } shouldBe 1L
        } finally {
            releaseRead.countDown()
            presenter.shutdown()
        }
    }

    @TempDir lateinit var root: Path
    private val data = "package bytes".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private val name = "mihondesk-0.3.0.exe"
    private val base = "https://github.com/${DesktopAppUpdateService.DEFAULT_REPO}/releases/download/v0.3.0/"
    private val json = """{"tag_name":"v0.3.0","assets":[
        {"name":"$name","browser_download_url":"$base$name","size":${data.size}},
        {"name":"SHA256SUMS.txt","browser_download_url":"${base}SHA256SUMS.txt"}]}"""

    private suspend fun AppUpdatePresenter.await(phase: AppUpdatePhase) = withTimeout(5000) {
        state.first {
            it.phase ==
                phase
        }
    }

    @Test
    fun `rapid checks stay single flight and immediate cancel allows retry`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val presenter = AppUpdatePresenter(
            DesktopAppUpdateService(fetchText = {
                calls.incrementAndGet()
                entered.complete(Unit)
                delay(60_000)
                json
            }),
            this,
        )
        presenter.check()
        presenter.check()
        entered.await()
        calls.get() shouldBe 1
        presenter.cancel()
        presenter.await(AppUpdatePhase.Cancelled)
        presenter.check()
        presenter.cancel()
        presenter.await(AppUpdatePhase.Cancelled)
        presenter.shutdown()
    }

    @Test
    fun `bad manifest blocks download then retry publishes verified bytes`(): Unit = runBlocking {
        var manifest = "missing"
        val downloads = AtomicInteger()
        val service =
            DesktopAppUpdateService(fetchText = { if (it.endsWith("latest")) json else manifest }, downloadStream = {
                downloads.incrementAndGet()
                ByteArrayInputStream(data)
            })
        val presenter = AppUpdatePresenter(service, this)
        presenter.check()
        presenter.await(AppUpdatePhase.Available)
        val destination = root.resolve(name)
        Files.writeString(destination, "old package")
        presenter.download(destination)
        presenter.await(AppUpdatePhase.Failed)
        downloads.get() shouldBe 0
        Files.readString(destination) shouldBe "old package"
        manifest = "$hash  $name"
        presenter.download(destination)
        val ready = presenter.await(AppUpdatePhase.Ready)
        ready.savedFile shouldBe destination
        ready.received shouldBe data.size.toLong()
        Files.readAllBytes(destination) shouldBe data
        Files.list(root).use { it.count() } shouldBe 1L
        presenter.shutdown()
    }
}
