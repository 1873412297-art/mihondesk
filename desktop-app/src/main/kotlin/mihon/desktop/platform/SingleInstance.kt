package mihon.desktop.platform

import mihon.desktop.cli.DesktopCommand
import mihon.desktop.logging.DesktopLogger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/** A request a second launch forwards to the instance that already holds the profile lock. */
sealed interface SingleInstanceRequest {
    /** Bring the existing main window to the front (restoring it from the tray if hidden). */
    data object Activate : SingleInstanceRequest

    /** Bring the window to the front and handle [path] (an absolute file path chosen by the user). */
    data class Open(val path: Path) : SingleInstanceRequest
}

/**
 * Decides whether a launch that lost the profile lock should be forwarded to the running instance.
 *
 * Only interactive launches are forwarded: a plain UI launch (Start Menu, shortcut) becomes
 * [SingleInstanceRequest.Activate], and a file-association launch (`mihondesk.exe "C:\x.cbz"`, a bare
 * path argument) becomes [SingleInstanceRequest.Open]. Explicit `--import-*` CLI commands, background
 * tasks and every other headless command keep their existing "profile in use" behaviour.
 */
object SingleInstanceForwarding {
    fun requestFor(args: Array<String>, command: DesktopCommand, workingDirectory: Path): SingleInstanceRequest? =
        when (command) {
            DesktopCommand.LaunchUi -> SingleInstanceRequest.Activate
            is DesktopCommand.ImportBackup -> openIfBareFile(args, command.path, workingDirectory)
            is DesktopCommand.ImportLocal -> openIfBareFile(args, command.path, workingDirectory)
            else -> null
        }

    private fun openIfBareFile(args: Array<String>, path: Path, workingDirectory: Path): SingleInstanceRequest? {
        val bare = args.filterNot { it.startsWith("--") }.singleOrNull() ?: return null
        if (runCatching { Path.of(bare) }.getOrNull() != path) return null
        return SingleInstanceRequest.Open(workingDirectory.resolve(path).toAbsolutePath().normalize())
    }
}

/**
 * Wire protocol shared by [SingleInstanceServer] and [SingleInstanceClient].
 *
 * Endpoint file `<profile>/.mihon-instance` (owner-only ACL): `MIHON-INSTANCE-1`, port, pid, token (hex).
 * Request: one UTF-8 line `MIHON1 <token> ACTIVATE` or `MIHON1 <token> OPEN <base64url(path)>`.
 * Reply: one line `OK` or `DENIED`. The server listens on 127.0.0.1 only.
 */
internal object SingleInstanceProtocol {
    const val ENDPOINT_FILE = ".mihon-instance"
    const val ENDPOINT_HEADER = "MIHON-INSTANCE-1"
    const val REQUEST_PREFIX = "MIHON1"
    const val MAX_LINE_BYTES = 16 * 1024
    const val IO_TIMEOUT_MS = 2_000

    fun encode(token: String, request: SingleInstanceRequest): String = when (request) {
        SingleInstanceRequest.Activate -> "$REQUEST_PREFIX $token ACTIVATE"
        is SingleInstanceRequest.Open -> {
            val encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(request.path.toString().toByteArray(StandardCharsets.UTF_8))
            "$REQUEST_PREFIX $token OPEN $encoded"
        }
    }

    /** Returns the request when [line] carries [expectedToken]; null for anything malformed or unauthenticated. */
    fun decode(line: String, expectedToken: String): SingleInstanceRequest? {
        val parts = line.split(' ')
        if (parts.size < 3 || parts[0] != REQUEST_PREFIX) return null
        if (!MessageDigest.isEqual(
                parts[1].toByteArray(StandardCharsets.US_ASCII),
                expectedToken.toByteArray(StandardCharsets.US_ASCII),
            )
        ) {
            return null
        }
        return when {
            parts.size == 3 && parts[2] == "ACTIVATE" -> SingleInstanceRequest.Activate
            parts.size == 4 && parts[2] == "OPEN" -> runCatching {
                val raw = String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                require(raw.isNotBlank() && '\u0000' !in raw)
                val path = Path.of(raw)
                require(path.isAbsolute)
                SingleInstanceRequest.Open(path.normalize())
            }.getOrNull()
            else -> null
        }
    }

    fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) return if (buffer.size() == 0) null else buffer.toString(StandardCharsets.UTF_8)
            if (next == '\n'.code) return buffer.toString(StandardCharsets.UTF_8).removeSuffix("\r")
            if (buffer.size() >= MAX_LINE_BYTES) return null
            buffer.write(next)
        }
    }
}

/**
 * Loopback listener run by the instance that owns the profile lock (UI mode only).
 *
 * Authentication: a fresh 256-bit token is written to an owner-only file inside the profile directory,
 * which is itself per-user. A local process that cannot read the user's profile cannot forge a request.
 */
class SingleInstanceServer private constructor(
    private val endpointFile: Path,
    private val socket: ServerSocket,
    private val token: String,
    private val onRequest: (SingleInstanceRequest) -> Unit,
) : AutoCloseable {
    val port: Int get() = socket.localPort

    private val thread = Thread(::acceptLoop, "mihon-single-instance").apply { isDaemon = true }

    private fun acceptLoop() {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (error: IOException) {
                DesktopLogger.warn("SingleInstance", "Accept failed: ${error.message}")
                continue
            }
            client.use(::serve)
        }
    }

    private fun serve(client: Socket) {
        try {
            if (!client.inetAddress.isLoopbackAddress) return
            client.soTimeout = SingleInstanceProtocol.IO_TIMEOUT_MS
            val line = SingleInstanceProtocol.readLine(client.getInputStream())
            val request = line?.let { SingleInstanceProtocol.decode(it, token) }
            val output = client.getOutputStream()
            if (request == null) {
                DesktopLogger.warn("SingleInstance", "Rejected unauthenticated or malformed forward request")
                output.write("DENIED\n".toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                return
            }
            DesktopLogger.info("SingleInstance", "Received forwarded ${request::class.simpleName} request")
            val accepted = runCatching { onRequest(request) }
                .onFailure { DesktopLogger.error("SingleInstance", "Forwarded request handler failed", it) }
                .isSuccess
            output.write((if (accepted) "OK\n" else "DENIED\n").toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        } catch (error: IOException) {
            DesktopLogger.warn("SingleInstance", "Forward connection failed: ${error.message}")
        }
    }

    override fun close() {
        runCatching { socket.close() }
        // Only remove the endpoint we published; a newer instance may have replaced it.
        runCatching {
            if (SingleInstanceEndpoint.read(endpointFile)?.token == token) Files.deleteIfExists(endpointFile)
        }
    }

    companion object {
        fun start(profileRoot: Path, onRequest: (SingleInstanceRequest) -> Unit): SingleInstanceServer {
            val root = profileRoot.toRealPath()
            val token = HexFormat.of().formatHex(ByteArray(32).also(SecureRandom()::nextBytes))
            val socket = ServerSocket()
            try {
                socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
                val endpoint = root.resolve(SingleInstanceProtocol.ENDPOINT_FILE)
                SingleInstanceEndpoint.write(
                    endpoint,
                    SingleInstanceEndpoint(socket.localPort, ProcessHandle.current().pid(), token),
                )
                return SingleInstanceServer(endpoint, socket, token, onRequest).also { it.thread.start() }
            } catch (error: Throwable) {
                socket.close()
                throw error
            }
        }
    }
}

internal data class SingleInstanceEndpoint(val port: Int, val pid: Long, val token: String) {
    companion object {
        private val TOKEN = Regex("[0-9a-f]{64}")

        fun read(file: Path): SingleInstanceEndpoint? = runCatching {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null
            val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
            if (lines.size < 4 || lines[0] != SingleInstanceProtocol.ENDPOINT_HEADER) return null
            val port = lines[1].trim().toInt().takeIf { it in 1..65535 } ?: return null
            val pid = lines[2].trim().toLong()
            val token = lines[3].trim().takeIf(TOKEN::matches) ?: return null
            SingleInstanceEndpoint(port, pid, token)
        }.getOrNull()

        fun write(file: Path, endpoint: SingleInstanceEndpoint) {
            val temp = Files.createTempFile(file.parent, ".mihon-instance-", ".tmp")
            try {
                // Restrict before the secret is written; a same-volume move keeps the security descriptor.
                restrictToOwner(temp)
                Files.writeString(
                    temp,
                    listOf(
                        SingleInstanceProtocol.ENDPOINT_HEADER,
                        endpoint.port.toString(),
                        endpoint.pid.toString(),
                        endpoint.token,
                    ).joinToString("\n", postfix = "\n"),
                    StandardCharsets.UTF_8,
                )
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }

        private fun restrictToOwner(file: Path) {
            val posix = Files.getFileAttributeView(file, PosixFileAttributeView::class.java)
            if (posix != null) {
                posix.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
                return
            }
            val acl = Files.getFileAttributeView(file, AclFileAttributeView::class.java) ?: return
            runCatching {
                val principals = buildSet {
                    add(acl.owner)
                    runCatching {
                        add(
                            file.fileSystem.userPrincipalLookupService
                                .lookupPrincipalByName(System.getProperty("user.name")),
                        )
                    }
                }
                acl.acl = principals.map { principal ->
                    AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(principal)
                        .setPermissions(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_NAMED_ATTRS,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.WRITE_ACL,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.SYNCHRONIZE,
                        )
                        .build()
                }
            }.onFailure { DesktopLogger.warn("SingleInstance", "Could not restrict endpoint ACL: ${it.message}") }
        }
    }
}

/** Used by a second launch to hand its request to the running instance. */
object SingleInstanceClient {
    /** Returns true only when the running instance acknowledged the request. Never throws. */
    fun send(
        profileRoot: Path,
        request: SingleInstanceRequest,
        beforeSend: (pid: Long) -> Unit = WindowsForegroundPermission::grant,
    ): Boolean = try {
        val endpoint = SingleInstanceEndpoint.read(profileRoot.resolve(SingleInstanceProtocol.ENDPOINT_FILE))
        if (endpoint == null) {
            false
        } else {
            runCatching { beforeSend(endpoint.pid) }
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.port),
                    SingleInstanceProtocol.IO_TIMEOUT_MS,
                )
                socket.soTimeout = SingleInstanceProtocol.IO_TIMEOUT_MS
                val output = socket.getOutputStream()
                output.write(
                    (SingleInstanceProtocol.encode(endpoint.token, request) + "\n").toByteArray(StandardCharsets.UTF_8),
                )
                output.flush()
                SingleInstanceProtocol.readLine(socket.getInputStream()) == "OK"
            }
        }
    } catch (error: Exception) {
        DesktopLogger.warn("SingleInstance", "Could not forward to running instance: ${error.message}")
        false
    }
}

/**
 * Windows only lets the foreground process hand focus away. The second launch (which the user just started,
 * so it owns the foreground) grants the running instance permission to take it.
 */
internal object WindowsForegroundPermission {
    @Suppress("FunctionName", "ktlint:standard:function-naming")
    private interface User32Foreground : com.sun.jna.Library {
        fun AllowSetForegroundWindow(dwProcessId: Int): Boolean
    }

    fun grant(pid: Long) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            com.sun.jna.Native.load("user32", User32Foreground::class.java).AllowSetForegroundWindow(pid.toInt())
        }
    }
}

/** How the running UI handles a forwarded [SingleInstanceRequest.Open]; mirrors the CLI file-argument parsing. */
enum class ForwardedFileKind {
    AndroidBackup,
    LocalArchive,
    Unsupported,
    ;

    companion object {
        fun of(path: Path): ForwardedFileKind {
            val name = path.fileName?.toString().orEmpty()
            return when {
                name.endsWith(".tachibk", ignoreCase = true) -> AndroidBackup
                name.endsWith(".cbz", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true) -> LocalArchive
                else -> Unsupported
            }
        }
    }
}
