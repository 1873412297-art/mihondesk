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

fun WindowsExtensionProcessManager.routedHost(sourceId: Long): WindowsExtensionProcessManager? = if (routesPackages) {
    sourceHosts[sourceId] ?: throw IpcException("No isolated host registered for source $sourceId")
} else {
    null
}
fun WindowsExtensionProcessManager.readStderrTail(file: File, maxBytes: Int = 16384): String {
    if (!file.exists()) return ""
    return try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val seekPos = maxOf(0L, length - maxBytes)
            raf.seek(seekPos)
            val buffer = ByteArray((length - seekPos).toInt())
            raf.readFully(buffer)
            String(buffer, Charsets.UTF_8).trim().takeLast(8192)
        }
    } catch (_: Exception) {
        ""
    }
}

suspend fun WindowsExtensionProcessManager.ensureRunningSession(): IpcSession {
    if (state != HostProcessState.RUNNING) {
        start()
    }
    return ensureRunning()
}

fun WindowsExtensionProcessManager.ensureRunning(): IpcSession {
    val proc = process
    if (proc != null && !proc.isAlive) {
        state = HostProcessState.CRASHED
        throw IpcException("Extension host process is dead (exitCode=${proc.exitValue()})")
    }
    return session ?: throw IpcException("Extension host is not running (state=$state)")
}

suspend fun WindowsExtensionProcessManager.ping(): Boolean {
    return try {
        val session = ensureRunningSession()
        session.sendRequest(IpcCommands.PING, timeoutMillis = 5_000L) == "pong"
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
suspend fun WindowsExtensionProcessManager.loadExtensionInternal(
    packageFile: File,
    validatedManifest: ExtensionManifest?,
): List<SourceDescriptor> {
    if (routesPackages) {
        val manifest = validatedManifest
            ?: mihon.extension.validator.ExtensionPackageValidator.validatePackage(packageFile)
        return routingMutex.withLock {
            packageHosts[manifest.id]?.let { existing ->
                val current = existing.getSources()
                val sources = if (current.isNotEmpty()) current else existing.loadExtension(packageFile, manifest)
                registerRoutes(existing, sources)
                return@withLock sources
            }
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(manifest.id.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val child = WindowsExtensionProcessManager(
                workingDirectory = File(workingDirectory, "isolated-extensions/$key"),
                customCommand = customCommand,
                memoryLimitBytes = memoryLimitBytes,
                onBrokerHttp = onBrokerHttp,
                sourceSessionFile = sourceSessionFile,
                networkHelper = networkHelper,
                onWebView = onWebView,
                isolatedLeaf = true,
            )
            try {
                val sources = child.loadExtension(packageFile, manifest)
                registerRoutes(child, sources)
                packageHosts[manifest.id] = child
                packageFiles[manifest.id] = packageFile
                sources
            } catch (failure: Throwable) {
                child.close()
                throw failure
            }
        }
    }
    val session = ensureRunningSession()
    val manifest = validatedManifest
        ?: mihon.extension.validator.ExtensionPackageValidator.validatePackage(packageFile)
    brokerPackages.add(manifest.id)
    networkHelper?.registerExtensionDomains(manifest.id, ExtensionPackageDomains.resolve(packageFile, manifest))
    manifest.sources.forEach { source ->
        brokerSources[source.id] = manifest.id
        networkHelper?.registerSourceOwner(source.id, manifest.id)
    }
    val payload = LoadExtensionPayload(
        packagePath = if (Platform.isWindows()) {
            val packages = File(hostWorkingDirectory, "packages").apply { mkdirs() }
            packageFile.copyTo(File(packages, "${manifest.id}.wext"), overwrite = true).absolutePath
        } else {
            packageFile.absolutePath
        },
        workingDir = File(hostWorkingDirectory, manifest.id).absolutePath,
    )
    val res = session.sendRequest(IpcCommands.LOAD_EXTENSION, json.encodeToString(payload))
    return json.decodeFromString<List<SourceDescriptor>>(res).also { sources ->
        sources.forEach { source ->
            brokerSources[source.id] = manifest.id
            networkHelper?.registerSourceOwner(source.id, manifest.id)
        }
    }
}

fun WindowsExtensionProcessManager.registerRoutes(
    host: WindowsExtensionProcessManager,
    sources: List<SourceDescriptor>,
) {
    check(sources.none { sourceHosts[it.id]?.let { owner -> owner !== host } == true }) {
        "Source identity is already owned by another extension host"
    }
    val ids = sources.mapTo(hashSetOf()) { it.id }
    sourceHosts.entries.removeIf { it.value === host && it.key !in ids }
    sources.forEach { sourceHosts[it.id] = host }
}

fun WindowsExtensionProcessManager.prepareBrokerResponse(response: BrokerHttpResponse): BrokerHttpResponse {
    val encoded = response.bodyBase64 ?: return response
    if (encoded.length <= 4 * 1024 * 1024) return response
    val bytes = java.util.Base64.getDecoder().decode(encoded)
    require(bytes.size <= 64 * 1024 * 1024) { "Broker response exceeds 64 MiB" }
    val directory = File(hostWorkingDirectory, "broker-responses").apply { mkdirs() }
    val file = File(directory, "broker-${java.util.UUID.randomUUID()}.bin")
    try {
        file.writeBytes(bytes)
    } catch (error: Exception) {
        file.delete()
        throw error
    }
    return response.copy(body = null, bodyBase64 = null, bodyFileName = file.name)
}

suspend fun WindowsExtensionProcessManager.restart() {
    closeInternal()
    state = HostProcessState.STOPPED
    start()
}

fun WindowsExtensionProcessManager.closeInternal() {
    packageHosts.values.forEach { host ->
        try {
            host.close()
        } catch (error: Throwable) {
            mihon.desktop.logging.DesktopLogger.warn(
                "ExtensionHost",
                "Failed to close an isolated extension host during shutdown: ${error.message}",
                error,
            )
        }
    }
    packageHosts.clear()
    packageFiles.clear()
    sourceHosts.clear()
    val proc = process
    process = null

    try {
        proc?.destroyForcibly()
    } catch (error: Exception) {
        mihon.desktop.logging.DesktopLogger.warn("ExtensionHost", "Failed to terminate stale host process", error)
    }

    try {
        jobObject?.terminate(0)
        jobObject?.close()
    } catch (error: Exception) {
        mihon.desktop.logging.DesktopLogger.warn("ExtensionHost", "Failed to close host job object", error)
    }
    jobObject = null

    try {
        proc?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
    } catch (error: Exception) {
        mihon.desktop.logging.DesktopLogger.warn("ExtensionHost", "Failed to await host process exit", error)
    }

    try {
        session?.close()
    } catch (error: Exception) {
        mihon.desktop.logging.DesktopLogger.warn("ExtensionHost", "Failed to close host IPC session", error)
    }
    session = null
    try {
        sandboxLauncher?.close()
    } catch (error: Throwable) {
        mihon.desktop.logging.DesktopLogger.warn(
            "ExtensionHost",
            "Failed to release the sandbox launcher during shutdown: ${error.message}",
            error,
        )
    }
    sandboxLauncher = null
    brokerPackages.clear()
    brokerSources.clear()
    File(hostWorkingDirectory, "broker-responses").listFiles()?.filter {
        it.isFile && it.name.matches(WindowsExtensionProcessManager.BROKER_RESPONSE_REGEX)
    }?.forEach { it.delete() }
    File(hostWorkingDirectory, "page-images").listFiles()?.filter {
        it.isFile && it.name.matches(WindowsExtensionProcessManager.PAGE_IMAGE_REGEX)
    }?.forEach { it.delete() }
}

fun WindowsExtensionProcessManager.buildDefaultCommand(): List<String> {
    val currentCommand = ProcessHandle.current().info().command().orElse(null)
    val isPackagedExe = currentCommand != null &&
        !currentCommand.endsWith("java.exe", ignoreCase = true) &&
        !currentCommand.endsWith("javaw.exe", ignoreCase = true) &&
        !currentCommand.endsWith("java", ignoreCase = true)

    if (isPackagedExe) {
        return listOf(
            currentCommand,
            "--extension-host",
            "--stdio",
        )
    }

    val javaBin = currentCommand ?: run {
        val javaHome = System.getProperty("java.home")
        val isWindows = Platform.isWindows()
        val ext = if (isWindows) ".exe" else ""
        "$javaHome/bin/java$ext"
    }
    val locations = mutableSetOf<String>()
    fun addLocation(className: String) {
        try {
            val clazz = Class.forName(className)
            val uri = clazz.protectionDomain?.codeSource?.location?.toURI()
            if (uri != null) {
                locations.add(File(uri).absolutePath)
            }
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.debug("ExtensionHost", "Classpath probe failed for $className", error)
        }
    }

    addLocation("mihon.extension.host.MainKt")
    addLocation("mihon.extension.source.WindowsSource")
    addLocation("kotlin.Unit")
    addLocation("kotlinx.coroutines.CoroutineScope")
    addLocation("kotlinx.serialization.json.Json")
    addLocation("org.jsoup.Jsoup")
    addLocation("okhttp3.OkHttpClient")
    addLocation("okio.ByteString")
    addLocation("uy.kohesive.injekt.Injekt")
    addLocation("rx.Observable")
    addLocation("org.objectweb.asm.ClassReader")

    val sysCp = System.getProperty("java.class.path") ?: ""
    if (sysCp.isNotBlank()) {
        sysCp.split(File.pathSeparator).forEach {
            if (it.isNotBlank()) locations.add(File(it).absolutePath)
        }
    }

    val cp = locations.joinToString(File.pathSeparator)
    return listOf(
        javaBin,
        "-Xms16m",
        "-Xmx128m",
        "-XX:ReservedCodeCacheSize=32m",
        "-XX:MaxMetaspaceSize=64m",
        "-XX:+UseSerialGC",
        "-Dfile.encoding=UTF-8",
        "-cp",
        cp,
        "mihon.extension.host.MainKt",
        "--stdio",
    )
}

/** Directory the extension host runs in; on Windows this is the AppContainer's writable sandbox. */
internal fun extensionHostSandboxDirectory(workingDirectory: File): File =
    if (Platform.isWindows()) File(workingDirectory, "sandbox-data") else workingDirectory

/** Shared temp area for large broker request bodies, writable by the host and readable by the parent. */
internal fun extensionBrokerTempRoot(workingDirectory: File): File =
    extensionHostSandboxDirectory(workingDirectory).resolve("broker-temp")
