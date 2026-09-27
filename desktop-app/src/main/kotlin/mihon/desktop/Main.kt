package mihon.desktop

import androidx.compose.ui.window.application
import kotlinx.coroutines.CancellationException
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.platform.DesktopProfileDirectories
import mihon.desktop.platform.DesktopProfileLock
import mihon.desktop.platform.PortableUpdateGuard
import mihon.desktop.platform.PortableUpdatePendingException
import mihon.desktop.platform.ProfileInUseException
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.ui.MihonDesktopApp
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        if (throwable !is CancellationException) {
            DesktopLogger.error("UncaughtException", "Uncaught exception on thread ${thread.name}", throwable)
        }
    }
    if ("--extension-host" in args) {
        val hostArgs = args.filter { it != "--extension-host" }.toTypedArray()
        mihon.extension.host.main(hostArgs)
        return
    }
    val command = ProcessHandle.current().info().command().orElse(null)
    val executableDirectory = command?.let(Path::of)?.parent
        ?: Path.of(System.getProperty("user.dir"))
    var interactiveStartup = false
    var startupLanguage = AppLanguage.System
    val exitCode = try {
        val requestedCommand = DesktopCommandParser.parse(args)
        interactiveStartup = requestedCommand == DesktopCommand.LaunchUi
        PortableUpdateGuard.requireAllowed(executableDirectory, requestedCommand, System.getenv())
        val profile = DesktopProfileDirectories.resolve(args, System.getenv(), executableDirectory)
        DesktopLogger.init(profile.logs)
        DesktopLogger.info("Main", "Starting mihondesk with args: ${args.joinToString(" ")}")
        DesktopProfileLock.acquire(profile.root).use {
            // Close the race with an updater creating the guard after our initial check.
            PortableUpdateGuard.requireAllowed(executableDirectory, requestedCommand, System.getenv())
            startupLanguage = runCatching {
                DesktopPreferenceStore(profile.root.resolve("preferences.properties")).load().language
            }.getOrDefault(AppLanguage.System)
            val runtime = DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory)
            executeDesktopRuntime(
                runtime = runtime,
                runCommand = { activeRuntime, desktopCommand ->
                    DesktopCommandRunner(activeRuntime, System.out).run(desktopCommand)
                },
                launchUi = { activeRuntime ->
                    configureDesktopRendering()
                    application(exitProcessOnExit = false) {
                        MihonDesktopApp(activeRuntime)
                    }
                },
            )
        }
    } catch (_: ProfileInUseException) {
        DesktopLogger.warn("Main", "Profile in use; skipping startup")
        System.out.println("""{"command":"startup","status":"SKIPPED","category":"PROFILE_IN_USE"}""")
        if ("--background-update" in args || "--background-backup" in args) 0 else 75
    } catch (error: CommandLineException) {
        DesktopLogger.error("Main", "Command line error: ${error.message}", error)
        DesktopCommandRunner.writeCommandLineError(System.out, error)
        error.exitCode
    } catch (e: Throwable) {
        DesktopLogger.error("Main", "Fatal startup error", e)
        e.printStackTrace(System.err)
        if (!reportStartupRecoveryFailure(e, interactiveStartup, System.out, DesktopStrings.resolve(startupLanguage))) {
            DesktopCommandRunner.writeStartupFailure(System.out)
        }
        if (e is PortableUpdatePendingException) 75 else 1
    }
    DesktopLogger.info("Main", "Exiting mihondesk with exit code: $exitCode")
    DesktopLogger.close()
    exitProcess(exitCode)
}

internal fun executeDesktopRuntime(
    runtime: DesktopRuntime,
    runCommand: (DesktopRuntime, DesktopCommand) -> Int,
    launchUi: (DesktopRuntime) -> Unit,
): Int = runtime.use { activeRuntime ->
    when (val command = activeRuntime.command) {
        DesktopCommand.LaunchUi -> {
            launchUi(activeRuntime)
            0
        }
        else -> runCommand(activeRuntime, command)
    }
}
