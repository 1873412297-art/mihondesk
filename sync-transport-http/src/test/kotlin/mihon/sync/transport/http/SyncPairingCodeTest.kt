package mihon.sync.transport.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SyncPairingCodeTest {

    @Test
    fun `parse valid pairing URI`() {
        val code = SyncPairingCode.parseOrNull("mihonsync://192.168.1.105:45831#abc123token")
        assertNotNull(code)
        assertEquals("192.168.1.105", code!!.host)
        assertEquals(45831, code.port)
        assertEquals("abc123token", code.token)
        assertEquals("mihonsync://192.168.1.105:45831#abc123token", code.toUriString())
        assertEquals("http://192.168.1.105:45831", code.toHttpBaseUrl())
    }

    @Test
    fun `parse http scheme and default port`() {
        val code1 = SyncPairingCode.parseOrNull("http://desktop-pc:8080#tokenxyz")
        assertNotNull(code1)
        assertEquals("desktop-pc", code1!!.host)
        assertEquals(8080, code1.port)
        assertEquals("tokenxyz", code1.token)

        val code2 = SyncPairingCode.parseOrNull("192.168.0.2#tokenWithoutPort")
        assertNotNull(code2)
        assertEquals("192.168.0.2", code2!!.host)
        assertEquals(45831, code2.port)
        assertEquals("tokenWithoutPort", code2.token)
    }

    @Test
    fun `parse rejects invalid input`() {
        assertNull(SyncPairingCode.parseOrNull(""))
        assertNull(SyncPairingCode.parseOrNull("   "))
        assertNull(SyncPairingCode.parseOrNull("noTokenHere"))
        assertNull(SyncPairingCode.parseOrNull("#onlyToken"))
        assertNull(SyncPairingCode.parseOrNull("192.168.1.1#"))
    }
}
