package mihon.webview

import com.jetbrains.cef.JCefAppConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JFrame
import javax.swing.SwingUtilities

internal class WebViewSession(
    private val config: JsonObject,
    private val identity: SessionIdentity,
    private val events: Events,
) : AutoCloseable {
    private val broker = URI(config.string("broker"))
    private val app: CefApp
    private val client: org.cef.CefClient
    private val browser: CefBrowser
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    private val reportedBrowserUserAgent = java.util.concurrent.atomic.AtomicBoolean(false)
    private val window = JFrame("mihondesk - Web verification")
    init {
        require(broker.host == "127.0.0.1" && broker.scheme == "http")
        val cef = JCefAppConfig.getInstance()
        cef.cefSettings.cache_path = config.string("cache")
        cef.cefSettings.log_file = Path.of(config.string("cache"), "cef.log").toString()
        // The login window uses Chromium's own network stack. Replaying the page through OkHttp
        // keeps the extension allowlist, but a site that checks the browser TLS fingerprint
        // (Cloudflare) then stops on a human check. The extension's own requests never come here,
        // and the User-Agent is Chromium's so it matches that stack.
        cef.appArgsAsList.addAll(
            listOf(
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-sync",
            ),
        )
        // Proxy and DoH switches chosen by the main process, so this window shares the source route.
        cef.appArgsAsList.addAll(BrowserSwitches.accepted(config))
        check(CefApp.startup(cef.appArgs)) { "JCEF startup failed" }
        app = CefApp.getInstance(cef.appArgs, cef.cefSettings)
        // Native startup finishes on another thread. The cookie manager throws if used before that.
        val initialized = CompletableFuture<Unit>()
        app.onInitialization({ state ->
            if (state == CefApp.CefAppState.INITIALIZED) initialized.complete(Unit)
        }, false)
        try {
            initialized.get(20, TimeUnit.SECONDS)
        } catch (failure: TimeoutException) {
            throw IllegalStateException("JCEF did not finish initializing", failure)
        }
        client = app.createClient()
        val router = CefMessageRouter.create()
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: CefQueryCallback?,
                ): Boolean {
                    val value =
                        runCatching { wireJson.parseToJsonElement(request.orEmpty()).jsonObject }.getOrNull()
                            ?: return false
                    events.send("result", value.string("id"), value.string("value"))
                    callback?.success("")
                    return true
                }
            },
            true,
        )
        client.addMessageRouter(router)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    events.send("loaded", value = browser?.url.orEmpty())
                    // Login finishes on a redirect. Save that page's cookies while the window is
                    // still open; waiting for the button races the window closing.
                    if (browser?.url?.startsWith("http") == true) exportCookies("__sync__")
                }
            }
            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                // Login handoffs redirect and abort the previous navigation. That abort is not a
                // failure; reporting it makes the parent close the verification window.
                if (frame?.isMain == true && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    events.send("error", value = "$errorCode: $errorText")
                }
            }
        })
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean,
            ): Boolean =
                request?.url?.let { !allowedScheme(it) } ?: true
            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: BoolRef?,
            ): CefResourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
                override fun onBeforeResourceLoad(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                ): Boolean {
                    val target = request ?: return true
                    if (!allowedScheme(target.url)) return true
                    val page = runCatching { URI(config.string("url")) }.getOrNull()
                    val current = runCatching { URI(target.url) }.getOrNull()
                    if (page != null && current?.scheme in setOf("http", "https") &&
                        current?.authority == page.authority
                    ) {
                        (config["headers"] as? JsonObject)?.forEach { (key, value) ->
                            if (!key.equals("User-Agent", true) && !key.equals("Cookie", true)) {
                                target.setHeaderByName(key, value.jsonPrimitive.content, false)
                            }
                        }
                    }
                    val ua = target.getHeaderByName("User-Agent")
                    if (!ua.isNullOrBlank() && reportedBrowserUserAgent.compareAndSet(false, true)) {
                        events.send("userAgent", value = ua)
                    }
                    return false
                }
            }
            override fun onRenderProcessTerminated(
                browser: CefBrowser?,
                status: CefRequestHandler.TerminationStatus?,
                errorCode: Int,
                errorString: String?,
            ) {
                events.send("error", value = "Renderer terminated: $status ($errorCode)")
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) {
                browser.loadURL(config.string("url"))
            }
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?,
            ): Boolean = true
        })
        browser = client.createBrowser("about:blank", false, false)
        SwingUtilities.invokeAndWait {
            window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent?) {
                    exportCookies("__closing__")
                }
            })
            window.contentPane.add(browser.uiComponent, BorderLayout.CENTER)
            window.setSize(1000, 760)
            window.isVisible = true
        }
        val cookies = config["cookies"] as? JsonArray ?: JsonArray(emptyList())
        cookies.forEach { item ->
            val cookie = item.jsonObject
            val expires = cookie.string("expiresAt").toLongOrNull() ?: 0
            CefCookieManager.getGlobalManager().setCookie(
                cookie.string("url").ifBlank { config.string("url") },
                CefCookie(
                    cookie.string("name"), cookie.string("value"), cookie.string("domain"), cookie.string("path"),
                    cookie.string("secure") == "true",
                    cookie.string("httpOnly") == "true",
                    Date(),
                    Date(),
                    expires > 0,
                    if (expires > 0) Date(expires) else Date(),
                ),
            )
        }
        events.send("ready", value = System.getProperty("java.version"))
    }
    fun command(message: JsonObject) {
        require(identity.accepts(message)) { "Session identity mismatch" }
        when (message.string("type")) {
            "navigate" -> {
                val url = message.string("url")
                require(allowedScheme(url))
                browser.loadURL(url)
            }
            "evaluate" -> {
                val id = JsonPrimitive(message.string("id")).toString()
                val script = JsonPrimitive(message.string("script")).toString()
                val queryScript = "Promise.resolve().then(()=>eval($script))" +
                    ".then(v=>window.cefQuery({request:JSON.stringify({id:$id,value:JSON.stringify(v)})}))" +
                    ".catch(e=>window.cefQuery({request:JSON.stringify({id:$id,value:String(e)})}));"
                browser.executeJavaScript(queryScript, browser.url, 0)
            }
            "source" -> browser.getSource { source -> events.send("result", message.string("id"), source) }
            "cookies" -> exportCookies(message.string("id"))
            "setCookie" -> {
                val cookie = message["cookie"]!!.jsonObject
                val expires = cookie.string("expiresAt").toLongOrNull() ?: 0
                val manager = CefCookieManager.getGlobalManager()
                manager.setCookie(
                    message.string("url"),
                    CefCookie(
                        cookie.string("name"), cookie.string("value"), cookie.string("domain"), cookie.string("path"),
                        cookie.string(
                            "secure",
                        ) == "true",
                        cookie.string("httpOnly") == "true", Date(), Date(), expires > 0, Date(expires),
                    ),
                )
                manager.flushStore { events.send("result", message.string("id"), "true") }
            }
            "close" -> close()
        }
    }
    private fun exportCookies(id: String) {
        val manager = CefCookieManager.getGlobalManager()
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val seen = java.util.concurrent.atomic.AtomicInteger(0)
        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            manager.flushStore {
                events.send("cookiesDone", id)
                if (id == "__closing__") {
                    events.send("closed")
                    SwingUtilities.invokeLater { close() }
                }
            }
        }
        // The visitor is not called when the jar is empty, so an empty login must still finish.
        val accepted = manager.visitAllCookies { cookie, count, total, _ ->
            seen.incrementAndGet()
            events.send(
                "cookie",
                id,
                buildJsonObject {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("httpOnly", cookie.httponly)
                    put("expiresAt", if (cookie.hasExpires) cookie.expires?.time ?: 0 else 0)
                }.toString(),
            )
            if (total <= 0 || count + 1 >= total) finish()
            true
        }
        if (!accepted) {
            finish()
        } else {
            javax.swing.Timer(1500) { _ -> if (seen.get() == 0) finish() }.apply {
                isRepeats = false
                start()
            }
        }
    }
    private fun allowedScheme(url: String) =
        url == "chrome://credits/" || URI(url).scheme in setOf("http", "https", "about", "data", "blob")
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        browser.stopLoad()
        browser.close(true)
        client.dispose()
        app.dispose()
        SwingUtilities.invokeLater { window.dispose() }
    }
}
