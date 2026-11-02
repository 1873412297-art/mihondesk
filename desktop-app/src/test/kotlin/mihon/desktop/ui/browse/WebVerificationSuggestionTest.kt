package mihon.desktop.ui.browse

import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.UiText
import mihon.extension.ipc.NetworkFailure
import mihon.extension.ipc.NetworkFailureKind
import org.junit.jupiter.api.Test

class WebVerificationSuggestionTest {
    @Test
    fun `403 and cloudflare challenges suggest web view verification`() {
        suggestsWebVerification("HttpException: HTTP error 403 at keiyoushi.a.t.a", null) shouldBe true
        suggestsWebVerification("x", NetworkFailure(NetworkFailureKind.WEB_VERIFICATION, 403, "h")) shouldBe true
        suggestsWebVerification("x", NetworkFailure(NetworkFailureKind.AUTHENTICATION_REQUIRED, 403, "h")) shouldBe true
        suggestsWebVerification("Cloudflare challenge", null) shouldBe true
        suggestsWebVerification("HTTP error 404", null) shouldBe false
        suggestsWebVerification("timeout", NetworkFailure(NetworkFailureKind.HTTP_ERROR, 500, "h")) shouldBe false
        suggestsWebVerification(null, null) shouldBe false
    }

    @Test
    fun `verification hint and action are translated in all locales`() {
        listOf(UiText.SourceVerifyInWebView, UiText.HintWebVerificationAction).forEach {
            (it.english.isNotBlank() && it.simplified.isNotBlank() && it.traditional.isNotBlank()) shouldBe true
            (it.simplified != it.english && it.traditional != it.simplified) shouldBe true
        }
    }
}
