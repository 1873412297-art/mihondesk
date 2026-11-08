package mihon.sync.core.merge

/**
 * Shared last-writer-wins rule for tracking rows: higher chapter progress wins; equal progress is
 * resolved by the device id so every device converges to the same row regardless of merge path.
 */
fun <T : Comparable<T>> trackingIncomingWins(
    existingLastChapterRead: T,
    existingDeviceId: String,
    incomingLastChapterRead: T,
    incomingDeviceId: String,
): Boolean = if (incomingLastChapterRead != existingLastChapterRead) {
    incomingLastChapterRead > existingLastChapterRead
} else {
    incomingDeviceId > existingDeviceId
}
