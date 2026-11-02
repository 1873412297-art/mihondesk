package mihon.sync.transport.gdrive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GoogleOAuthTokenTest {
    @Test
    fun `parses token response with refresh token and absolute expiry`() {
        val t = GoogleOAuth.parseTokenResponse(
            """{"access_token":"A","expires_in":3600,"refresh_token":"R","scope":"x","token_type":"Bearer"}""",
            now = 1_000L,
        )
        t.accessToken shouldBe "A"
        t.refreshToken shouldBe "R"
        t.expiresAtMillis shouldBe 1_000L + 3_600_000L
    }

    @Test
    fun `refresh response keeps previous refresh token`() {
        val t = GoogleOAuth.parseTokenResponse(
            """{"access_token":"B","expires_in":10}""",
            now = 0,
            previousRefreshToken = "R",
        )
        t.refreshToken shouldBe "R"
    }

    @Test
    fun `invalid_grant becomes auth exception`() {
        shouldThrow<DriveAuthException> {
            GoogleOAuth.parseTokenResponse("""{"error":"invalid_grant","error_description":"revoked"}""", now = 0)
        }
    }
}
