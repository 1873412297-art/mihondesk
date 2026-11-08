package mihon.desktop.extension

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Test-only helpers for generating signed JAR fixtures with the JDK keytool/jarsigner tools.
 * Returns null when the tools are unavailable so tests can skip gracefully.
 */
object ExtensionSignatureTestFixtures {

    data class SignedJarFixture(
        val file: File,
        val fingerprint: String,
        val payload: ByteArray,
    )

    fun createSignedJar(
        target: File,
        payload: ByteArray = "hello extension".toByteArray(),
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): SignedJarFixture? {
        val keytool = jdkTool("keytool") ?: return null
        val jarsigner = jdkTool("jarsigner") ?: return null
        val workDir = Files.createTempDirectory("mihon-signature-fixture").toFile()
        try {
            val unsignedJar = File(workDir, "unsigned.jar")
            JarOutputStream(FileOutputStream(unsignedJar)).use { jar ->
                jar.putNextEntry(JarEntry("payload.txt"))
                jar.write(payload)
                jar.closeEntry()
                jar.putNextEntry(JarEntry("assets/data.bin"))
                jar.write(ByteArray(32) { it.toByte() })
                jar.closeEntry()
                for ((name, bytes) in extraEntries) {
                    jar.putNextEntry(JarEntry(name))
                    jar.write(bytes)
                    jar.closeEntry()
                }
            }

            val alias = "testkey"
            val password = "changeit"
            val keystore = File(workDir, "keystore.p12")
            val keytoolResult = runTool(
                keytool.absolutePath,
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-storetype", "PKCS12",
                "-keystore", keystore.absolutePath,
                "-storepass", password,
                "-keypass", password,
                "-dname", "CN=Mihon Test Extension, OU=Dev, O=Mihon, C=US",
                "-noprompt",
            )
            if (!keytoolResult) return null

            val jarsignerResult = runTool(
                jarsigner.absolutePath,
                "-keystore", keystore.absolutePath,
                "-storepass", password,
                "-keypass", password,
                "-signedjar", target.absolutePath,
                unsignedJar.absolutePath,
                alias,
            )
            if (!jarsignerResult) return null

            val loaded = KeyStore.getInstance("PKCS12")
            FileInputStream(keystore).use { loaded.load(it, password.toCharArray()) }
            val certificate = loaded.getCertificate(alias) as? X509Certificate ?: return null
            return SignedJarFixture(
                file = target,
                fingerprint = ExtensionSignatureVerifier.sha256Hex(certificate.encoded),
                payload = payload,
            )
        } catch (_: Exception) {
            return null
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Copies a signed archive entry-for-entry and appends an extra entry that no v1 signature
     * covers. Entry contents are unchanged, so the original signers stay valid.
     */
    fun appendUnsignedEntry(source: File, target: File, entryName: String, bytes: ByteArray) {
        ZipFile(source).use { zip ->
            ZipOutputStream(FileOutputStream(target)).use { output ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (entry.isDirectory) continue
                    output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    zip.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
                output.putNextEntry(ZipEntry(entryName))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    /** Rewrites [source] with [entryName] replaced by [replacement], leaving signature files intact. */
    fun tamperJar(source: File, target: File, entryName: String, replacement: ByteArray) {
        val entries = mutableListOf<Triple<String, ByteArray, Long>>()
        ZipFile(source).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (entry.isDirectory) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                entries += Triple(entry.name, bytes, entry.time)
            }
        }

        ZipOutputStream(FileOutputStream(target)).use { output ->
            for ((name, bytes, time) in entries) {
                val entry = ZipEntry(name).apply { this.time = time }
                output.putNextEntry(entry)
                output.write(if (name == entryName) replacement else bytes)
                output.closeEntry()
            }
        }
    }

    /**
     * Creates a ZIP that carries an APK Signing Block magic but no v1 JAR signature. This models a
     * v2/v3-only APK without requiring apksigner to be installed.
     */
    fun createApkSigningBlockOnlyApk(target: File) {
        val plain = ByteArrayOutputStream()
        ZipOutputStream(plain).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest package=\"ext.test.v2\" />".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val original = plain.toByteArray()
        val eocdOffset = findEocd(original)
        require(eocdOffset >= 0) { "Generated ZIP has no EOCD" }
        val centralDirectoryOffset = readIntLe(original, eocdOffset + 16)
        require(centralDirectoryOffset in 0..original.size) { "Invalid central directory offset" }

        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val block = ByteArray(8 + 8 + magic.size)
        writeLongLe(block, 0, block.size.toLong())
        writeLongLe(block, 8, block.size.toLong())
        magic.copyInto(block, 16)

        val updated = ByteArray(original.size + block.size)
        original.copyInto(updated, 0, 0, centralDirectoryOffset)
        block.copyInto(updated, centralDirectoryOffset)
        original.copyInto(updated, centralDirectoryOffset + block.size, centralDirectoryOffset, original.size)

        val newEocdOffset = eocdOffset + block.size
        writeIntLe(updated, newEocdOffset + 16, centralDirectoryOffset + block.size)
        target.writeBytes(updated)
    }

    /**
     * Signs a ZIP with APK Signature Scheme v2 only (no v1 JAR signature), like current official
     * extension repositories do. Returns null when keytool is unavailable.
     */
    fun createV2OnlySignedApk(
        target: File,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): SignedJarFixture? {
        val keytool = jdkTool("keytool") ?: return null
        val workDir = Files.createTempDirectory("mihon-v2-fixture").toFile()
        try {
            val unsigned = File(workDir, "unsigned.apk")
            val payload = "hello v2 extension".toByteArray()
            ZipOutputStream(FileOutputStream(unsigned)).use { zip ->
                // apksig requires a binary manifest, as every real APK has.
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(minimalBinaryManifest())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("payload.txt"))
                zip.write(payload)
                zip.closeEntry()
                for ((name, bytes) in extraEntries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            val alias = "testkey"
            val password = "changeit"
            val keystore = File(workDir, "keystore.p12")
            val ok = runTool(
                keytool.absolutePath, "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-storetype", "PKCS12", "-keystore", keystore.absolutePath,
                "-storepass", password, "-keypass", password,
                "-dname", "CN=Mihon Test V2 Extension, OU=Dev, O=Mihon, C=US", "-noprompt",
            )
            if (!ok) return null
            val loaded = KeyStore.getInstance("PKCS12")
            FileInputStream(keystore).use { loaded.load(it, password.toCharArray()) }
            val certificate = loaded.getCertificate(alias) as X509Certificate
            val key = loaded.getKey(alias, password.toCharArray()) as java.security.PrivateKey
            val signer = com.android.apksig.ApkSigner.SignerConfig.Builder("CERT", key, listOf(certificate)).build()
            com.android.apksig.ApkSigner.Builder(listOf(signer))
                .setInputApk(unsigned)
                .setOutputApk(target)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()
                .sign()
            return SignedJarFixture(target, ExtensionSignatureVerifier.sha256Hex(certificate.encoded), payload)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** A minimal Android binary XML document containing only an empty `<manifest>` element. */
    private fun minimalBinaryManifest(): ByteArray {
        val name = "manifest".toByteArray(Charsets.UTF_8)
        // UTF-8 string: char length, byte length, bytes, NUL terminator; padded to 4 bytes.
        val stringData = byteArrayOf(name.size.toByte(), name.size.toByte()) + name + byteArrayOf(0)
        val paddedStrings = stringData + ByteArray((4 - stringData.size % 4) % 4)
        val poolHeader = 28
        val poolSize = poolHeader + 4 + paddedStrings.size
        val buffer = java.nio.ByteBuffer.allocate(8 + poolSize + 36 + 24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0x0003).putShort(8).putInt(8 + poolSize + 36 + 24)
        buffer.putShort(0x0001).putShort(poolHeader.toShort()).putInt(poolSize)
        buffer.putInt(1).putInt(0).putInt(0x100).putInt(poolHeader + 4).putInt(0)
        buffer.putInt(0).put(paddedStrings)
        buffer.putShort(0x0102).putShort(16).putInt(36)
        buffer.putInt(1).putInt(-1).putInt(-1).putInt(0)
        buffer.putShort(20).putShort(20).putShort(0).putShort(0).putShort(0).putShort(0)
        buffer.putShort(0x0103).putShort(16).putInt(24)
        buffer.putInt(1).putInt(-1).putInt(-1).putInt(0)
        return buffer.array()
    }

    /** Flips one byte inside the stored ZIP entry data, which invalidates a v2 signature digest. */
    fun corruptFirstEntryData(file: File) {
        val bytes = file.readBytes()
        // Local file header: data starts after 30 bytes + name length + extra length.
        val nameLen = (bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)
        val extraLen = (bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)
        val dataStart = 30 + nameLen + extraLen
        bytes[dataStart] = (bytes[dataStart].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
    }

    private fun jdkTool(name: String): File? {
        val executable = if (System.getProperty("os.name").lowercase().contains("win")) "$name.exe" else name
        val file = File(System.getProperty("java.home"), "bin/$executable")
        return file.takeIf { it.isFile }
    }

    private fun runTool(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun findEocd(bytes: ByteArray): Int {
        for (index in bytes.size - 22 downTo 0) {
            if (bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4B.toByte() &&
                bytes[index + 2] == 0x05.toByte() && bytes[index + 3] == 0x06.toByte()
            ) {
                return index
            }
        }
        return -1
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeIntLe(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeLongLe(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            bytes[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
        }
    }
}
