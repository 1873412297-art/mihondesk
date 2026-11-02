package eu.kanade.tachiyomi.data.sync

import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveSnapshotCorruptException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriveSyncErrorTest {

    @Test
    fun `exceptions map to friendly categories`() {
        assertEquals(DriveSyncError.AUTH_REQUIRED, DriveSyncError.classify(DriveAuthException("sign-in required")))
        assertEquals(DriveSyncError.CORRUPT, DriveSyncError.classify(DriveSnapshotCorruptException("bad")))
        assertEquals(DriveSyncError.NETWORK, DriveSyncError.classify(UnknownHostException("www.googleapis.com")))
        assertEquals(DriveSyncError.NETWORK, DriveSyncError.classify(SocketTimeoutException()))
        assertEquals(DriveSyncError.NETWORK, DriveSyncError.classify(IOException("connection reset")))
        assertEquals(DriveSyncError.UNKNOWN, DriveSyncError.classify(IllegalStateException("boom")))
    }

    @Test
    fun `wrapped causes are unwrapped`() {
        val wrapped = RuntimeException("outer", DriveAuthException("Google Drive sign-in required"))
        assertEquals(DriveSyncError.AUTH_REQUIRED, DriveSyncError.classify(wrapped))
    }

    @Test
    fun `engine error messages map to categories`() {
        assertEquals(DriveSyncError.AUTH_REQUIRED, DriveSyncError.classifyMessage("Google Drive sign-in required"))
        assertEquals(DriveSyncError.AUTH_REQUIRED, DriveSyncError.classifyMessage("HTTP 401 Unauthorized"))
        assertEquals(DriveSyncError.CORRUPT, DriveSyncError.classifyMessage("Drive snapshot is corrupt"))
        assertEquals(DriveSyncError.NETWORK, DriveSyncError.classifyMessage("Unable to resolve host \"x\""))
        assertEquals(DriveSyncError.NETWORK, DriveSyncError.classifyMessage("timeout"))
        assertEquals(DriveSyncError.UNKNOWN, DriveSyncError.classifyMessage(null))
    }

    @Test
    fun `stored values round trip and junk is ignored`() {
        DriveSyncError.entries.forEach { assertEquals(it, DriveSyncError.fromStored(it.name)) }
        assertNull(DriveSyncError.fromStored(""))
        assertNull(DriveSyncError.fromStored("HTTP sync server or token not configured"))
    }
}
