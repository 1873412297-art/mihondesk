package mihon.desktop.extension
import com.sun.jna.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.ImageFilePayload
import mihon.extension.ipc.ImagePayload
import mihon.extension.ipc.IpcCallbackResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcSession
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnloadExtensionPayload
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.decodeSourcePreferenceValue
import mihon.extension.ipc.decodeSourcePreferences
import mihon.extension.ipc.encodeFilterList
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.io.File

internal suspend fun WindowsExtensionProcessManager.startInternal() = mutex.withLock {
    if (closed) throw IpcException("Extension host has been closed")
    if (state == HostProcessState.RUNNING && process?.isAlive == true && session != null) return@withLock
    if (state == HostProcessState.RUNNING) {
        closeInternal()
        state = HostProcessState.CRASHED
    }
    if (state == HostProcessState.CRASHED) {
        closeInternal()
    }
    state = HostProcessState.STARTING
    mihon.desktop.logging.DesktopLogger.info(
        "ExtensionHost",
        "Starting extension host in ${hostWorkingDirectory.name}",
    )
    lastExitCode = null
    lastStderr = ""

    hostWorkingDirectory.mkdirs()
    val brokerTempRoot = extensionBrokerTempRoot(workingDirectory).apply { mkdirs() }

    val command = customCommand ?: buildDefaultCommand()
    val stderrLog = File(hostWorkingDirectory, "extension-host-stderr.log")
    val proc = if (Platform.isWindows() && WindowsAppContainerLauncher.isSupported()) {
        // Session cookies and headers remain exclusively in the parent HTTP broker.
        val environment = mapOf("MIHON_BROKER_TEMP_DIR" to brokerTempRoot.absolutePath)
        val launcher = WindowsAppContainerLauncher(hostWorkingDirectory, memoryLimitBytes)
        sandboxLauncher = launcher
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO) { launcher.launch(command, environment) }
        } catch (
            cancelled: CancellationException,
        ) {
            launcher.close()
            sandboxLauncher = null
            state = HostProcessState.STOPPED
            throw cancelled
        } catch (error: Throwable) {
            mihon.desktop.logging.DesktopLogger.error(
                "ExtensionHost",
                "Isolated extension host launch failed: ${error.message}",
                error,
            )
            sandboxLauncher = null
            state = HostProcessState.CRASHED
            throw IpcException("Isolated extension host launch failed: ${error.message}", error)
        }
    } else {
        ProcessBuilder(command).directory(hostWorkingDirectory).redirectError(stderrLog).apply {
            environment().remove("MIHON_SOURCE_SESSION_FILE")
            sourceSessionFile?.let { environment()["MIHON_SOURCE_SESSION_FILE"] = it.absolutePath }
            environment()["MIHON_BROKER_TEMP_DIR"] = brokerTempRoot.absolutePath
        }.start()
    }
    this.process = proc
    // Covers the non-sandbox fallback: the host and its descendants die with this JVM.
    mihon.desktop.platform.ChildProcessReaper.track(proc)
    val ipc = IpcSession(
        input = proc.inputStream,
        output = proc.outputStream,
        onCallback = { callback ->
            if (callback.callbackType == IpcCallbacks.BROKER_HTTP) {
                val received = json.decodeFromString<BrokerHttpRequest>(callback.payloadJson)
                // Detached extension coroutines/OkHttp dispatchers can lose thread-local identity.
                // Only a dedicated, authenticated package process may supply the missing owner.
                val request = received.copy(
                    extensionId = received.extensionId ?: if (isolatedLeaf) brokerPackages.singleOrNull() else null,
                )
                val permitted = networkHelper == null || (
                    request.extensionId != null && request.extensionId in brokerPackages &&
                        (request.sourceId == null || brokerSources[request.sourceId] == request.extensionId)
                    )
                val response = if (!permitted) {
                    BrokerHttpResponse(403, error = "Unregistered extension network identity")
                } else {
                    onBrokerHttp?.invoke(request) ?: BrokerHttpResponse(
                        statusCode = 503,
                        error = "Brokered HTTP handler not configured in process manager",
                    )
                }
                IpcCallbackResponse(
                    requestId = callback.requestId,
                    success = true,
                    payloadJson = json.encodeToString(prepareBrokerResponse(response)),
                )
            } else if (callback.callbackType == "webview") {
                val request = json.decodeFromString<mihon.extension.ipc.WebViewRequest>(callback.payloadJson)
                val permitted =
                    request.extensionId in brokerPackages && brokerSources[request.sourceId] == request.extensionId
                val response = if (!permitted) {
                    mihon.extension.ipc.WebViewResponse(false, error = "Unregistered extension WebView identity")
                } else {
                    onWebView?.invoke(request)
                        ?: mihon.extension.ipc.WebViewResponse(false, error = "WebView handler is not configured")
                }
                IpcCallbackResponse(callback.requestId, success = true, payloadJson = json.encodeToString(response))
            } else {
                IpcCallbackResponse(
                    requestId = callback.requestId,
                    success = false,
                    error = "Unknown callback '${callback.callbackType}'",
                )
            }
        },
    )
    this.session = ipc

    scope.launch(Dispatchers.IO) {
        val code = proc.waitFor()
        lastExitCode = code
        lastStderr = readStderrTail(stderrLog)
        if (process === proc && (state == HostProcessState.RUNNING || state == HostProcessState.STARTING)) {
            mihon.desktop.logging.DesktopLogger.error(
                "ExtensionHost",
                "Extension host process terminated with code $code; stderr: $lastStderr",
            )
            state = HostProcessState.CRASHED
            try {
                ipc.close()
            } catch (error: Exception) {
                mihon.desktop.logging.DesktopLogger.warn(
                    "ExtensionHost",
                    "Failed to close crashed host IPC session",
                    error,
                )
            }
        }
    }

    try {
        val pong = ipc.sendRequest(IpcCommands.PING, timeoutMillis = 10_000L)
        if (pong != "pong") {
            throw IpcException("Unexpected ping response from extension host: $pong")
        }
        if (closed) {
            closeInternal()
            throw IpcException("Extension host was closed while starting")
        }
        state = HostProcessState.RUNNING
        mihon.desktop.logging.DesktopLogger.info(
            "ExtensionHost",
            "Extension host running in ${hostWorkingDirectory.name} (pid=${proc.pid()})",
        )
        epoch++
    } catch (cancelled: CancellationException) {
        closeInternal()
        state = HostProcessState.STOPPED
        throw cancelled
    } catch (e: Exception) {
        closeInternal()
        // Give a moment for stderr to flush to file
        kotlinx.coroutines.delay(300)
        lastStderr = readStderrTail(stderrLog)
        state = HostProcessState.CRASHED
        val stderrInfo = if (lastStderr.isNotEmpty()) "\nStderr: $lastStderr" else ""
        mihon.desktop.logging.DesktopLogger.error(
            "ExtensionHost",
            "Failed to establish IPC handshake with extension host: ${e.message}$stderrInfo",
            e,
        )
        throw IpcException("Failed to establish IPC handshake with extension host: ${e.message}$stderrInfo", e)
    }
}
