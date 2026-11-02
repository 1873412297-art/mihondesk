package mihon.desktop.sync.gdrive

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.sync.transport.gdrive.GoogleTokens
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/** Encrypts the token blob at rest. */
interface TokenCipher {
    fun protect(plain: ByteArray): ByteArray
    fun unprotect(data: ByteArray): ByteArray

    companion object {
        fun platformDefault(): TokenCipher =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) DpapiTokenCipher else OwnerOnlyFileCipher
    }
}

/** Windows DPAPI, bound to the current Windows user account. */
object DpapiTokenCipher : TokenCipher {
    private val entropy = "mihondesk-gdrive".toByteArray()
    override fun protect(
        plain: ByteArray,
    ): ByteArray = Crypt32Util.cryptProtectData(plain, entropy, 0, "mihondesk", null)
    override fun unprotect(data: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(data, entropy, 0, null)
}

/** Non-Windows fallback: no OS keystore wired yet, relies on 0600 file permissions. */
object OwnerOnlyFileCipher : TokenCipher {
    override fun protect(plain: ByteArray) = plain
    override fun unprotect(data: ByteArray) = data
}

@Serializable
private data class StoredTokens(val accessToken: String, val refreshToken: String?, val expiresAtMillis: Long)

/** Per-profile token file (`<profile>/gdrive/tokens.bin`). */
class DriveTokenVault(
    private val file: Path,
    private val cipher: TokenCipher = TokenCipher.platformDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): GoogleTokens? {
        if (!Files.exists(file)) return null
        return runCatching {
            val stored = json.decodeFromString<StoredTokens>(
                cipher.unprotect(Files.readAllBytes(file)).decodeToString(),
            )
            GoogleTokens(stored.accessToken, stored.refreshToken, stored.expiresAtMillis)
        }.getOrNull()
    }

    fun save(tokens: GoogleTokens) {
        Files.createDirectories(file.parent)
        val plain = json.encodeToString(
            StoredTokens.serializer(),
            StoredTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMillis),
        ).toByteArray()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.write(tmp, cipher.protect(plain))
        runCatching { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")) }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun clear() {
        Files.deleteIfExists(file)
    }
}
