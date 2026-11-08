package mihon.webview

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Chromium switches from the main process's configuration line.
 *
 * Only network-route switches are accepted. The helper is an untrusted-content process, so a
 * compromised or buggy configuration must not be able to turn off the sandbox, enable remote
 * debugging or load an extension through this channel.
 */
internal object BrowserSwitches {
    private val exact = setOf("--no-proxy-server", "--enable-features=DnsOverHttps")
    private val valued = listOf(
        "--proxy-server=",
        "--host-resolver-rules=",
        "--force-fieldtrials=DoHTrial/",
        "--force-fieldtrial-params=DoHTrial.",
    )

    fun accepted(config: JsonObject): List<String> {
        val requested = config["browserArgs"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        return requested.filter { argument ->
            argument.none { it == '\r' || it == '\n' || it == '\u0000' } &&
                (argument in exact || valued.any(argument::startsWith))
        }
    }
}
