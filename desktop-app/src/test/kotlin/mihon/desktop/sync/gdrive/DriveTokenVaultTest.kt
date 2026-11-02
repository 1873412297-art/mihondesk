package mihon.desktop.sync.gdrive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import mihon.sync.transport.gdrive.GoogleTokens
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private object XorCipher : TokenCipher {
    override fun protect(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unprotect(data: ByteArray) = protect(data)
}

class DriveTokenVaultTest {
    @TempDir lateinit var dir: Path

    private val tokens = GoogleTokens("access-123", "refresh-secret-456", 99L)

    @Test
    fun `round trips and never stores plaintext refresh token`() {
        val vault = DriveTokenVault(dir.resolve("gdrive/tokens.bin"), XorCipher)
        vault.load() shouldBe null
        vault.save(tokens)
        String(Files.readAllBytes(dir.resolve("gdrive/tokens.bin")), Charsets.ISO_8859_1) shouldNotContain
            "refresh-secret"
        DriveTokenVault(dir.resolve("gdrive/tokens.bin"), XorCipher).load() shouldBe tokens
        vault.clear()
        vault.load() shouldBe null
    }

    @Test
    fun `corrupt file loads as signed out`() {
        val f = dir.resolve("tokens.bin")
        Files.write(f, byteArrayOf(1, 2, 3))
        DriveTokenVault(f, XorCipher).load() shouldBe null
    }

    @Test
    fun `dpapi round trip on windows`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val vault = DriveTokenVault(dir.resolve("t.bin"), DpapiTokenCipher)
        vault.save(tokens)
        String(Files.readAllBytes(dir.resolve("t.bin")), Charsets.ISO_8859_1) shouldNotContain "refresh-secret"
        vault.load() shouldBe tokens
    }
}
