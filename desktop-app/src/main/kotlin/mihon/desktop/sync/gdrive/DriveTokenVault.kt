package mihon.desktop.sync.gdrive

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.desktop.logging.DesktopLogger
import mihon.sync.transport.gdrive.GoogleTokens
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean

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
    private val unreadable = AtomicBoolean(false)

    /** True after [load] found a token file it could not decrypt, until the next successful [save]. */
    fun hadUnreadableToken(): Boolean = unreadable.get()

    fun load(): GoogleTokens? {
        if (!Files.exists(file)) return null
        return runCatching {
            val stored = json.decodeFromString<StoredTokens>(
                cipher.unprotect(Files.readAllBytes(file)).decodeToString(),
            )
            GoogleTokens(stored.accessToken, stored.refreshToken, stored.expiresAtMillis)
        }.onFailure { error ->
            unreadable.set(true)
            DesktopLogger.warn("GoogleDrive", "Token file at $file is unreadable; sync watermarks are kept", error)
            quarantine()
        }.getOrNull()
    }

    private fun quarantine() {
        val aside = file.resolveSibling("${file.fileName}.corrupt")
        runCatching {
            mihon.desktop.platform.moveReplacing(file, aside)
        }.onFailure { error ->
            DesktopLogger.warn("GoogleDrive", "Cannot quarantine unreadable token file at $file", error)
        }
    }

    fun save(tokens: GoogleTokens) {
        Files.createDirectories(file.parent)
        val plain = json.encodeToString(
            StoredTokens.serializer(),
            StoredTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMillis),
        ).toByteArray()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.deleteIfExists(tmp)
        // Off Windows the tokens are stored as-is, so owner-only permissions are the protection. Create
        // the file with them instead of tightening afterwards, and fail rather than write it readable.
        if ("posix" in tmp.fileSystem.supportedFileAttributeViews()) {
            val ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            Files.createFile(tmp, ownerOnly)
        }
        Files.write(tmp, cipher.protect(plain))
        mihon.desktop.platform.moveReplacing(tmp, file)
        unreadable.set(false)
    }

    fun clear() {
        Files.deleteIfExists(file)
    }
}
