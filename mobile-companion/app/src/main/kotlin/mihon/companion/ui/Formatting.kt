package mihon.companion.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import mihon.companion.R

/** Relative time like "5 minutes ago" / "3 天前", in the system language. */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()

/** Mihon's SManga status codes. */
@StringRes
fun statusLabel(status: Int): Int = when (status) {
    1 -> R.string.status_ongoing
    2 -> R.string.status_completed
    3 -> R.string.status_licensed
    4 -> R.string.status_publishing_finished
    5 -> R.string.status_cancelled
    6 -> R.string.status_hiatus
    else -> R.string.status_unknown
}
