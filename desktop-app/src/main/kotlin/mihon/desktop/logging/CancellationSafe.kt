package mihon.desktop.logging

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for code that suspends: a [CancellationException] is rethrown instead of being
 * turned into a failed [Result], so a cancelled caller stops instead of carrying on with a fallback.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
