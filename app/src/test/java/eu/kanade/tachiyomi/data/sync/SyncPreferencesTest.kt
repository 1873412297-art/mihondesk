package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class SyncPreferencesTest {

    @Test
    fun `defaults are drive signed out and manual only`() {
        val prefs = SyncPreferences(InMemoryPreferenceStore())

        assertFalse(prefs.googleDriveEnabled.get())
        assertEquals(0, prefs.syncIntervalMinutes.get())
        assertFalse(prefs.syncOnlyOnWifi.get())
        assertEquals(0L, prefs.lastSyncTimestamp.get())
        assertEquals("", prefs.lastSyncError.get())
    }

    @Test
    fun `sign out clears drive flag and last result`() {
        val prefs = SyncPreferences(InMemoryPreferenceStore())
        prefs.googleDriveEnabled.set(true)
        prefs.lastSyncTimestamp.set(5L)
        prefs.lastSyncSummary.set("s")
        prefs.lastSyncError.set(DriveSyncError.NETWORK.name)

        prefs.clearDriveSignIn()

        assertFalse(prefs.googleDriveEnabled.get())
        assertEquals(0L, prefs.lastSyncTimestamp.get())
        assertEquals("", prefs.lastSyncSummary.get())
        assertEquals("", prefs.lastSyncError.get())
    }
}
