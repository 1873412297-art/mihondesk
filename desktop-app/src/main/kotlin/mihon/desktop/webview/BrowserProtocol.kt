package mihon.desktop.webview

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import java.nio.file.Path

internal fun browserConfiguration(
    url: String,
    cache: Path,
    brokerPort: Int,
    headers: Map<String, String>,
    cookies: List<Cookie>,
    browserArgs: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("url", url)
    put("cache", cache.toAbsolutePath().toString())
    put("broker", "http://127.0.0.1:$brokerPort/request")
    put("headers", buildJsonObject { headers.forEach { (key, value) -> put(key, value) } })
    put("cookies", buildJsonArray { cookies.forEach { add(cookieJson(it)) } })
    put("browserArgs", buildJsonArray { browserArgs.forEach { add(it) } })
}

internal fun cookieMessage(url: String, cookie: Cookie): JsonObject = buildJsonObject {
    put("url", url)
    put("cookie", cookieJson(cookie))
}

private fun cookieJson(cookie: Cookie): JsonObject = buildJsonObject {
    put("name", cookie.name)
    put("value", cookie.value)
    put("domain", if (cookie.hostOnly) cookie.domain else ".${cookie.domain}")
    put("path", cookie.path)
    put("secure", cookie.secure)
    put("httpOnly", cookie.httpOnly)
    put("expiresAt", if (cookie.persistent) cookie.expiresAt else 0)
    put("url", "${if (cookie.secure) "https" else "http"}://${cookie.domain.removePrefix(".")}/")
}
