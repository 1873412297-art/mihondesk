package mihon.sync.core.merge

import io.kotest.matchers.shouldBe
import mihon.sync.core.model.AndroidBackupChapter
import org.junit.jupiter.api.Test

class ReadIntentTest {
    private fun side(read: Boolean, modified: Long = 0, readAt: Long = 0, unreadAt: Long = 0): ReadIntent.Side {
        var memo = "{}"
        if (readAt > 0) memo = ReadIntent.stamp(memo, read = true, at = readAt)
        if (unreadAt > 0) memo = ReadIntent.stamp(memo, read = false, at = unreadAt)
        return ReadIntent.Side(read, modified, memo)
    }

    @Test
    fun `without recorded intents read on either side wins, as before`() {
        ReadIntent.merge(side(true), side(false)).read shouldBe true
        ReadIntent.merge(side(false), side(true)).read shouldBe true
        ReadIntent.merge(side(false), side(false)).read shouldBe false
    }

    @Test
    fun `a deliberate unread made after the read wins`() {
        // Read on the desktop at 100, marked unread on the phone at 200.
        ReadIntent.merge(side(true, modified = 100, readAt = 100), side(false, modified = 200, unreadAt = 200))
            .read shouldBe false
    }

    @Test
    fun `reading again after an unread wins`() {
        ReadIntent.merge(side(true, modified = 300, readAt = 300), side(false, modified = 200, unreadAt = 200))
            .read shouldBe true
    }

    @Test
    fun `a read that only bumped lastModifiedAt still counts from that time`() {
        // The reader marks a chapter finished without writing the memo; an older unread must not undo it.
        ReadIntent.merge(side(true, modified = 300, readAt = 100), side(false, modified = 200, unreadAt = 200))
            .read shouldBe true
    }

    @Test
    fun `an unread beats a read from an older version that carries no intent`() {
        ReadIntent.merge(side(true, modified = 100), side(false, modified = 200, unreadAt = 200)).read shouldBe false
    }

    @Test
    fun `merge order does not matter`() {
        val sides = listOf(
            side(true, modified = 100, readAt = 100),
            side(false, modified = 200, unreadAt = 200),
            side(true, modified = 300, readAt = 300),
            side(false, modified = 150, unreadAt = 150),
        )
        fun fold(order: List<ReadIntent.Side>) = order.reduce { a, b ->
            val merged = ReadIntent.merge(a, b)
            ReadIntent.Side(merged.read, maxOf(a.lastModifiedAt, b.lastModifiedAt), ReadIntent.withMerged("{}", merged))
        }.read
        val expected = fold(sides)
        expected shouldBe true // the read at 300 is the newest change
        fold(sides.reversed()) shouldBe expected
        fold(listOf(sides[2], sides[0], sides[3], sides[1])) shouldBe expected
    }

    @Test
    fun `mergeChapter keeps the merged times in the memo so later merges agree`() {
        val read = AndroidBackupChapter(url = "c", name = "c", read = true, lastModifiedAt = 100)
        val unread = AndroidBackupChapter(
            url = "c",
            name = "c",
            read = false,
            lastModifiedAt = 200,
            memo = ReadIntent.stamp("{}", read = false, at = 200).encodeToByteArray(),
        )
        val merged = SyncMergePolicy.mergeChapter(existing = read, incoming = unread)
        merged.read shouldBe false
        ReadIntent.timeOf(merged.memo.decodeToString(), ReadIntent.UNREAD_AT) shouldBe 200
        SyncMergePolicy.mergeChapter(existing = unread, incoming = read).read shouldBe false
    }
}
