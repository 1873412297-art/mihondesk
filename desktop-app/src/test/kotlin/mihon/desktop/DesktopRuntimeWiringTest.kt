package mihon.desktop

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class DesktopRuntimeWiringTest {
    @Test
    fun `a failing deferred step does not abort the following services`() {
        val ran = mutableListOf<String>()

        runDeferredStep("broken") { error("cannot scan local media") }
        runDeferredStep("backup") { ran += "backup" }
        runDeferredStep("sync") { ran += "sync" }

        ran shouldContainExactly listOf("backup", "sync")
    }
}
