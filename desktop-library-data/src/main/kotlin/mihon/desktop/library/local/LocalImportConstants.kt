package mihon.desktop.library.local

import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

internal const val STAGING_DIRECTORY = ".staging"
internal const val CLAIMS_DIRECTORY = ".claims"
internal const val MANGA_DIRECTORY = "manga"
internal const val CLAIM_SUFFIX = ".claim"
internal const val CLAIM_FORMAT = "mihon-desktop-local-import-claim-v1"
internal const val LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX = ".mihon-local-import-owner."
internal const val OWNERSHIP_MARKER_FORMAT = "mihon-desktop-local-import-v1"
internal const val MAX_OWNERSHIP_MARKER_BYTES = 256L
internal const val MAX_CLAIM_BYTES = 512L
internal const val OWNERSHIP_NONCE_BYTES = 16
internal const val CLAIM_STALE_MILLIS = 5 * 60 * 1000L
internal const val COPY_BUFFER_SIZE = 64 * 1024
internal val OWNERSHIP_RANDOM = SecureRandom()
internal val DIRECTORY_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
)
internal val FILE_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
)
