package mihon.desktop.reader

/**
 * Hands-free scrolling for continuous (webtoon / vertical) reading. Pure frame maths so the speed
 * policy is testable; the composable only feeds frame times in and scrolls by the result.
 */
class AutoScrollController(speedDpPerSecond: Int = DEFAULT_DP_PER_SECOND) {
    var speedDpPerSecond: Int = speedDpPerSecond.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND)
        private set

    /** Pixels to scroll for one frame. Long stalls are capped so a hitch never skips content. */
    fun deltaPixels(elapsedNanos: Long, density: Float): Float {
        val seconds = elapsedNanos.coerceIn(0L, MAX_FRAME_NANOS) / 1_000_000_000f
        return speedDpPerSecond * seconds * density
    }

    fun faster(): Int {
        speedDpPerSecond = (speedDpPerSecond + STEP_DP_PER_SECOND).coerceAtMost(MAX_DP_PER_SECOND)
        return speedDpPerSecond
    }

    fun slower(): Int {
        speedDpPerSecond = (speedDpPerSecond - STEP_DP_PER_SECOND).coerceAtLeast(MIN_DP_PER_SECOND)
        return speedDpPerSecond
    }

    companion object {
        const val DEFAULT_DP_PER_SECOND = 120
        const val MIN_DP_PER_SECOND = 20
        const val MAX_DP_PER_SECOND = 1_000
        const val STEP_DP_PER_SECOND = 20
        const val MAX_FRAME_NANOS = 100_000_000L
    }
}
