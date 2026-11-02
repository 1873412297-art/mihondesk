package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.extension.model.ExtensionManifest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** vomic only embeds "https://vomicmh.com" but calls api.vomicmh.com (host built at runtime). */
class ExtensionDomainSubdomainTest {
    private fun manifest(id: String = "eu.kanade.tachiyomi.extension.zh.vomic") =
        ExtensionManifest(id, "vomic", "1.6.1", 1, 1.6, "zh", sources = emptyList())

    private fun pkg(dir: Path, vararg literals: String) = dir.resolve("p.mext").toFile().also { file ->
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
        literals.forEachIndexed { i, v ->
            writer.visitField(Opcodes.ACC_STATIC, "f$i", "Ljava/lang/String;", null, v).visitEnd()
        }
        writer.visitEnd()
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("Fixture.class"))
            it.write(writer.toByteArray())
            it.closeEntry()
        }
    }

    @Test
    fun `subdomains of a discovered site's registrable domain are allowed`(@TempDir dir: Path) {
        val helper = DesktopNetworkHelper()
        helper.registerExtensionDomains(
            "vomic",
            ExtensionPackageDomains.resolve(pkg(dir, "https://vomicmh.com"), manifest()),
        )
        helper.isDomainAllowed("vomicmh.com", "vomic") shouldBe true
        helper.isDomainAllowed("api.vomicmh.com", "vomic") shouldBe true
        helper.isDomainAllowed("vomicmh.com.evil.net", "vomic") shouldBe false
        helper.isDomainAllowed("evilvomicmh.com", "vomic") shouldBe false
        helper.isDomainAllowed("other.com", "vomic") shouldBe false
        helper.close()
    }

    @Test
    fun `explicitly declared domains also cover their registrable domain's subdomains`(@TempDir dir: Path) {
        val resolved = ExtensionPackageDomains.resolve(
            pkg(dir),
            manifest().copy(declaredDomains = listOf("www.site.org")),
        )
        resolved.toSet() shouldBe setOf("www.site.org", "*.site.org")
    }

    @Test
    fun `public suffix hosts are not widened to the whole suffix`(@TempDir dir: Path) {
        val resolved = ExtensionPackageDomains.resolve(
            pkg(dir, "https://reader.github.io", "https://a.b.co.uk"),
            manifest(),
        )
        resolved.toSet() shouldBe setOf("reader.github.io", "*.reader.github.io", "a.b.co.uk", "*.b.co.uk")
    }
}
