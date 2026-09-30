package mihon.desktop.cli

import java.nio.file.Path

sealed interface DesktopCommand {
    data object RemoveBackgroundTasks : DesktopCommand
    data object BackgroundUpdate : DesktopCommand
    data object BackgroundBackup : DesktopCommand
    data object LaunchUi : DesktopCommand
    data object FoundationSmoke : DesktopCommand
    data object Help : DesktopCommand
    data object Version : DesktopCommand
    data class ImportBackup(val path: Path) : DesktopCommand
    data class ExportBackup(val path: Path) : DesktopCommand
    data class ImportLocal(val path: Path) : DesktopCommand
    data object ListLibraryJson : DesktopCommand
    data class VerifyReader(val fixtureRoot: Path) : DesktopCommand
}

/**
 * Whether the command can deliver a user-visible Windows notification, and therefore needs a tray icon.
 *
 * Windows records a persistent per-executable tray registration (`HKCU\Control Panel\NotifyIconSettings`)
 * the first time a process adds a tray icon, and keeps it after the executable is deleted. Only
 * [DesktopCommand.LaunchUi], [DesktopCommand.BackgroundUpdate] and [DesktopCommand.BackgroundBackup] can
 * actually notify the user; every other command (version, help, export, import, smoke test, reader
 * verification, background-task removal) is a plain command-line run that must not touch the shell.
 */
val DesktopCommand.needsNotificationTray: Boolean
    get() = when (this) {
        DesktopCommand.LaunchUi, DesktopCommand.BackgroundUpdate, DesktopCommand.BackgroundBackup -> true
        DesktopCommand.Help,
        DesktopCommand.Version,
        DesktopCommand.FoundationSmoke,
        DesktopCommand.ListLibraryJson,
        DesktopCommand.RemoveBackgroundTasks,
        is DesktopCommand.ImportBackup,
        is DesktopCommand.ExportBackup,
        is DesktopCommand.ImportLocal,
        is DesktopCommand.VerifyReader,
        -> false
    }

class CommandLineException(
    message: String,
    val argument: String? = null,
    cause: Throwable? = null,
    val exitCode: Int = 2,
) : IllegalArgumentException(message, cause)

object DesktopCommandParser {
    fun parse(
        args: Array<String>,
        environment: Map<String, String> = System.getenv(),
    ): DesktopCommand {
        val commands = buildList {
            args.forEach { argument ->
                when {
                    argument == "--remove-background-tasks" -> add(DesktopCommand.RemoveBackgroundTasks)
                    argument == "--background-update" -> add(DesktopCommand.BackgroundUpdate)
                    argument == "--background-backup" -> add(DesktopCommand.BackgroundBackup)
                    argument == "--portable" -> Unit
                    argument.startsWith("--data-dir=") -> Unit
                    argument == "--help" || argument == "-h" || argument == "/?" -> add(DesktopCommand.Help)
                    argument == "--version" || argument == "-v" -> add(DesktopCommand.Version)
                    argument == "--smoke-test" -> add(DesktopCommand.FoundationSmoke)
                    argument == "--list-library-json" -> add(DesktopCommand.ListLibraryJson)
                    argument.startsWith("--verify-reader=") -> {
                        if (environment[READER_VERIFY_ENVIRONMENT] != "1") {
                            throw CommandLineException(
                                "--verify-reader is an internal verification command",
                                "--verify-reader",
                            )
                        }
                        add(DesktopCommand.VerifyReader(parsePath(argument, "--verify-reader=")))
                    }
                    argument.startsWith("--import-backup=") -> add(
                        DesktopCommand.ImportBackup(parsePath(argument, "--import-backup=")),
                    )
                    argument.startsWith("--export-backup=") -> add(
                        DesktopCommand.ExportBackup(parsePath(argument, "--export-backup=")),
                    )
                    argument.startsWith("--import-local=") -> add(
                        DesktopCommand.ImportLocal(parsePath(argument, "--import-local=")),
                    )
                    argument.endsWith(".tachibk", ignoreCase = true) -> add(
                        DesktopCommand.ImportBackup(tryParsePath(argument)),
                    )
                    argument.endsWith(".cbz", ignoreCase = true) || argument.endsWith(".zip", ignoreCase = true) -> add(
                        DesktopCommand.ImportLocal(tryParsePath(argument)),
                    )
                    else -> throw CommandLineException("Unknown or incomplete argument", safeArgument(argument))
                }
            }
        }
        if (commands.size > 1) {
            throw CommandLineException("Only one headless command may be specified")
        }
        return commands.singleOrNull() ?: DesktopCommand.LaunchUi
    }

    private fun tryParsePath(value: String): Path = try {
        Path.of(value)
    } catch (error: RuntimeException) {
        throw CommandLineException("Argument contains an invalid path", value, error)
    }

    private fun parsePath(argument: String, prefix: String): Path {
        val value = argument.removePrefix(prefix)
        if (value.isBlank()) throw CommandLineException("${prefix.removeSuffix("=")} requires a non-blank path", prefix)
        return try {
            Path.of(value)
        } catch (error: RuntimeException) {
            throw CommandLineException("${prefix.removeSuffix("=")} contains an invalid path", prefix, error)
        }
    }

    private fun safeArgument(argument: String): String? =
        argument.takeIf { it.startsWith("--") }?.substringBefore('=')

    private const val READER_VERIFY_ENVIRONMENT = "MIHON_W_READER_VERIFY"
}
