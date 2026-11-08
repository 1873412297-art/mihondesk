package android.content

import android.app.Application
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FileSharedPreferencesTest {
    @Test fun `typed values survive application recreation and remain package isolated`(@TempDir root: Path) {
        val first = Application(root.resolve("first").toFile(), "extension.first")
        first.getSharedPreferences("source", Context.MODE_PRIVATE).edit()
            .putString("token", "secret")
            .putInt("page", 3)
            .putLong("updated", 42L)
            .putFloat("ratio", 1.5f)
            .putBoolean("enabled", true)
            .putStringSet("genres", linkedSetOf("action", "drama"))
            .commit() shouldBe true

        val restored = Application(root.resolve("first").toFile(), "extension.first")
            .getSharedPreferences("source", Context.MODE_PRIVATE)
        restored.getString("token", null) shouldBe "secret"
        restored.getInt("page", 0) shouldBe 3
        restored.getLong("updated", 0L) shouldBe 42L
        restored.getFloat("ratio", 0f) shouldBe 1.5f
        restored.getBoolean("enabled", false) shouldBe true
        restored.getStringSet("genres", null) shouldBe linkedSetOf("action", "drama")

        val isolated = Application(root.resolve("second").toFile(), "extension.second")
            .getSharedPreferences("source", Context.MODE_PRIVATE)
        isolated.getAll().shouldBeEmpty()
    }

    @Test fun `removal and clear are persisted not only changed in memory`(@TempDir root: Path) {
        val preferences = Application(root.toFile()).getSharedPreferences("source", Context.MODE_PRIVATE)
        preferences.edit().putString("remove", "value").putString("clear", "value").commit() shouldBe true
        preferences.edit().remove("remove").commit() shouldBe true
        preferences.edit().clear().commit() shouldBe true

        Application(root.toFile()).getSharedPreferences("source", Context.MODE_PRIVATE).getAll().shouldBeEmpty()
    }
}
