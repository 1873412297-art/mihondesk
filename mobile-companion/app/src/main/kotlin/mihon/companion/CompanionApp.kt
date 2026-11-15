package mihon.companion

import android.app.Application

class CompanionApp : Application() {
    /** Assembled once per process; everything else receives its dependencies from here. */
    val runtime: CompanionRuntime by lazy { CompanionRuntime.create(this) }
}
