package eu.kanade.tachiyomi.data.sync

import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveSnapshotCorruptException
import java.io.IOException

/** User-facing categories for Google Drive sync failures; stored by [name] in preferences. */
enum class DriveSyncError {
    AUTH_REQUIRED,
    CANCELLED,
    NETWORK,
    CORRUPT,
    UNKNOWN,
    ;

    companion object {
        fun classify(error: Throwable): DriveSyncError {
            var current: Throwable? = error
            while (current != null) {
                when (current) {
                    is DriveAuthException -> return AUTH_REQUIRED
                    is DriveSnapshotCorruptException -> return CORRUPT
                }
                current = current.cause.takeIf { it !== current }
            }
            return if (generateSequence(error) { it.cause }.any { it is IOException }) NETWORK else UNKNOWN
        }

        fun classifyMessage(message: String?): DriveSyncError {
            val m = message?.lowercase() ?: return UNKNOWN
            return when {
                "sign-in" in m || "sign in" in m || "401" in m || "unauthorized" in m ||
                    "access token" in m -> AUTH_REQUIRED
                "corrupt" in m -> CORRUPT
                "resolve host" in m || "timeout" in m || "timed out" in m || "failed to connect" in m ||
                    "connection" in m || "network" in m -> NETWORK
                else -> UNKNOWN
            }
        }

        fun fromStored(value: String): DriveSyncError? = entries.firstOrNull { it.name == value }
    }
}
