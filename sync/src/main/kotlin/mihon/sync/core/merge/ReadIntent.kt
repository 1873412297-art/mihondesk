package mihon.sync.core.merge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Read state of one chapter across devices: "keep the furthest progress, unless someone
 * deliberately marked it unread later".
 *
 * Each device records, in the chapter's memo JSON, when the chapter was last marked read
 * ([READ_AT]) and when it was last deliberately marked unread ([UNREAD_AT]). Both only grow, so
 * merging them with max() converges in any order. The chapter is read when its newest read is
 * newer than its newest deliberate unread.
 *
 * Data without these keys (older versions, Mihon backups) behaves as before: a chapter marked
 * read counts from its modification time, and with no unread mark anywhere, read on either side
 * means read. Every fallback errs towards keeping progress.
 */
object ReadIntent {
    const val READ_AT = "sync_read_at"
    const val UNREAD_AT = "sync_unread_at"

    /** One side of a merge: the chapter's read flag, modification time and memo JSON. */
    data class Side(val read: Boolean, val lastModifiedAt: Long, val memoJson: String?)

    /**
     * [readAt] and [unreadAt] are the newest times either side *recorded*; only those go back into
     * the memo. Times derived from lastModifiedAt decide [read] but are not written, so merging
     * data that carries no intents leaves its memo untouched (merges stay idempotent and
     * order-independent).
     */
    data class Merged(val read: Boolean, val readAt: Long, val unreadAt: Long)

    fun merge(a: Side, b: Side): Merged {
        val newestRead = maxOf(effectiveReadAt(a), effectiveReadAt(b))
        val unreadAt = maxOf(timeOf(a.memoJson, UNREAD_AT), timeOf(b.memoJson, UNREAD_AT))
        return Merged(
            read = newestRead > 0 && newestRead > unreadAt,
            readAt = maxOf(timeOf(a.memoJson, READ_AT), timeOf(b.memoJson, READ_AT)),
            unreadAt = unreadAt,
        )
    }

    /** The time this side last saw the chapter read; 0 when it never did. */
    fun effectiveReadAt(side: Side): Long {
        val recorded = timeOf(side.memoJson, READ_AT)
        // Reads that bypass the memo (the reader marking a chapter finished, older versions) still
        // bump lastModifiedAt, so a chapter that is read counts from at least that time.
        return if (side.read) maxOf(recorded, side.lastModifiedAt, 1L) else recorded
    }

    /** Records a deliberate read/unread made at [at]; times only ever grow. */
    fun stamp(memoJson: String?, read: Boolean, at: Long): String {
        val key = if (read) READ_AT else UNREAD_AT
        return withTimes(memoJson, mapOf(key to maxOf(timeOf(memoJson, key), at)))
    }

    /** Writes merged times into [memoJson]; returns it unchanged when it already holds them. */
    fun withMerged(memoJson: String?, merged: Merged): String {
        if (timeOf(memoJson, READ_AT) == merged.readAt && timeOf(memoJson, UNREAD_AT) == merged.unreadAt) {
            return memoJson ?: "{}"
        }
        return withTimes(memoJson, mapOf(READ_AT to merged.readAt, UNREAD_AT to merged.unreadAt))
    }

    fun timeOf(memoJson: String?, key: String): Long {
        if (memoJson.isNullOrBlank()) return 0L
        return try {
            Json.decodeFromString<JsonObject>(memoJson)[key]?.jsonPrimitive?.longOrNull ?: 0L
        } catch (_: IllegalArgumentException) {
            0L // Unreadable memo: no recorded intent, which falls back to the read flag.
        }
    }

    private fun withTimes(memoJson: String?, times: Map<String, Long>): String {
        val root = try {
            if (memoJson.isNullOrBlank()) JsonObject(emptyMap()) else Json.decodeFromString<JsonObject>(memoJson)
        } catch (_: IllegalArgumentException) {
            JsonObject(emptyMap()) // An unreadable memo is replaced rather than kept half-written.
        }
        val updated = root.toMutableMap()
        times.forEach { (key, time) -> if (time > 0) updated[key] = JsonPrimitive(time) }
        return JsonObject(updated).toString()
    }
}
