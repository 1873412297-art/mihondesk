package mihon.desktop.i18n

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.download.DownloadFailureReason
import org.junit.jupiter.api.Test

class FailureTextTlsHintTest {

    @Test
    fun `tls failures are not presented as a network or proxy problem`() {
        DownloadFailureReason.TLS.hint shouldNotBe UiText.HintNetwork
        DownloadFailureReason.TLS.hint shouldBe UiText.HintTls
        UiText.HintTls.english shouldNotContain "proxy"
        UiText.HintTls.simplified shouldNotContain "代理"
        UiText.HintTls.traditional shouldNotContain "代理"
    }
}
