package mihon.desktop.extension

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * Network-dependent TLS matrix (not in the default suite). Run:
 *   gradlew :desktop-app:tlsDiagnostics [-Dmihon.diag.proxy=127.0.0.1:7897] [-Dmihon.diag.hosts=a.com,b.com]
 * Columns: OkHttp MODERN_TLS (ALPN h2+http/1.1), MODERN_TLS http/1.1 only (no h2 in ALPN),
 * compatible spec (all JDK suites/versions), and plain JDK HttpsURLConnection (no ALPN).
 * Through an HTTP proxy every client opens a CONNECT tunnel and sends the real host as SNI.
 */
@Tag("network")
class TlsCompatibilityDiagnostic {
    private val hosts = System.getProperty("mihon.diag.hosts")?.split(',')?.filter { it.isNotBlank() } ?: listOf(
        "www.bilimanga.net", "akaicomic.org", "ezmanga.org", "vapi.ezmanga.org", "api.vomicmh.com",
        "m.hipmh.com", "rouman5.com", "kemono.cr", "j-novel.club", "everia.club",
    )
    private val proxy = (System.getProperty("mihon.diag.proxy") ?: "127.0.0.1:7897").split(':').let {
        Proxy(Proxy.Type.HTTP, InetSocketAddress(it[0], it[1].toInt()))
    }

    private fun okhttp(route: Proxy, specs: List<ConnectionSpec>, protocols: List<Protocol>) = OkHttpClient.Builder()
        .proxy(route).connectionSpecs(specs).protocols(protocols).followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    private fun short(e: Throwable): String {
        val m = (e.message ?: e.javaClass.simpleName).replace('|', '/').take(48)
        return "${e.javaClass.simpleName}: $m"
    }

    private fun viaOkHttp(client: OkHttpClient, host: String): String = runCatching {
        client.newCall(Request.Builder().url("https://$host/").build()).execute().use { r ->
            "${r.code} ${r.protocol} ${r.handshake?.tlsVersion?.javaName ?: ""}".trim()
        }
    }.getOrElse(::short)

    private fun viaJdk(route: Proxy, host: String): String = runCatching {
        val c = URI("https://$host/").toURL().openConnection(route) as HttpsURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            "${c.responseCode} http/1.1"
        } finally {
            (c as HttpURLConnection).disconnect()
        }
    }.getOrElse(::short)

    @Test
    fun `print tls compatibility matrix`() {
        val h2 = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        val h1 = listOf(Protocol.HTTP_1_1)
        val rows = mutableListOf(
            "| host | route | OkHttp MODERN h2 | OkHttp MODERN h1 | OkHttp compatible h2 | plain JDK |",
            "|---|---|---|---|---|---|",
        )
        for (host in hosts) {
            for ((name, route) in listOf("direct" to Proxy.NO_PROXY, "proxy" to proxy)) {
                val cells = listOf(
                    viaOkHttp(okhttp(route, DesktopTls.defaultSpecs, h2), host),
                    viaOkHttp(okhttp(route, DesktopTls.defaultSpecs, h1), host),
                    viaOkHttp(okhttp(route, DesktopTls.compatibleSpecs, h2), host),
                    viaJdk(route, host),
                )
                rows += "| $host | $name | ${cells.joinToString(" | ")} |"
            }
        }
        val report = rows.joinToString("\n")
        println(report)
        File("build/reports/tls-matrix.md").apply { parentFile.mkdirs() }.writeText(report + "\n")
    }
}
