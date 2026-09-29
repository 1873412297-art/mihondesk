package mihon.sync.server

/** A bounded set of buckets: callers use only configured token identities and one anonymous key. */
internal class FixedWindowRateLimiter(
    private val maxRequests: Int,
    private val windowMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Window(var start: Long, var count: Int)

    private val windows = mutableMapOf<String, Window>()

    init {
        require(maxRequests > 0)
        require(windowMillis > 0)
    }

    /** Returns seconds until retry, or null when the request may proceed. */
    @Synchronized
    fun acquire(identity: String): Long? {
        val now = nowMillis()
        val window = windows.getOrPut(identity) { Window(now, 0) }
        if (now - window.start >= windowMillis) {
            window.start = now
            window.count = 0
        }
        if (window.count >= maxRequests) {
            return ((window.start + windowMillis - now + 999) / 1000).coerceAtLeast(1)
        }
        window.count++
        return null
    }
}
