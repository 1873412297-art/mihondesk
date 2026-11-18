package mihon.desktop.library.db

import mihon.desktop.library.model.ChapterRecord
import mihon.sync.core.merge.ReadIntent

/**
 * Called for every chapter write made by the app (detail page, batch actions, update feed,
 * backup import). When the read flag flips, records when it happened in the memo so sync can
 * tell a deliberate "mark unread" from a device that simply has not seen the read yet
 * ([ReadIntent]), and bumps lastModifiedAt so the change is exported at all.
 *
 * Sync's own apply path writes merged rows directly and never comes through here.
 */
internal fun ChapterRecord.stampReadChange(stored: ChapterRecord?, now: Long): ChapterRecord {
    if (stored == null || stored.read == read) return this
    return copy(
        memoJson = ReadIntent.stamp(memoJson, read, now),
        lastModifiedAt = maxOf(lastModifiedAt, now),
    )
}
