package mihon.desktop.extension

import mihon.extension.model.ExtensionManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionPackageDomainsTest {
    @Test
    fun `legacy domain discovery recovers exact hosts and preserves explicit restrictions`(@TempDir directory: Path) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
        writer.visitField(
            Opcodes.ACC_STATIC,
            "site",
            "Ljava/lang/String;",
            null,
            "https://www.example.org/path",
        ).visitEnd()
        writer.visitField(
            Opcodes.ACC_STATIC,
            "api",
            "Ljava/lang/String;",
            null,
            "https://api.example.org:8443/v1",
        ).visitEnd()
        writer.visitEnd()
        val file = directory.resolve("fixture.mext").toFile()
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("Fixture.class"))
            it.write(writer.toByteArray())
            it.closeEntry()
        }
        val manifest = ExtensionManifest(
            "eu.kanade.tachiyomi.extension.en.fixture",
            "Fixture",
            "1.0",
            1,
            1.4,
            "en",
            sources = emptyList(),
        )
        assertEquals(
            setOf("www.example.org", "api.example.org"),
            ExtensionPackageDomains.resolve(file, manifest).toSet(),
        )
        assertEquals(
            listOf("explicit.example.org"),
            ExtensionPackageDomains.resolve(file, manifest.copy(declaredDomains = listOf("explicit.example.org"))),
        )
        assertEquals(emptyList<String>(), ExtensionPackageDomains.resolve(file, manifest.copy(id = "native.fixture")))
    }

    @Test
    fun `bare image host literals sharing a discovered site domain are recovered`(@TempDir directory: Path) {
        // Mirrors HipManga: image servers are listed as bare hosts, the cover CDN as a full URL.
        val literals = listOf(
            "https://cover.s3imgs.top",
            "https://m.hipmh.com",
            "hip-cf-1.s3imgs.top",
            "hip-tx-s1.s3imgs.top",
            "m.hipmh.com",
            "unrelated.evil.example",
            "kotlin.collections.List",
        )
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
        literals.forEachIndexed { index, value ->
            writer.visitField(Opcodes.ACC_STATIC, "f$index", "Ljava/lang/String;", null, value).visitEnd()
        }
        writer.visitEnd()
        val file = directory.resolve("fixture.mext").toFile()
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("Fixture.class"))
            it.write(writer.toByteArray())
            it.closeEntry()
        }
        val manifest = ExtensionManifest(
            "eu.kanade.tachiyomi.extension.zh.fixture",
            "Fixture",
            "1.4.1",
            1,
            1.4,
            "zh",
            sources = emptyList(),
        )
        assertEquals(
            setOf("cover.s3imgs.top", "m.hipmh.com", "hip-cf-1.s3imgs.top", "hip-tx-s1.s3imgs.top"),
            ExtensionPackageDomains.resolve(file, manifest).toSet(),
        )
    }
}
