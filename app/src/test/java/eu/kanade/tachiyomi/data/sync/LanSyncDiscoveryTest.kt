package eu.kanade.tachiyomi.data.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class LanSyncDiscoveryTest {

    // ---------------------------------------------------------------------------
    // buildDiscoveredSyncService – pure-function tests, no Android context needed
    // ---------------------------------------------------------------------------

    @Test
    fun `returns service when all TXT keys present`() {
        val attrs = mapOf("v" to "1", "name" to "MyPC", "token" to "tok123")
        val result = buildDiscoveredSyncService("raw-name", "192.168.1.1", 45831, attrs)
        result shouldNotBe null
        result!!.serviceName shouldBe "raw-name"
        result.name shouldBe "MyPC"
        result.host shouldBe "192.168.1.1"
        result.port shouldBe 45831
        result.token shouldBe "tok123"
        result.version shouldBe 1
    }

    @Test
    fun `uses service name as display name when TXT name is absent`() {
        val attrs = mapOf("v" to "1")
        val result = buildDiscoveredSyncService("fallback-name", "10.0.0.1", 9000, attrs)
        result shouldNotBe null
        result!!.name shouldBe "fallback-name"
    }

    @Test
    fun `token is null when TXT token key is absent`() {
        val attrs = mapOf("v" to "1", "name" to "PC")
        val result = buildDiscoveredSyncService("PC", "10.0.0.1", 45831, attrs)
        result shouldNotBe null
        result!!.token shouldBe null
    }

    @Test
    fun `token is null when TXT token key is blank`() {
        val attrs = mapOf("v" to "1", "name" to "PC", "token" to "")
        val result = buildDiscoveredSyncService("PC", "10.0.0.1", 45831, attrs)
        result shouldNotBe null
        result!!.token shouldBe null
    }

    @Test
    fun `returns null when version key exists and is not 1 (forward compat)`() {
        val attrs = mapOf("v" to "2", "name" to "NextVersion")
        val result = buildDiscoveredSyncService("NextVersion", "10.0.0.1", 45831, attrs)
        result shouldBe null
    }

    @Test
    fun `defaults version to 1 when v key is absent`() {
        val attrs = mapOf("name" to "LegacyServer")
        val result = buildDiscoveredSyncService("LegacyServer", "10.0.0.1", 45831, attrs)
        result shouldNotBe null
        result!!.version shouldBe 1
    }

    @Test
    fun `ignores service with non-numeric version`() {
        val attrs = mapOf("v" to "notanumber", "name" to "WeirdServer")
        val result = buildDiscoveredSyncService("WeirdServer", "10.0.0.1", 45831, attrs)
        result shouldBe null
    }

    @Test
    fun `handles empty attrs map (low-API degradation)`() {
        val result = buildDiscoveredSyncService("OldServer", "172.16.0.1", 45831, emptyMap())
        result shouldNotBe null
        result!!.token shouldBe null
        result.version shouldBe 1
    }

    @Test
    fun `name from TXT is trimmed and not blank`() {
        val attrs = mapOf("v" to "1", "name" to "  ", "token" to "t")
        // blank name → falls back to service name
        val result = buildDiscoveredSyncService("fallback", "1.2.3.4", 100, attrs)
        result shouldNotBe null
        result!!.name shouldBe "fallback"
    }
}
