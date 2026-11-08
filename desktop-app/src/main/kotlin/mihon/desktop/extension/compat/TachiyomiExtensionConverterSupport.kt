package mihon.desktop.extension.compat
import com.googlecode.d2j.dex.Dex2jar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.extension.ExtensionStoreItem
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal fun TachiyomiExtensionConverter.frameParents(type: String, loader: ClassLoader): Pair<String?, List<String>> {
    val stream = loader.getResourceAsStream("$type.class")
    if (stream == null) {
        require(type.startsWith("android/") || type.startsWith("androidx/")) {
            "Unsupported extension dependency while computing JVM frames: $type"
        }
        return "java/lang/Object" to emptyList()
    }
    return stream.use {
        org.objectweb.asm.ClassReader(it).let { reader ->
            reader.superName to
                reader.interfaces.toList()
        }
    }
}

internal fun TachiyomiExtensionConverter.rejectInvalidConstructorLowering(bytes: ByteArray, sourceName: String) {
    val node = org.objectweb.asm.tree.ClassNode()
    org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.SKIP_DEBUG)
    for (method in node.methods) {
        val instructions = method.instructions.toArray().filter { it.opcode >= 0 }
        for (window in instructions.windowed(4)) {
            val allocation = window[0] as? org.objectweb.asm.tree.TypeInsnNode ?: continue
            val constructor = window[2] as? org.objectweb.asm.tree.MethodInsnNode ?: continue
            val assignment = window[3] as? org.objectweb.asm.tree.FieldInsnNode ?: continue
            require(
                !(
                    allocation.opcode == org.objectweb.asm.Opcodes.NEW && allocation.desc == "java/lang/Object" &&
                        window[1].opcode == org.objectweb.asm.Opcodes.DUP && constructor.name == "<init>" &&
                        constructor.owner == "java/lang/Object" &&
                        assignment.opcode == org.objectweb.asm.Opcodes.PUTSTATIC &&
                        assignment.desc != "Ljava/lang/Object;"
                    ),
            ) {
                "Unsupported DEX constructor lowering in $sourceName: ${node.name}.${method.name}; " +
                    "converter created Object for ${assignment.desc}"
            }
        }
    }
}

/** Keep runtime IDs and languages while retaining the APK entry factory for the next load. */
fun TachiyomiExtensionConverter.replaceSourceIdentities(
    packageFile: File,
    manifest: ExtensionManifest,
    sources: List<SourceDescriptor>,
): ExtensionManifest {
    require(sources.isNotEmpty()) { "Extension did not expose any sources" }
    val entryClass = manifest.sources.first().className
    val resolved = manifest.copy(sources = sources.distinctBy { it.id }.map { it.copy(className = entryClass) })
    mihon.extension.validator.ExtensionPackageValidator.validateManifest(resolved)
    val replacement = File.createTempFile("mext_identity_", ".mext", packageFile.parentFile)
    try {
        ZipFile(packageFile).use { zip ->
            ZipOutputStream(replacement.outputStream()).use { output ->
                zip.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "manifest.json") {
                        output.write(json.encodeToString(resolved).toByteArray())
                    } else if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { it.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
        mihon.extension.validator.ExtensionPackageValidator.validatePackage(replacement)
        java.nio.file.Files.move(
            replacement.toPath(),
            packageFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        replacement.delete()
    }
    return resolved
}

internal fun TachiyomiExtensionConverter.generateSourceId(name: String, lang: String): Long {
    val key = "${name.lowercase()}/$lang/1"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    return buffer.long and Long.MAX_VALUE
}
