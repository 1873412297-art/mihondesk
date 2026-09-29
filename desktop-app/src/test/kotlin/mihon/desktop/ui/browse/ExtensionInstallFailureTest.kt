package mihon.desktop.ui.browse

import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionDownloadException
import mihon.desktop.extension.ExtensionSignatureException
import mihon.desktop.extension.ExtensionSignatureMismatchException
import mihon.desktop.extension.ExtensionStoreUnavailableException
import mihon.desktop.extension.ExtensionTrustRequiredException
import mihon.extension.validator.ExtensionValidationException
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExtensionInstallFailureTest {

    @Test
    fun `signature and trust rejections are reported as such`() {
        classifyInstallFailure(ExtensionTrustRequiredException("ext.one", listOf("aa11"), "signer is unknown")) shouldBe
            ExtensionInstallFailure.SignatureRejected
        classifyInstallFailure(ExtensionSignatureMismatchException("ext.one", setOf("aa11"), listOf("bb22"))) shouldBe
            ExtensionInstallFailure.SignatureRejected
        classifyInstallFailure(ExtensionSignatureException("v2/v3-only packages are not supported")) shouldBe
            ExtensionInstallFailure.SignatureRejected
    }

    @Test
    fun `download step failures are reported as such`() {
        classifyInstallFailure(ExtensionDownloadException("Failed to download extension package: HTTP 404")) shouldBe
            ExtensionInstallFailure.DownloadFailed
        classifyInstallFailure(IOException("unexpected end of stream")) shouldBe ExtensionInstallFailure.DownloadFailed
        classifyInstallFailure(UnknownHostException("repo.example.com")) shouldBe ExtensionInstallFailure.DownloadFailed
        classifyInstallFailure(SocketTimeoutException("timeout")) shouldBe ExtensionInstallFailure.DownloadFailed
    }

    @Test
    fun `a nested download failure is still a download failure`() {
        val wrapped = RuntimeException("install failed", IOException("connection reset"))

        classifyInstallFailure(wrapped) shouldBe ExtensionInstallFailure.DownloadFailed
    }

    @Test
    fun `store losses are reported as such`() {
        classifyInstallFailure(ExtensionStoreUnavailableException("ext.one", "1.0")) shouldBe
            ExtensionInstallFailure.StoreUnavailable
    }

    @Test
    fun `anything else stays generic`() {
        classifyInstallFailure(ExtensionValidationException("Package manifest is missing")) shouldBe
            ExtensionInstallFailure.Unknown
        classifyInstallFailure(IllegalStateException("boom")) shouldBe ExtensionInstallFailure.Unknown
    }
}
