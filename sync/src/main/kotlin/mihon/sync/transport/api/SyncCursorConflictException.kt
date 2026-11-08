package mihon.sync.transport.api

import java.io.IOException

/**
 * A push was rejected because the changeset cursor conflicts with the cursor history the
 * transport already holds for this device: a gap (a cursor was skipped), a non-monotonic
 * cursor, or the same cursor arriving with a different payload.
 *
 * Transports that can surface this conflict (e.g. HTTP 409) must throw it instead of a
 * generic protocol error so sync engines can attempt cursor recovery rather than failing
 * permanently on every subsequent push.
 */
class SyncCursorConflictException(
    message: String,
    cause: Throwable? = null,
    /**
     * The cursor the server will accept next for this device, when it reports one
     * (`X-Expected-Cursor`). Lets clients whose cursor history diverged from the server realign
     * instead of failing every push.
     */
    val expectedCursor: Long? = null,
) : IOException(message, cause)
