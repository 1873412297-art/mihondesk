package mihon.desktop.cli

import kotlinx.serialization.decodeFromString
import mihon.desktop.DesktopRuntime
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal class PackagedReaderVerifier(
    internal val runtime: DesktopRuntime,
    internal val nowMillis: () -> Long,
    internal val environment: Map<String, String> = System.getenv(),
) {
    suspend fun verify(requestedRoot: Path): ReaderVerificationSummary = try {
        this.verifyInternal(requestedRoot)
    } catch (error: Throwable) {
        if (ReaderSoak.seconds(environment) > 0) error.printStackTrace(System.err)
        throw error
    }

    internal fun validateFixture(requestedRoot: Path): ValidatedReaderFixture {
        val root = requestedRoot.toAbsolutePath().normalize()
        check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "reader fixture root is not a directory" }
        val manifestPath = root.resolve(READER_FIXTURE_MANIFEST)
        check(Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) { "reader fixture manifest is missing" }
        val manifest = FIXTURE_JSON.decodeFromString<ReaderFixtureManifest>(Files.readString(manifestPath, UTF_8))
        check(manifest.format == READER_FIXTURE_FORMAT)
        check(manifest.mangaDirectory == READER_FIXTURE_MANGA)
        check(manifest.standaloneImage == READER_FIXTURE_STANDALONE)
        check(manifest.committedRarPath == COMMITTED_RAR_PATH)
        check(manifest.committedRarSha256 == COMMITTED_RAR_SHA256)
        check(manifest.committedSourcePath == COMMITTED_SOURCE_PATH)
        check(manifest.committedSourceSha256 == COMMITTED_SOURCE_SHA256)
        check(manifest.files == manifest.files.sortedBy { it.relativePath }) { "fixture manifest files are not sorted" }
        check(manifest.files.map { it.relativePath }.distinct().size == manifest.files.size) {
            "fixture manifest contains duplicate paths"
        }
        manifest.files.forEach { record ->
            val relative = safeRelativePath(record.relativePath)
            val file = root.resolve(relative).normalize()
            check(file.startsWith(root) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "fixture file is missing or unsafe"
            }
            check(Files.size(file) == record.sizeBytes) { "fixture file size mismatch" }
            check(sha256(file) == record.sha256) { "fixture file SHA-256 mismatch" }
        }
        val paths = manifest.files.mapTo(mutableSetOf()) { it.relativePath }
        check(paths.containsAll(REQUIRED_FIXTURE_PATHS)) { "fixture manifest does not contain the required matrix" }
        val rarRecord = manifest.files.single { it.relativePath == "$READER_FIXTURE_MANGA/05-pages.cbr" }
        check(rarRecord.sha256 == COMMITTED_RAR_SHA256) { "fixture RAR is not the committed Task 3 resource" }
        val mangaRoot = root.resolve(READER_FIXTURE_MANGA)
        val standalone = root.resolve(READER_FIXTURE_STANDALONE)
        check(Files.isDirectory(mangaRoot, LinkOption.NOFOLLOW_LINKS))
        check(Files.isRegularFile(standalone, LinkOption.NOFOLLOW_LINKS))
        return ValidatedReaderFixture(root, mangaRoot, standalone, sha256(manifestPath))
    }
}
