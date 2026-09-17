package mihon.desktop

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.db.DesktopLibraryDatabaseOpenException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class DatabaseUpgradeFailureTest {
    private val database = Path.of("C:/private-profile/database/library.db")
    private val directory = database.resolveSibling("migration-backups")
    private val sensitive = IllegalStateException("private title /token-secret")

    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_UPGRADE_APP")?.let { packaged ->
            val type = Class.forName("mihon.desktop.DatabaseUpgradeFailureKt")
            val origin = Path.of(type.protectionDomain.codeSource.location.toURI()).toAbsolutePath()
            origin.startsWith(Path.of(packaged).toAbsolutePath()) shouldBe true
            println("PACKAGED_UPGRADE ${type.name}: $origin")
        }
    }

    @ParameterizedTest
    @EnumSource(AppLanguage::class, names = ["English", "SimplifiedChinese", "TraditionalChinese"])
    fun `interactive snapshot failure gives localized action and safe structured output`(language: AppLanguage) {
        val strings = DesktopStrings.resolve(language)
        val output = ByteArrayOutputStream()
        var shown: Pair<String, String>? = null
        reportDatabaseUpgradeFailure(
            DesktopLibraryDatabaseOpenException.SnapshotFailed(database, directory, sensitive),
            true,
            output,
            strings,
        ) { title, message ->
            shown = title to message
        } shouldBe true
        shown shouldBe
            (strings.text(UiText.UpgradeFailedTitle) to strings.text(UiText.UpgradeSnapshotFailed, directory))
        shown!!.second shouldNotContain "token-secret"
        output.toString("UTF-8") shouldContain "DATABASE_SNAPSHOT_FAILED"
        output.toString("UTF-8") shouldNotContain "private-profile"
        output.toString("UTF-8") shouldNotContain "token-secret"
    }

    @Test
    fun `background failures report category without opening any window`() {
        val errors = listOf(
            DesktopLibraryDatabaseOpenException.SnapshotFailed(database, directory, sensitive),
            DesktopLibraryDatabaseOpenException.MigrationFailed(database, 1, 3, sensitive, directory.resolve("old.db")),
        )
        errors.forEach { error ->
            val output = ByteArrayOutputStream()
            var shown = false
            reportDatabaseUpgradeFailure(error, false, output, DesktopStrings.resolve(AppLanguage.English)) { _, _ ->
                shown = true
            } shouldBe true
            shown shouldBe false
            output.toString("UTF-8") shouldContain "FAILED"
        }
    }

    @Test
    fun `failed migration shows preserved snapshot location and never raw cause`() {
        val snapshot = directory.resolve("before.db")
        var shown = ""
        reportDatabaseUpgradeFailure(
            DesktopLibraryDatabaseOpenException.MigrationFailed(database, 1, 3, sensitive, snapshot),
            true,
            ByteArrayOutputStream(),
            DesktopStrings.resolve(AppLanguage.English),
        ) { _, message ->
            shown = message
        } shouldBe true
        shown shouldContain snapshot.toString()
        shown shouldContain "do not delete"
        shown shouldNotContain "token-secret"
    }

    @Test
    fun `unrelated startup errors retain existing handler`() {
        val output = ByteArrayOutputStream()
        reportDatabaseUpgradeFailure(sensitive, true, output, DesktopStrings.resolve(AppLanguage.English)) { _, _ ->
            error("Unexpected dialog")
        } shouldBe false
        output.size() shouldBe 0
    }
}
