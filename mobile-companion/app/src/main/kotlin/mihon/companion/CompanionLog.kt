package mihon.companion

import android.util.Log

/** Thin logging facade so no failure is swallowed silently (see CLAUDE.md rule 3). */
object CompanionLog {
    fun warn(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
    }

    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }
}
