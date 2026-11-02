package mihon.desktop.sync.gdrive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Properties

class GoogleDriveConfigTest {
    @Test
    fun `missing secret is reported as not configured with default client id`() {
        val c = GoogleDriveConfig.resolve(resource = null, systemProperty = { null }, env = { null })
        c.clientId shouldBe GoogleDriveConfig.DEFAULT_CLIENT_ID
        c.isConfigured shouldBe false
    }

    @Test
    fun `build resource is used and runtime overrides win`() {
        val res = Properties().apply { setProperty("clientSecret", "from-build") }
        GoogleDriveConfig.resolve(res, { null }, { null }).clientSecret shouldBe "from-build"
        GoogleDriveConfig.resolve(res, { null }, { if (it == "GDRIVE_CLIENT_SECRET") "from-env" else null })
            .clientSecret shouldBe "from-env"
        GoogleDriveConfig.resolve(res, { if (it == "mihon.gdrive.clientSecret") "from-prop" else null }, { "x" })
            .clientSecret shouldBe "from-prop"
    }

    @Test
    fun `blank secret counts as missing`() {
        val res = Properties().apply { setProperty("clientSecret", "  ") }
        GoogleDriveConfig.resolve(res, { null }, { null }).isConfigured shouldBe false
    }
}
