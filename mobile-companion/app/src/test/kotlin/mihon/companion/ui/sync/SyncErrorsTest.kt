package mihon.companion.ui.sync

import io.kotest.matchers.shouldBe
import mihon.companion.R
import org.junit.jupiter.api.Test

class SyncErrorsTest {
    @Test
    fun `okhttp timeouts become network advice`() {
        SyncErrors.describe("timeout").text shouldBe R.string.sync_error_timeout
        SyncErrors.describe("Read timed out").text shouldBe R.string.sync_error_timeout
    }

    @Test
    fun `unreachable host keeps the raw detail`() {
        val message = SyncErrors.describe("Unable to resolve host \"www.googleapis.com\"")
        message.text shouldBe R.string.sync_error_network
        message.args shouldBe listOf("Unable to resolve host \"www.googleapis.com\"")
    }

    @Test
    fun `expired sign-in asks to sign in again`() {
        SyncErrors.describe("Drive rejected the access token (401)").text shouldBe R.string.sync_error_sign_in
    }

    @Test
    fun `anything else is shown as is`() {
        SyncErrors.describe("Drive HTTP 500").args shouldBe listOf("Drive HTTP 500")
    }
}
