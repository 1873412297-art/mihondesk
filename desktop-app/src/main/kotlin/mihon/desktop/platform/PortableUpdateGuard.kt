package mihon.desktop.platform

import mihon.desktop.cli.DesktopCommand
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class PortableUpdatePendingException(val applicationDirectory: Path) :
    IllegalStateException("A portable update is still pending in $applicationDirectory")

/** Coordinates with mihondesk-updater.ps1; this is a lifecycle guard, not an authorization boundary. */
object PortableUpdateGuard {
    private const val GUARD_FILE = ".mihon-update-in-progress"
    private const val TOKEN_ENVIRONMENT = "MIHON_PORTABLE_UPDATE_TOKEN"

    fun requireAllowed(applicationDirectory: Path, command: DesktopCommand, environment: Map<String, String>) {
        val guard = applicationDirectory.resolve(GUARD_FILE)
        // Inability to inspect a guard fails closed; a missing file is the normal startup path.
        if (Files.notExists(guard, LinkOption.NOFOLLOW_LINKS)) return
        val token = runCatching { Files.readString(guard).trim() }.getOrNull()
        if (command == DesktopCommand.Version && token != null && token.matches(Regex("[a-f0-9]{32}")) &&
            environment[TOKEN_ENVIRONMENT] == token
        ) {
            return
        }
        throw PortableUpdatePendingException(applicationDirectory)
    }
}
