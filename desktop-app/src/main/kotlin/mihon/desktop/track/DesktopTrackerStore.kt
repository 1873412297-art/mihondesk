package mihon.desktop.track

import mihon.desktop.logging.DesktopLogger
import mihon.desktop.platform.CredentialStore
import mihon.desktop.platform.WindowsCredentialStore
import mihon.desktop.preferences.DesktopPreferenceStore
import java.util.UUID

class DesktopTrackerStore(
    private val preferences: DesktopPreferenceStore,
    private val credentials: CredentialStore = WindowsCredentialStore(),
    namespace: String? = null,
) {
    private val namespace = namespace ?: preferences.property("tracker.credential_profile")
        ?: UUID.randomUUID().toString().also { value ->
            preferences.update { setProperty("tracker.credential_profile", value) }
        }
    private val sessionTokens = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private fun target(id: Long) = "$namespace/tracker/$id"
    private fun persistSecret(id: Long, value: String): Boolean = runCatching {
        val saved = credentials.write(target(id), value) && credentials.read(target(id)) == value
        if (!saved) DesktopLogger.warn("Tracker", "Credential store rejected tracker $id")
        saved
    }.getOrElse { error ->
        DesktopLogger.warn("Tracker", "Unable to store credential for tracker $id", error)
        false
    }

    fun isLoggedIn(trackerId: Long): Boolean {
        return sessionTokens.containsKey(trackerId) ||
            (preferences.property(key(trackerId, "logged_in"))?.toBooleanStrictOrNull() ?: false)
    }

    fun getUsername(trackerId: Long): String? {
        return preferences.property(key(trackerId, "username"))
    }

    fun getAuthToken(trackerId: Long): String? {
        sessionTokens[trackerId]?.let { return it }
        val legacy = preferences.property(key(trackerId, "token"))
        if (legacy != null) {
            if (persistSecret(trackerId, legacy)) preferences.update { remove(key(trackerId, "token")) }
            return legacy
        }
        return runCatching { credentials.read(target(trackerId)) }.getOrNull()
    }

    fun getServerUrl(trackerId: Long): String? {
        return preferences.property(key(trackerId, "server_url"))
    }

    fun getLoginInfo(trackerId: Long): TrackerLoginInfo? {
        if (!isLoggedIn(trackerId)) return null
        val token = getAuthToken(trackerId) ?: return null
        return TrackerLoginInfo(
            trackerId = trackerId,
            username = getUsername(trackerId).orEmpty(),
            token = token,
            serverUrl = getServerUrl(trackerId).orEmpty(),
        )
    }

    fun saveLogin(trackerId: Long, username: String, token: String, serverUrl: String = ""): Boolean {
        // A rejected write must not replace a restorable account with credentials that
        // only exist in this process.
        if (!persistSecret(trackerId, token)) return false
        sessionTokens[trackerId] = token
        preferences.update {
            setProperty(key(trackerId, "logged_in"), "true")
            setProperty(key(trackerId, "username"), username)
            remove(key(trackerId, "token"))
            if (serverUrl.isNotBlank()) {
                setProperty(key(trackerId, "server_url"), serverUrl)
            } else {
                remove(key(trackerId, "server_url"))
            }
        }
        return true
    }

    fun clearLogin(trackerId: Long) {
        sessionTokens.remove(trackerId)
        // The local login is cleared either way; a token left in the OS store must at least be visible.
        runCatching { credentials.delete(target(trackerId)) }
            .onFailure { DesktopLogger.warn("Tracker", "Unable to delete stored credential on logout", it) }
        preferences.update {
            remove(key(trackerId, "logged_in"))
            remove(key(trackerId, "username"))
            remove(key(trackerId, "token"))
            remove(key(trackerId, "server_url"))
        }
    }

    private fun key(trackerId: Long, property: String): String = "tracker.$trackerId.$property"
}
