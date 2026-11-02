package mihon.desktop.sync.gdrive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI

class LoopbackOAuthReceiverTest {
    private fun hit(url: String): Int =
        (URI(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection).run {
            responseCode.also { disconnect() }
        }

    @Test
    fun `captures code when state matches`(): Unit = runBlocking {
        LoopbackOAuthReceiver("s1").use { r ->
            r.redirectUri shouldStartWith "http://127.0.0.1:"
            hit("${r.redirectUri}/?state=s1&code=4%2Fabc&scope=x") shouldBe 200
            r.awaitCode(5_000) shouldBe "4/abc"
        }
    }

    @Test
    fun `state mismatch or user denial fails`(): Unit = runBlocking {
        LoopbackOAuthReceiver("s1").use { r ->
            hit("${r.redirectUri}/?state=evil&code=abc") shouldBe 400
            shouldThrow<OAuthCancelledException> { r.awaitCode(5_000) }
        }
        LoopbackOAuthReceiver("s2").use { r ->
            hit("${r.redirectUri}/?error=access_denied&state=s2") shouldBe 400
            shouldThrow<OAuthCancelledException> { r.awaitCode(5_000) }
        }
    }
}
