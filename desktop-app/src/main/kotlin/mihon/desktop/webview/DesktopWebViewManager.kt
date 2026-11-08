package mihon.desktop.webview

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.logging.DesktopLogger
import mihon.extension.ipc.BrokerHttpRequest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A browser that has already opened stays up. A later page error, including one from a login
 * redirect, must not close the verification window.
 */
internal fun browserErrorClosesSession(sessionReady: Boolean): Boolean = !sessionReady

/** A browser process is created only by open(), one capability and process per source session. */
class DesktopWebViewManager(
    internal val browserRuntime: Path,
    internal val hostDistribution: Path,
    internal val cacheDirectory: Path,
    internal val network: DesktopNetworkHelper,
    internal val cookies: DesktopCookieStore,
    internal val idleTimeoutMillis: Long = 120_000,
) : AutoCloseable {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val sessions = ConcurrentHashMap<String, BrowserSession>()
    val activeSessionCount: Int get() = sessions.size
    internal val extensionSessions = ConcurrentHashMap<String, BrowserSession>()
    suspend fun open(
        sourceId: Long,
        extensionId: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Long = 30_000,
    ): BrowserSession {
        require(url.toHttpUrl().scheme in setOf("http", "https"))
        require(extensionId.isNotBlank())
        val release = browserRuntime.resolve("release")
        check(Files.isRegularFile(release) && Files.readString(release).contains("JAVA_VERSION=\"21.")) {
            "Independent Java 21 browser runtime is missing"
        }
        check(Files.isRegularFile(browserRuntime.resolve("bin/libcef.dll"))) {
            "Browser Chromium native runtime is missing"
        }
        check(Files.isDirectory(hostDistribution.resolve("lib"))) { "Browser host distribution is missing" }
        val id = UUID.randomUUID().toString()
        val token = ByteArray(32).also(SecureRandom()::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val cache = cacheDirectory.resolve(id)
        Files.createDirectories(cache)
        val session = BrowserSession(id, sourceId, extensionId, token, url, cache)
        sessions[id] = session
        return try {
            session.launch(mapOf("User-Agent" to network.userAgentFor(url)) + headers)
            withTimeout(timeoutMillis) { session.ready.await() }
            session
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    inner class BrowserSession internal constructor(
        val sessionId: String,
        val sourceId: Long,
        private val extensionId: String,
        private val token: String,
        private val initialUrl: String,
        private val cache: Path,
    ) : AutoCloseable {
        internal val ready = CompletableDeferred<Unit>()
        private val loaded = CompletableDeferred<String>()
        private val closed = AtomicBoolean(false)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
        private val updates = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 64)
        val events = updates.asSharedFlow()
        private var browserUserAgent: String? = null
        private val currentState = kotlinx.coroutines.flow.MutableStateFlow(BrowserEvent("starting", ""))
        val state: kotlinx.coroutines.flow.StateFlow<BrowserEvent> = currentState
        private val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "webview-broker-$sessionId").apply {
                isDaemon =
                    true
            }
        }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var process: Process? = null
        private val idleWatch = BrowserIdleWatch(scope, idleTimeoutMillis, sessionId, ::close)
        private var processJob: mihon.desktop.extension.WindowsJobObject? = null
        private var writer: java.io.BufferedWriter? = null
        val processId: Long? get() = process?.pid()

        internal fun launch(headers: Map<String, String>) {
            server.executor = executor
            server.createContext("/request") { exchange ->
                try {
                    require(exchange.requestMethod == "POST")
                    val bytes = exchange.requestBody.readNBytes(8 * 1024 * 1024 + 1)
                    require(bytes.size <= 8 * 1024 * 1024)
                    val request = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    require(
                        request.text("token") == token && request.text("sessionId") == sessionId &&
                            request.text("sourceId") == sourceId.toString(),
                    )
                    val response = runBlocking {
                        network.executeBrokeredRequest(
                            BrokerHttpRequest(
                                method = request.text("method"),
                                url = request.text("url"),
                                headers = (request["headers"] as? JsonObject).orEmpty().mapValues {
                                    it.value.jsonPrimitive.content
                                },
                                extensionId = extensionId,
                                sourceId = sourceId,
                                bodyBase64 = request.text("bodyBase64").takeIf(String::isNotBlank),
                            ),
                            browsing = true,
                        )
                    }
                    val output = Json.encodeToString(response).toByteArray()
                    exchange.sendResponseHeaders(200, output.size.toLong())
                    exchange.responseBody.use { it.write(output) }
                } catch (failure: Exception) {
                    rejectBrokerRequest(exchange, failure)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val command = browserHostCommand(browserRuntime, hostDistribution)
            val child = mihon.desktop.platform.ChildProcessReaper.track(
                ProcessBuilder(command).redirectError(cache.resolve("host-error.log").toFile()).start(),
            )
            process = child
            idleWatch.start()
            // The helper waits for stdin configuration, so no Chromium children can escape
            // before it is assigned to this kill-on-close process tree.
            val job = mihon.desktop.extension.WindowsJobObject(2L * 1024 * 1024 * 1024, activeProcessLimit = 32)
            processJob = job
            check(job.assignProcess(child)) { "Unable to contain browser process tree" }
            writer = child.outputStream.bufferedWriter(Charsets.UTF_8)
            val args = BrowserNetworkArguments.forPolicy(network.currentPolicy())
            val profile = cookies.loadAll(extensionId)
            send(browserConfiguration(initialUrl, cache, server.address.port, headers, profile, args))
            scope.launch {
                child.waitFor()
                close()
            }
            scope.launch {
                try {
                    child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.startsWith(
                                    "MIHON_WEBVIEW ",
                                )
                            ) {
                                onEvent(Json.parseToJsonElement(line.removePrefix("MIHON_WEBVIEW ")).jsonObject)
                            }
                        }
                    }
                } catch (failure: Exception) {
                    ready.completeExceptionally(failure)
                } finally {
                    val failure = IllegalStateException("Browser helper exited")
                    ready.completeExceptionally(failure)
                    loaded.completeExceptionally(failure)
                    pending.values.forEach { it.completeExceptionally(failure) }
                    close()
                }
            }
        }
        internal fun touch() = idleWatch.touch()

        private fun onEvent(message: JsonObject) {
            touch()

            if (message.text("sessionId") != sessionId || message.text("sourceId") != sourceId.toString()) return
            val type = message.text("type")
            val value = message.text("value")
            val id = message.text("id")
            when (type) {
                "ready" -> ready.complete(Unit)
                "loaded" -> if (value.startsWith("http")) loaded.complete(value)
                "result", "cookiesDone" -> pending.remove(id)?.complete(value)
                "userAgent" -> {
                    if (value.isNotBlank()) {
                        browserUserAgent = value
                        cookies.noteBrowserUserAgent(initialUrl.toHttpUrl().host, value)
                    }
                }
                "cookie" -> importCookie(Json.parseToJsonElement(value).jsonObject)
                "error" -> {
                    val failure = IllegalStateException(value)
                    if (browserErrorClosesSession(ready.isCompleted)) {
                        ready.completeExceptionally(failure)
                        loaded.completeExceptionally(failure)
                        pending.values.forEach { it.completeExceptionally(failure) }
                        close()
                    } else {
                        pending.values.forEach { it.completeExceptionally(failure) }
                    }
                }
                "closed" -> close()
            }
            val event = BrowserEvent(type, value)
            if (type in setOf("ready", "loaded", "error", "closed")) currentState.value = event
            updates.tryEmit(event)
        }
        private fun importCookie(value: JsonObject) {
            // The login window is the user browsing, so keep whatever session it collected.
            // Extension requests still cannot call a host the source did not declare.
            val domain = value.text("domain").removePrefix(".")
            val name = value.text("name")
            if (domain.isBlank() || name.isBlank()) return
            DesktopLogger.debug("DesktopWebViewManager", "Keeping browser cookie $name for $domain ($extensionId)")
            if (name == "cf_clearance") {
                browserUserAgent?.let { cookies.noteBrowserUserAgent(domain, it) }
            }
            val secure = value.text("secure") == "true"
            val saved = runCatching {
                val builder = Cookie.Builder().name(name).value(value.text("value")).path(
                    value.text("path").ifBlank { "/" },
                )
                if (value.text("domain").startsWith('.')) builder.domain(domain) else builder.hostOnlyDomain(domain)
                if (secure) builder.secure()
                if (value.text("httpOnly") == "true") builder.httpOnly()
                value.text("expiresAt").toLongOrNull()?.takeIf { it > 0 }?.let(builder::expiresAt)
                cookies.saveFromResponse(
                    extensionId,
                    "${if (secure) "https" else "http"}://$domain/".toHttpUrl(),
                    listOf(builder.build()),
                )
            }
            saved.exceptionOrNull()?.let {
                DesktopLogger.warn("DesktopWebViewManager", "Dropped browser cookie $name for $domain", it)
            }
        }
        suspend fun awaitLoaded(timeoutMillis: Long = 30_000): String = try {
            withTimeout(timeoutMillis) { loaded.await() }
        } catch (timeout: TimeoutCancellationException) {
            close()
            throw IllegalStateException("Browser page did not load within $timeoutMillis ms", timeout)
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        }
        suspend fun evaluate(
            script: String,
            timeoutMillis: Long = 10_000,
        ): String {
            touch()
            return query("evaluate", "script", script, timeoutMillis)
        }
        internal suspend fun setCookie(url: String, cookie: Cookie) {
            query("setCookie", "", "", 5_000, cookieMessage(url, cookie))
        }
        suspend fun exportCookies(timeoutMillis: Long = 5_000) {
            query("cookies", "", "", timeoutMillis)
        }
        private suspend fun query(
            type: String,
            key: String,
            value: String,
            timeout: Long,
            extra: JsonObject = JsonObject(emptyMap()),
        ): String {
            touch()
            val id = UUID.randomUUID().toString()
            val result = CompletableDeferred<String>()
            pending[id] = result
            return try {
                send(
                    buildJsonObject {
                        put("type", type)
                        put("id", id)
                        if (key.isNotEmpty()) put(key, value)
                        extra.forEach { (field, content) -> put(field, content) }
                    },
                )
                withTimeout(timeout) { result.await() }
            } catch (cancelled: CancellationException) {
                close()
                throw cancelled
            } finally {
                pending.remove(id)
            }
        }

        @Synchronized private fun send(message: JsonObject) {
            check(!closed.get()) { "Browser session closed" }
            val payload = JsonObject(
                message +
                    mapOf(
                        "sessionId" to JsonPrimitive(sessionId),
                        "sourceId" to JsonPrimitive(sourceId),
                        "token" to JsonPrimitive(token),
                    ),
            )
            writer?.apply {
                write(payload.toString())
                newLine()
                flush()
            } ?: error("Browser process has not started")
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            idleWatch.close()
            currentState.value = BrowserEvent("closed", "")
            runCatching { writer?.close() }.onFailure {
                DesktopLogger.warn("DesktopWebViewManager", "Failed to close browser input", it)
            }
            runCatching {
                process?.let { child ->
                    val descendants = child.descendants().toList()
                    descendants.forEach { it.destroy() }
                    child.destroy()
                    if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly()
                    descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
                }
            }.onFailure {
                DesktopLogger.warn("DesktopWebViewManager", "Failed to stop browser process", it)
            }
            runCatching { processJob?.close() }.onFailure {
                DesktopLogger.warn("DesktopWebViewManager", "Failed to close browser process job", it)
            }
            processJob = null
            runCatching { server.stop(0) }.onFailure {
                DesktopLogger.warn("DesktopWebViewManager", "Failed to stop browser server", it)
            }
            executor.shutdownNow()
            sessions.remove(sessionId)
            extensionSessions.entries.removeIf { it.value === this }
            // The path was created under this manager's cache root for this UUID only.
            val root = cacheDirectory.toAbsolutePath().normalize()
            val owned = cache.toAbsolutePath().normalize()
            if (owned.startsWith(root) && owned != root) {
                runCatching {
                    Files.walk(owned).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }.onFailure { DesktopLogger.warn("DesktopWebViewManager", "Failed to clear browser cache", it) }
            }
        }
    }
    override fun close() {
        sessions.values.toList().forEach { it.close() }
        scope.cancel()
    }
}
data class BrowserEvent(val type: String, val value: String)
private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
