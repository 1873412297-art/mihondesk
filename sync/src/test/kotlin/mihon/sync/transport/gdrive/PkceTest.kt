package mihon.sync.transport.gdrive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.URLDecoder

class PkceTest {
    @Test
    fun `challenge matches RFC 7636 vector`() {
        Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") shouldBe
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    @Test
    fun `verifier is url-safe and within RFC length`() {
        val v = Pkce.newVerifier()
        (v.length in 43..128) shouldBe true
        Regex("^[A-Za-z0-9_-]+$").matches(v) shouldBe true
    }

    @Test
    fun `auth url requests only appdata scope with offline access and S256`() {
        val url = GoogleOAuth.authorizationUrl(
            clientId = "cid",
            redirectUri = "http://127.0.0.1:5555",
            codeChallenge = "chal",
            state = "st",
        )
        val decoded = URLDecoder.decode(url, Charsets.UTF_8)
        decoded shouldContain "scope=https://www.googleapis.com/auth/drive.appdata"
        decoded shouldNotContain "auth/drive "
        decoded shouldContain "code_challenge=chal"
        decoded shouldContain "code_challenge_method=S256"
        decoded shouldContain "access_type=offline"
        decoded shouldContain "redirect_uri=http://127.0.0.1:5555"
        decoded shouldContain "state=st"
    }
}
