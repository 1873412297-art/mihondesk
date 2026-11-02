package mihon.desktop.extension

import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/** Every app OkHttp client must use the TLS reset fallback (library update, store, installer, tracker...). */
class HttpClientsTlsGuardTest {
    @Test
    fun `all desktop OkHttp clients install the TLS fallback`() {
        val roots = listOf(File("src/main/kotlin"), File("../extension-host/src/main/kotlin"))
        val offenders = roots.flatMap { root -> root.walk().filter { it.extension == "kt" }.toList() }
            .filter { file ->
                val text = file.readText()
                ("OkHttpClient.Builder()" in text || "OkHttpClient()" in text) && "withTlsFallback" !in text &&
                    "TlsFallback" !in text
            }.map { it.path }
        offenders.shouldBeEmpty()
    }
}
