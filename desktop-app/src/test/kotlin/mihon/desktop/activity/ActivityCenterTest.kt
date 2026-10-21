package mihon.desktop.activity

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class ActivityCenterTest {
    private var now = 1_000L
    private val center = ActivityCenter(clock = { now })

    @Test
    fun `begin, progress and succeed update a single entry`() {
        center.begin("sync", ActivityKind.SYNC)
        center.items.value.single().state shouldBe ActivityState.RUNNING
        center.progress("sync", current = 2, total = 4)
        center.items.value.single().progress shouldBe 0.5f
        center.succeed("sync")
        center.items.value.single().state shouldBe ActivityState.SUCCEEDED
        center.items.value.single().progress shouldBe null
    }

    @Test
    fun `failures keep the reason and retry re-runs the action`() {
        var retried = 0
        center.begin("ext:foo", ActivityKind.EXTENSION_INSTALL, subject = "Foo")
        center.fail("ext:foo", reason = "Checksum mismatch", retry = { retried++ })
        val failed = center.items.value.single()
        failed.state shouldBe ActivityState.FAILED
        failed.failureReason shouldBe "Checksum mismatch"
        center.summary.value shouldBe ActivitySummary(running = 0, failed = 1)

        center.retry("ext:foo") shouldBe true
        retried shouldBe 1
        center.items.value.single().state shouldBe ActivityState.RUNNING
        center.items.value.single().failureReason shouldBe null
    }

    @Test
    fun `retry without an action is refused`() {
        center.begin("x", ActivityKind.SYNC)
        center.fail("x", reason = "nope")
        center.retry("x") shouldBe false
    }

    @Test
    fun `track records success and failure of a suspending block`() = runBlocking {
        center.track("lib", ActivityKind.LIBRARY_UPDATE) { it.progress(1, 3) } shouldBe Unit
        center.items.value.single().state shouldBe ActivityState.SUCCEEDED

        assertThrows<IOException> {
            runBlocking { center.track("sync", ActivityKind.SYNC) { throw IOException("Connection refused") } }
        }
        val failed = center.items.value.first { it.id == "sync" }
        failed.state shouldBe ActivityState.FAILED
        failed.failureReason shouldBe "Connection refused"
    }

    @Test
    fun `running first, then failed, then most recent finished, finished entries are capped`() {
        center.begin("done-old", ActivityKind.SYNC)
        center.succeed("done-old")
        now += 10
        center.begin("failed", ActivityKind.SYNC)
        center.fail("failed", "x")
        now += 10
        center.begin("running", ActivityKind.DOWNLOAD)
        now += 10
        center.begin("done-new", ActivityKind.SYNC)
        center.succeed("done-new")

        center.items.value.map { it.id } shouldBe listOf("running", "failed", "done-new", "done-old")

        repeat(ActivityCenter.MAX_FINISHED + 5) { i ->
            center.begin("f$i", ActivityKind.SYNC)
            center.succeed("f$i")
        }
        center.items.value.count { it.state == ActivityState.SUCCEEDED } shouldBe ActivityCenter.MAX_FINISHED
    }

    @Test
    fun `dismiss and clearFinished never drop running work`() {
        center.begin("a", ActivityKind.DOWNLOAD)
        center.begin("b", ActivityKind.SYNC)
        center.fail("b", "err")
        center.begin("c", ActivityKind.SYNC)
        center.succeed("c")
        center.clearFinished()
        center.items.value.map { it.id } shouldBe listOf("a")
        center.dismiss("a")
        center.items.value.map { it.id } shouldBe listOf("a")
    }

    @Test
    fun `publish replaces or removes an externally derived entry`() {
        center.publish(ActivityItem(id = "downloads", kind = ActivityKind.DOWNLOAD, state = ActivityState.RUNNING))
        center.items.value.single().id shouldBe "downloads"
        center.publish(null, id = "downloads")
        center.items.value shouldBe emptyList()
    }
}
