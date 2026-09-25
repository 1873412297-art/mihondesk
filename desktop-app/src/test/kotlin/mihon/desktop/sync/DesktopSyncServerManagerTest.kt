package mihon.desktop.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopSyncServerManagerTest {

    @Test
    fun `effectiveName returns custom name when provided`() {
        val name = DesktopSyncServerManager.effectiveName("My Custom PC")
        assertEquals("My Custom PC", name)
    }

    @Test
    fun `effectiveName falls back to hostname when empty or blank`() {
        val nameFromEmpty = DesktopSyncServerManager.effectiveName("")
        assertFalse(nameFromEmpty.isBlank())

        val nameFromSpaces = DesktopSyncServerManager.effectiveName("   ")
        assertFalse(nameFromSpaces.isBlank())
        assertEquals(nameFromEmpty, nameFromSpaces)
    }

    @Test
    fun `effectiveName truncates names longer than max length`() {
        val longName = "A".repeat(100)
        val name = DesktopSyncServerManager.effectiveName(longName)
        assertEquals(DesktopSyncServerManager.MAX_NAME_LENGTH, name.length)
        assertEquals("A".repeat(DesktopSyncServerManager.MAX_NAME_LENGTH), name)
    }

    @Test
    fun `getLocalIpAddresses returns non-empty list of IP addresses`() {
        val ips = DesktopSyncServerManager.getLocalIpAddresses()
        assertTrue(ips.isNotEmpty())
        assertTrue(ips.none { it.isBlank() })
    }

    @Test
    fun `effectiveSelectedIp returns remembered ip when it exists in localIps`() {
        val localIps = listOf("192.168.1.5", "10.0.0.1", "127.0.0.1")
        val effective = DesktopSyncServerManager.effectiveSelectedIp("10.0.0.1", localIps)
        assertEquals("10.0.0.1", effective)
    }

    @Test
    fun `effectiveSelectedIp falls back to first ip when remembered ip not in localIps`() {
        val localIps = listOf("192.168.1.5", "10.0.0.1")
        val effective = DesktopSyncServerManager.effectiveSelectedIp("172.16.0.9", localIps)
        assertEquals("192.168.1.5", effective)
    }

    @Test
    fun `effectiveSelectedIp falls back to first ip when remembered ip is empty or blank`() {
        val localIps = listOf("192.168.1.5", "10.0.0.1")
        assertEquals("192.168.1.5", DesktopSyncServerManager.effectiveSelectedIp("", localIps))
        assertEquals("192.168.1.5", DesktopSyncServerManager.effectiveSelectedIp("   ", localIps))
    }

    @Test
    fun `effectiveSelectedIp returns localhost when localIps is empty`() {
        assertEquals("127.0.0.1", DesktopSyncServerManager.effectiveSelectedIp("192.168.1.5", emptyList()))
        assertEquals("127.0.0.1", DesktopSyncServerManager.effectiveSelectedIp("", emptyList()))
    }
}
