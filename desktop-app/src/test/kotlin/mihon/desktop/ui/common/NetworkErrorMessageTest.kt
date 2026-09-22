package mihon.desktop.ui.common

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.i18n.TraditionalChineseStrings
import org.junit.jupiter.api.Test

class NetworkErrorMessageTest {

    @Test
    fun `detects Cloudflare 1005 ASN ban from raw error message`() {
        val rawMessage = "HTTP error 403: error code: 1005"
        val mappedZh = formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings)
        mappedZh shouldBe SimplifiedChineseStrings.networkErrorCloudflare1005
        mappedZh shouldContain "1005"
        mappedZh shouldContain "IP/ASN"

        val mappedEn = formatNetworkErrorMessage(rawMessage, EnglishStrings)
        mappedEn shouldBe EnglishStrings.networkErrorCloudflare1005

        val mappedTw = formatNetworkErrorMessage(rawMessage, TraditionalChineseStrings)
        mappedTw shouldBe TraditionalChineseStrings.networkErrorCloudflare1005
    }

    @Test
    fun `detects Cloudflare 1020 firewall rule denial from error code`() {
        val rawMessage = "Request failed with error code: 1020"
        val mappedZh = formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings)
        mappedZh shouldBe SimplifiedChineseStrings.networkErrorCloudflare1020
        mappedZh shouldContain "1020"

        val mappedEn = formatNetworkErrorMessage(rawMessage, EnglishStrings)
        mappedEn shouldBe EnglishStrings.networkErrorCloudflare1020
    }

    @Test
    fun `detects Cloudflare 1015 rate limit from error code`() {
        val rawMessage = "HTTP error 429 (error code: 1015)"
        val mappedZh = formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings)
        mappedZh shouldBe SimplifiedChineseStrings.networkErrorCloudflare1015
        mappedZh shouldContain "1015"

        val mappedEn = formatNetworkErrorMessage(rawMessage, EnglishStrings)
        mappedEn shouldBe EnglishStrings.networkErrorCloudflare1015
    }

    @Test
    fun `detects Cloudflare 1016 origin DNS failure from error code`() {
        val rawMessage = "error code: 1016"
        val mappedZh = formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings)
        mappedZh shouldBe SimplifiedChineseStrings.networkErrorCloudflare1016
        mappedZh shouldContain "1016"

        val mappedEn = formatNetworkErrorMessage(rawMessage, EnglishStrings)
        mappedEn shouldBe EnglishStrings.networkErrorCloudflare1016
    }

    @Test
    fun `passes through plain HTTP error 500 without Cloudflare hints unchanged`() {
        val rawMessage = "HTTP error 500"
        formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings) shouldBe "HTTP error 500"
        formatNetworkErrorMessage(rawMessage, EnglishStrings) shouldBe "HTTP error 500"
    }

    @Test
    fun `provides generic fallback for unknown 10xx Cloudflare error codes`() {
        val rawMessage = "HTTP error 403: error code: 1099"
        val mappedZh = formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings)
        mappedZh shouldBe "站点拒绝访问（Cloudflare 错误码 1099）"

        val mappedEn = formatNetworkErrorMessage(rawMessage, EnglishStrings)
        mappedEn shouldBe "Access denied by site (Cloudflare error code 1099)"

        val mappedTw = formatNetworkErrorMessage(rawMessage, TraditionalChineseStrings)
        mappedTw shouldBe "站點拒絕存取（Cloudflare 錯誤碼 1099）"
    }

    @Test
    fun `detects Cloudflare 1020 from well-known status signature`() {
        val rawMessage = "Sorry, you have been blocked"
        formatNetworkErrorMessage(rawMessage, SimplifiedChineseStrings) shouldBe
            SimplifiedChineseStrings.networkErrorCloudflare1020
    }

    @Test
    fun `handles empty and null error messages gracefully`() {
        formatNetworkErrorMessage("", SimplifiedChineseStrings) shouldBe ""
        formatNetworkErrorMessage(null, SimplifiedChineseStrings) shouldBe ""
    }
}
