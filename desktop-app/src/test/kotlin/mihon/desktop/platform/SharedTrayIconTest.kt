package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class SharedTrayIconTest {

    @Test
    fun `every caller gets the same instance and it is created only once`() {
        var created = 0
        val once = CreateOnce { Any().also { created++ } }

        val first = once.get()
        val second = once.get()

        second!! shouldBeSameInstanceAs first!!
        created shouldBe 1
    }

    @Test
    fun `a failed creation is retried and peek never creates`() {
        var attempts = 0
        val once = CreateOnce { if (++attempts == 1) null else "icon" }

        once.peek() shouldBe null
        once.get() shouldBe null
        once.get() shouldBe "icon"
        once.get() shouldBe "icon"
        attempts shouldBe 2
        once.peek() shouldBe "icon"
    }
}
