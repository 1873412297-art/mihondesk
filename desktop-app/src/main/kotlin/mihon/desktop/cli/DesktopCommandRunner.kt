package mihon.desktop.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.DesktopRuntime
import mihon.desktop.library.backup.BackupDecodeException
import mihon.desktop.library.backup.BackupValidationException
import mihon.desktop.library.local.LocalImportRejected
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.updates.DesktopAppUpdateService
import mihon.reader.image.IntRect
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.PageLoadCoordinator
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

class DesktopCommandRunner(
    internal val runtime: DesktopRuntime,
    internal val output: OutputStream = System.out,
    internal val nowMillis: () -> Long = System::currentTimeMillis,
    internal val readerVerification: suspend (Path) -> ReaderVerificationSummary = { fixtureRoot ->
        PackagedReaderVerifier(runtime, nowMillis).verify(fixtureRoot)
    },
) {
    fun run(command: DesktopCommand): Int = try {
        when (command) {
            DesktopCommand.RemoveBackgroundTasks -> {
                val scheduler = requireNotNull(runtime.backgroundScheduler) { "Packaged scheduler unavailable" }
                val failures = scheduler.removeAll().filter { it.exitCode != 0 }.map { it.output }
                writeJson(
                    BackgroundOutput(
                        "remove-background-tasks",
                        if (failures.isEmpty()) "SUCCEEDED" else "FAILED",
                        failures,
                    ),
                )
                if (failures.isEmpty()) 0 else 1
            }
            DesktopCommand.BackgroundUpdate -> {
                val scheduler = requireNotNull(runtime.libraryUpdateScheduler) { "Library scheduler unavailable" }
                val report = runBlocking { scheduler.checkAndRunAutoUpdate() }
                writeJson(
                    BackgroundOutput(
                        "background-update",
                        if (report == null) {
                            "SKIPPED"
                        } else if (report.errors.isEmpty()) {
                            "SUCCEEDED"
                        } else {
                            "FAILED"
                        },
                        report?.errors.orEmpty(),
                    ),
                )
                if (report?.errors?.isNotEmpty() == true) 1 else 0
            }
            DesktopCommand.BackgroundBackup -> {
                val scheduler = requireNotNull(runtime.backupScheduler) { "Backup scheduler unavailable" }
                val path = runBlocking { scheduler.checkAndRunAutoBackup() }
                writeJson(BackgroundOutput("background-backup", if (path == null) "SKIPPED" else "SUCCEEDED"))
                0
            }
            DesktopCommand.LaunchUi -> error("LaunchUi is not a headless command")
            DesktopCommand.Help -> {
                writeUtf8Line(output, HELP_TEXT)
                0
            }
            DesktopCommand.Version -> {
                writeUtf8Line(output, "mihondesk ${DesktopAppUpdateService.CURRENT_VERSION} (Windows x64)")
                0
            }
            DesktopCommand.FoundationSmoke -> {
                writeUtf8Line(output, "MIHON_DESKTOP_SMOKE_OK ${runtime.directories.root}")
                0
            }
            is DesktopCommand.ImportBackup -> {
                writeReport("import-backup", command.path, runtime.backupImporter.import(command.path, nowMillis()))
                0
            }
            is DesktopCommand.ExportBackup -> {
                val exporter = mihon.desktop.library.backup.AndroidBackupExporter(runtime.library)
                exporter.export(command.path)
                writeJson(ExportOutput("export-backup", "SUCCEEDED", command.path.toString()))
                0
            }
            is DesktopCommand.ImportLocal -> {
                val report = runtime.localImporter.import(command.path, runtime.localLibraryRoot, nowMillis())
                writeReport("import-local", command.path, report)
                0
            }
            DesktopCommand.ListLibraryJson -> {
                writeJson(ListLibraryOutput(items = runtime.library.librarySnapshot().map(LibraryItemOutput::from)))
                0
            }
            is DesktopCommand.VerifyReader -> {
                writeJson(runBlocking { readerVerification(command.fixtureRoot) })
                0
            }
        }
    } catch (error: BackupDecodeException) {
        writeRejected(command, error.kind.name, command.sourcePath())
        2
    } catch (error: BackupValidationException) {
        writeRejected(command, "BACKUP_VALIDATION", error.path)
        2
    } catch (_: LocalImportRejected) {
        writeRejected(command, "LOCAL_IMPORT_REJECTED", command.sourcePath())
        2
    } catch (_: IOException) {
        writeRejected(command, "INPUT_IO", command.sourcePath())
        2
    } catch (error: Throwable) {
        // Keep stdout a single JSON line, but leave the cause on stderr for CI evidence.
        error.printStackTrace(System.err)
        writeFailure(command.commandName(), command.sourcePath())
        1
    }

    internal fun writeReport(command: String, path: Path, report: ImportReport) {
        writeJson(
            ImportOutput(
                command = command,
                status = report.status.name,
                reportId = report.id,
                path = path.toString(),
                counts = CountsOutput.from(report.counts),
            ),
        )
    }

    internal fun writeRejected(command: DesktopCommand, category: String, path: String?) {
        writeJson(ErrorOutput(command.commandName(), "REJECTED", category, path))
    }

    internal fun writeFailure(command: String, path: String?) {
        writeJson(ErrorOutput(command, "FAILED", "UNEXPECTED", path))
    }

    private inline fun <reified T> writeJson(value: T) {
        writeUtf8Line(output, JSON.encodeToString(value))
    }

    companion object {
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        fun writeCommandLineError(output: OutputStream, error: CommandLineException) {
            writeUtf8Line(
                output,
                JSON.encodeToString(ErrorOutput("command-line", "REJECTED", "COMMAND_LINE", error.argument)),
            )
        }

        fun writeStartupFailure(output: OutputStream, category: String = "UNEXPECTED") {
            writeUtf8Line(output, JSON.encodeToString(ErrorOutput("startup", "FAILED", category, null)))
        }
    }
}

@Serializable
private data class BackgroundOutput(
    val command: String,
    val status: String,
    val errors: List<String> = emptyList(),
)

@Serializable
private data class ExportOutput(
    val command: String,
    val status: String,
    val path: String,
)

@Serializable
private data class ImportOutput(
    val command: String,
    val status: String,
    val reportId: Long,
    val path: String,
    val counts: CountsOutput,
)

@Serializable
private data class CountsOutput(
    val mangaInserted: Long = 0,
    val mangaMerged: Long = 0,
    val chaptersInserted: Long = 0,
    val chaptersMerged: Long = 0,
    val categoriesLinked: Long = 0,
    val preferencesImported: Long = 0,
    val preferencesSkipped: Long = 0,
) {
    companion object {
        fun from(counts: ImportCounts) = CountsOutput(
            mangaInserted = counts.mangaInserted,
            mangaMerged = counts.mangaMerged,
            chaptersInserted = counts.chaptersInserted,
            chaptersMerged = counts.chaptersMerged,
            categoriesLinked = counts.categoriesLinked,
            preferencesImported = counts.preferencesImported,
            preferencesSkipped = counts.preferencesSkipped,
        )
    }
}

@Serializable
private data class ListLibraryOutput(
    val command: String = "list-library",
    val items: List<LibraryItemOutput>,
)

@Serializable
private data class LibraryItemOutput(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val chapterCount: Long,
    val unreadCount: Long,
) {
    companion object {
        fun from(manga: LibraryManga) = LibraryItemOutput(
            id = manga.id,
            sourceId = manga.sourceId,
            url = manga.url,
            title = manga.title,
            thumbnailUrl = manga.thumbnailUrl,
            chapterCount = manga.chapterCount,
            unreadCount = manga.unreadCount,
        )
    }
}

@Serializable
private data class ErrorOutput(
    val command: String,
    val status: String,
    val category: String,
    val path: String?,
)

private fun DesktopCommand.commandName(): String = when (this) {
    DesktopCommand.RemoveBackgroundTasks -> "remove-background-tasks"
    DesktopCommand.BackgroundUpdate -> "background-update"
    DesktopCommand.BackgroundBackup -> "background-backup"
    DesktopCommand.LaunchUi -> "launch-ui"
    DesktopCommand.FoundationSmoke -> "foundation-smoke"
    DesktopCommand.Help -> "help"
    DesktopCommand.Version -> "version"
    is DesktopCommand.ImportBackup -> "import-backup"
    is DesktopCommand.ExportBackup -> "export-backup"
    is DesktopCommand.ImportLocal -> "import-local"
    DesktopCommand.ListLibraryJson -> "list-library"
    is DesktopCommand.VerifyReader -> "verify-reader"
}

private fun DesktopCommand.sourcePath(): String? = when (this) {
    is DesktopCommand.ImportBackup -> path.toString()
    is DesktopCommand.ExportBackup -> path.toString()
    is DesktopCommand.ImportLocal -> path.toString()
    is DesktopCommand.VerifyReader -> fixtureRoot.toString()
    else -> null
}

internal val HELP_TEXT = """
mihondesk - Manga Reader for Windows
Usage: mihondesk.exe [options] [file]

Options:
  --help, -h                  Show this help message and exit
  --version, -v               Show application version and exit
  --portable                  Run in portable mode using ./data directory
  --data-dir=<path>           Specify custom application data directory
  --import-backup=<path>      Import Android .tachibk backup file headlessly
  --export-backup=<path>      Export library to Android .tachibk backup file headlessly
  --import-local=<path>       Import local manga directory or CBZ/ZIP archive headlessly
  --list-library-json         Output library contents as JSON to stdout
  --remove-background-tasks   Remove current profile tasks; preserve data
  --background-update         Run one due library update without opening a window
  --background-backup         Run one due automatic backup without opening a window
  --smoke-test                Verify database initialization and exit

File Associations:
  Passing a .tachibk file directly will import the backup.
  Passing a .cbz or .zip file directly will import the archive into the local library.
""".trimIndent()

private fun writeUtf8Line(output: OutputStream, value: String) {
    output.write(value.toByteArray(UTF_8))
    output.write('\n'.code)
    output.flush()
}

internal const val READER_FIXTURE_FORMAT = "mihon-w-reader-fixture-v1"
internal const val READER_FIXTURE_MANIFEST = "reader-fixture-manifest.json"
internal const val READER_FIXTURE_MANGA = "reader-fixture-manga"
internal const val READER_FIXTURE_STANDALONE = "standalone.png"
internal const val COMMITTED_RAR_PATH =
    "reader-core/src/test/resources/mihon/reader/source/fixtures/valid-rar4.rar"
internal const val COMMITTED_RAR_SHA256 = "ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693"
internal const val COMMITTED_SOURCE_PATH = ".superpowers/sdd/task-3-fixture-source/page.png"
internal const val COMMITTED_SOURCE_SHA256 = "0197651b31b314f8ee97130efa7427813db8520c65a373a0edc48cda884b0317"
internal const val TARGET_CHAPTER_NAME = "01-directory"
internal const val TRANSITION_CHAPTER_NAME = "06-pages.epub"
internal const val UNDECODABLE_SESSION_ASSET = "cbr"
internal const val STANDALONE_CHAPTER_ID = 9_000_000_000L
internal const val EXTREME_TILE_SIZE = 1024
internal val EXPECTED_CHAPTER_NAMES = setOf(
    TARGET_CHAPTER_NAME,
    "02-pages.cbz",
    "03-pages.cbt",
    "04-pages.cb7",
    "05-pages.cbr",
    TRANSITION_CHAPTER_NAME,
)
internal val EXPECTED_ASSET_LABELS = linkedSetOf("standalone", "directory", "cbz", "cbt", "cb7", "cbr", "epub")
internal val REQUIRED_FIXTURE_PATHS = setOf(
    READER_FIXTURE_STANDALONE,
    "$READER_FIXTURE_MANGA/01-directory/40-extreme.png",
    "$READER_FIXTURE_MANGA/02-pages.cbz",
    "$READER_FIXTURE_MANGA/03-pages.cbt",
    "$READER_FIXTURE_MANGA/04-pages.cb7",
    "$READER_FIXTURE_MANGA/05-pages.cbr",
    "$READER_FIXTURE_MANGA/06-pages.epub",
)
internal val FIXTURE_JSON = Json {
    encodeDefaults = true
    explicitNulls = true
}
