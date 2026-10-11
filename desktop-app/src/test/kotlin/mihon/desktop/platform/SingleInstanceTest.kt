package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import mihon.desktop.cli.DesktopCommandParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SingleInstanceTest {
    @TempDir lateinit var tempDir: Path

    private val token = "ab".repeat(32)

    private fun requestFor(vararg args: String): SingleInstanceRequest? {
        val array = arrayOf(*args)
        return SingleInstanceForwarding.requestFor(array, DesktopCommandParser.parse(array, emptyMap()), tempDir)
    }

    @Test
    fun `plain UI launch forwards activate`() {
        requestFor() shouldBe SingleInstanceRequest.Activate
        requestFor("--portable") shouldBe SingleInstanceRequest.Activate
    }

    @Test
    fun `file association launch forwards an absolute open request`() {
        requestFor("book.cbz") shouldBe SingleInstanceRequest.Open(tempDir.resolve("book.cbz").toAbsolutePath())
        val backup = tempDir.resolve("dir with space/备份.tachibk").toAbsolutePath()
        requestFor(backup.toString()) shouldBe SingleInstanceRequest.Open(backup)
    }

    @Test
    fun `explicit and background commands are not forwarded`() {
        requestFor("--import-local=book.cbz") shouldBe null
        requestFor("--import-backup=a.tachibk") shouldBe null
        requestFor("--background-update") shouldBe null
        requestFor("--background-backup") shouldBe null
        requestFor("--version") shouldBe null
        requestFor("--export-backup=out.tachibk") shouldBe null
    }

    @Test
    fun `protocol round-trips and rejects forged or malformed lines`() {
        val path = tempDir.resolve("a b/漫画.cbz").toAbsolutePath()
        val open = SingleInstanceRequest.Open(path)
        SingleInstanceProtocol.decode(SingleInstanceProtocol.encode(token, open), token) shouldBe open
        val activate = SingleInstanceProtocol.encode(token, SingleInstanceRequest.Activate)
        SingleInstanceProtocol.decode(activate, token) shouldBe
            SingleInstanceRequest.Activate
        SingleInstanceProtocol.decode(SingleInstanceProtocol.encode("cd".repeat(32), open), token) shouldBe null
        SingleInstanceProtocol.decode("MIHON1 $token", token) shouldBe null
        SingleInstanceProtocol.decode("MIHON1 $token DELETE", token) shouldBe null
        SingleInstanceProtocol.decode("OTHER $token ACTIVATE", token) shouldBe null
        val relative = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("relative.cbz".toByteArray(StandardCharsets.UTF_8))
        SingleInstanceProtocol.decode("MIHON1 $token OPEN $relative", token) shouldBe null
        SingleInstanceProtocol.decode("MIHON1 $token OPEN !!!", token) shouldBe null
    }

    @Test
    fun `client forwards activate and open to a running server`() {
        val received = LinkedBlockingQueue<SingleInstanceRequest>()
        SingleInstanceServer.start(tempDir) { received.put(it) }.use {
            SingleInstanceClient.send(tempDir, SingleInstanceRequest.Activate) {} shouldBe true
            received.poll(5, TimeUnit.SECONDS) shouldBe SingleInstanceRequest.Activate
            val open = SingleInstanceRequest.Open(tempDir.resolve("x.tachibk").toAbsolutePath())
            SingleInstanceClient.send(tempDir, open) {} shouldBe true
            received.poll(5, TimeUnit.SECONDS) shouldBe open
        }
    }

    @Test
    fun `server denies requests without the endpoint token`() {
        val received = LinkedBlockingQueue<SingleInstanceRequest>()
        SingleInstanceServer.start(tempDir) { received.put(it) }.use { server ->
            Socket(InetAddress.getLoopbackAddress(), server.port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write("MIHON1 ${"0".repeat(64)} ACTIVATE\n".toByteArray())
                socket.getOutputStream().flush()
                SingleInstanceProtocol.readLine(socket.getInputStream()) shouldBe "DENIED"
            }
            // The listener keeps serving legitimate requests afterwards.
            SingleInstanceClient.send(tempDir, SingleInstanceRequest.Activate) {} shouldBe true
            received.poll(5, TimeUnit.SECONDS) shouldBe SingleInstanceRequest.Activate
            received.size shouldBe 0
        }
    }

    @Test
    fun `failing handler is reported as not forwarded`() {
        SingleInstanceServer.start(tempDir) { error("closed") }.use {
            SingleInstanceClient.send(tempDir, SingleInstanceRequest.Activate) {} shouldBe false
        }
    }

    @Test
    fun `missing or stale endpoint fails without throwing and close removes endpoint`() {
        SingleInstanceClient.send(tempDir, SingleInstanceRequest.Activate) {} shouldBe false
        val endpoint = tempDir.resolve(SingleInstanceProtocol.ENDPOINT_FILE)
        val server = SingleInstanceServer.start(tempDir) {}
        Files.exists(endpoint) shouldBe true
        val stale = Files.readAllBytes(endpoint)
        server.close()
        Files.exists(endpoint) shouldBe false
        Files.write(endpoint, stale)
        SingleInstanceClient.send(tempDir, SingleInstanceRequest.Activate) {} shouldBe false
    }

    @Test
    fun `endpoint file is restricted to the current user`() {
        SingleInstanceServer.start(tempDir) {}.use {
            val endpoint = tempDir.resolve(SingleInstanceProtocol.ENDPOINT_FILE)
            val posix = Files.getFileAttributeView(endpoint, PosixFileAttributeView::class.java)
            if (posix != null) {
                posix.readAttributes().permissions() shouldBe
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            } else {
                val acl = Files.getFileAttributeView(endpoint, AclFileAttributeView::class.java)!!
                val allowed = setOf(acl.owner.name.substringAfter('\\'), System.getProperty("user.name"))
                acl.acl.all { it.principal().name.substringAfter('\\') in allowed } shouldBe true
            }
        }
    }

    @Test
    fun `file kinds follow the association extensions`() {
        ForwardedFileKind.of(Path.of("a.TACHIBK")) shouldBe ForwardedFileKind.AndroidBackup
        ForwardedFileKind.of(Path.of("a.cbz")) shouldBe ForwardedFileKind.LocalArchive
        ForwardedFileKind.of(Path.of("a.zip")) shouldBe ForwardedFileKind.LocalArchive
        ForwardedFileKind.of(Path.of("a.exe")) shouldBe ForwardedFileKind.Unsupported
    }
}
