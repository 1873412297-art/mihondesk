package mihon.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
import mihon.desktop.platform.SingleInstanceClient
import mihon.desktop.platform.SingleInstanceForwarding
import mihon.desktop.platform.SingleInstanceRequest
import mihon.desktop.platform.SingleInstanceServer
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.MihonDesktopApp
import mihon.desktop.ui.splash.FirstRunSplash
import mihon.desktop.ui.splash.StartupHandoff
import mihon.desktop.ui.splash.StartupWindow
import mihon.desktop.ui.splash.processUptimeMillis
import java.io.PrintStream
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Must be set before the first ProxySelector.getDefault() so "System" proxy mode sees the OS proxy.
    if (System.getProperty("java.net.useSystemProxies") == null) {
        System.setProperty("java.net.useSystemProxies", "true")
    }
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
    var requestedCommand: DesktopCommand? = null
    val exitCode = try {
        val parsedCommand = DesktopCommandParser.parse(args)
        requestedCommand = parsedCommand
        interactiveStartup = parsedCommand == DesktopCommand.LaunchUi
        PortableUpdateGuard.requireAllowed(executableDirectory, parsedCommand, System.getenv())
        val profile = DesktopProfileDirectories.resolve(args, System.getenv(), executableDirectory)
        DesktopLogger.init(profile.logs)
        DesktopLogger.info("Main", "Starting mihondesk with args: ${args.joinToString(" ")}")
        DesktopProfileLock.acquire(profile.root).use {
            // Close the race with an updater creating the guard after our initial check.
            PortableUpdateGuard.requireAllowed(executableDirectory, parsedCommand, System.getenv())
            val startupPreferences = runCatching {
                DesktopPreferenceStore(profile.root.resolve("preferences.properties")).load()
            }.getOrNull()
            startupLanguage = startupPreferences?.language ?: AppLanguage.System
            if (parsedCommand == DesktopCommand.LaunchUi) {
                return@use launchUiWithStartupWindow(
                    args = args,
                    executableDirectory = executableDirectory,
                    profileRoot = profile.root,
                    startupPreferences = startupPreferences,
                )
            }
            val runtime = DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory)
            executeDesktopRuntime(
                runtime = runtime,
                runCommand = { activeRuntime, desktopCommand ->
                    DesktopCommandRunner(activeRuntime, System.out).run(desktopCommand)
                },
                launchUi = { activeRuntime ->
                    configureDesktopRendering()
                    // Requests may arrive before the UI subscribes; keep a small buffer of the newest ones.
                    val forwarded = Channel<SingleInstanceRequest>(16, BufferOverflow.DROP_OLDEST)
                    val server = runCatching {
                        SingleInstanceServer.start(profile.root) { request ->
                            check(forwarded.trySend(request).isSuccess) { "UI is no longer accepting requests" }
                        }
                    }.onFailure {
                        DesktopLogger.warn("Main", "Single-instance listener unavailable: ${it.message}")
                    }.getOrNull()
                    try {
                        application(exitProcessOnExit = false) {
                            MihonDesktopApp(activeRuntime, forwarded.receiveAsFlow())
                        }
                    } finally {
                        forwarded.close()
                        server?.close()
                    }
                },
            )
        }
    } catch (error: ProfileInUseException) {
        handleProfileInUse(
            args = args,
            command = requestedCommand,
            profileRoot = error.profile,
            workingDirectory = Path.of(System.getProperty("user.dir")),
            out = System.out,
        )
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

/**
 * A launch lost the profile lock to a running instance. Interactive launches (plain UI start, file
 * association) are forwarded so the running window comes to the front; background tasks keep their
 * silent skip (exit 0) and everything else, or a failed forward, keeps the historical exit 75.
 */
internal fun handleProfileInUse(
    args: Array<String>,
    command: DesktopCommand?,
    profileRoot: Path,
    workingDirectory: Path,
    out: PrintStream,
    forward: (Path, SingleInstanceRequest) -> Boolean = { root, request -> SingleInstanceClient.send(root, request) },
): Int {
    val background = "--background-update" in args || "--background-backup" in args
    val request = if (background || command == null) {
        null
    } else {
        SingleInstanceForwarding.requestFor(args, command, workingDirectory)
    }
    if (request != null && forward(profileRoot, request)) {
        DesktopLogger.info("Main", "Profile in use; forwarded ${request::class.simpleName} to running instance")
        out.println("""{"command":"startup","status":"FORWARDED","category":"PROFILE_IN_USE"}""")
        return 0
    }
    DesktopLogger.warn("Main", "Profile in use; skipping startup")
    out.println("""{"command":"startup","status":"SKIPPED","category":"PROFILE_IN_USE"}""")
    return if (background) 0 else 75
}

/**
 * Interactive launch: the main window frame with splash v3 opens immediately while the runtime
 * (database, sync server, extension host...) is created on a background thread; the full app
 * window takes over once the splash finished and the runtime is ready.
 */
private fun launchUiWithStartupWindow(
    args: Array<String>,
    executableDirectory: Path,
    profileRoot: Path,
    startupPreferences: DesktopPreferences?,
): Int {
    configureDesktopRendering()
    // Requests may arrive before the UI subscribes; keep a small buffer of the newest ones.
    val forwarded = Channel<SingleInstanceRequest>(16, BufferOverflow.DROP_OLDEST)
    val server = runCatching {
        SingleInstanceServer.start(profileRoot) { request ->
            check(forwarded.trySend(request).isSuccess) { "UI is no longer accepting requests" }
        }
    }.onFailure {
        DesktopLogger.warn("Main", "Single-instance listener unavailable: ${it.message}")
    }.getOrNull()
    val playSplash = startupPreferences != null &&
        FirstRunSplash.shouldShow(DesktopPreferenceStore(profileRoot.resolve("preferences.properties")))
    val runtimeResult = mutableStateOf<Result<DesktopRuntime>?>(null)
    val initStart = System.nanoTime()
    val initThread = thread(name = "mihon-runtime-init") {
        val result = runCatching { DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory) }
        DesktopLogger.info(
            "Startup",
            "Runtime initialised in ${(System.nanoTime() - initStart) / 1_000_000} ms " +
                "(${processUptimeMillis()} ms since process start)",
        )
        runtimeResult.value = result
    }
    var failure: Throwable? = null
    try {
        application(exitProcessOnExit = false) {
            var handOff by remember { mutableStateOf<Boolean?>(null) }
            var startupWindowOpen by remember { mutableStateOf(true) }
            val result = runtimeResult.value
            val runtime = result?.getOrNull()
            if (result != null && result.isFailure) {
                LaunchedEffect(result) {
                    failure = result.exceptionOrNull()
                    exitApplication()
                }
            } else {
                if (startupWindowOpen) {
                    StartupWindow(
                        preferences = startupPreferences ?: DesktopPreferences(),
                        playSplash = playSplash,
                        ready = runtime != null,
                        onHandOff = { skipped -> handOff = skipped },
                        onCloseRequest = {
                            DesktopLogger.info("Main", "Startup window closed before the app was ready")
                            exitApplication()
                        },
                    )
                }
                val skipped = handOff
                if (runtime != null && skipped != null) {
                    MihonDesktopApp(
                        runtime,
                        forwarded.receiveAsFlow(),
                        startupHandoff = StartupHandoff(skipped) {
                            startupWindowOpen = false
                            DesktopLogger.info("Startup", "Main window shown at ${processUptimeMillis()} ms")
                        },
                    )
                }
            }
        }
    } finally {
        forwarded.close()
        server?.close()
        // A close during start-up waits for initialisation so the runtime shuts down cleanly.
        initThread.join()
        runtimeResult.value?.getOrNull()?.let { runCatching { it.close() } }
    }
    failure?.let { throw it }
    return 0
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

private fun windowsAppsUseDarkTheme(): Boolean = runCatching {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return false
    com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(
        com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "AppsUseLightTheme",
    ) == 0
}.getOrDefault(false)
