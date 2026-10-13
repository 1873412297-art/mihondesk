package mihon.desktop.extension.compat

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Pulls the launcher icon out of an Android extension APK.
 *
 * Resolution order:
 * 1. `<application android:icon>` from the binary manifest, resolved through `resources.arsc`, picking the
 *    highest-density bitmap (png/webp/jpg) among the resource's configurations.
 * 2. When that resolves only to XML (adaptive icons) or the table cannot be parsed, the best-looking
 *    `res/mipmap*` / `res/drawable*` launcher bitmap by density.
 *
 * The result is re-encoded as PNG when possible so callers can always save it as `icon.png`.
 */
object ApkIconExtractor {
    private val IMAGE_EXTENSIONS = listOf(".png", ".webp", ".jpg", ".jpeg")
    private val QUALIFIER_DENSITY = mapOf(
        "xxxhdpi" to 640,
        "xxhdpi" to 480,
        "xhdpi" to 320,
        "tvdpi" to 213,
        "hdpi" to 240,
        "mdpi" to 160,
        "ldpi" to 120,
        "nodpi" to 0xFFFF,
        "anydpi" to 0xFFFE,
    )

    fun extract(zip: ZipFile): ByteArray? {
        val raw = runCatching { extractRaw(zip) }.getOrNull() ?: return null
        return toPng(raw) ?: raw
    }

    internal fun extractRaw(zip: ZipFile): ByteArray? {
        val path = resolveIconPath(zip) ?: return null
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }.takeIf { it.isNotEmpty() }
    }

    internal fun resolveIconPath(zip: ZipFile): String? {
        val fromTable = runCatching {
            val manifest = zip.getEntry("AndroidManifest.xml")?.let { e ->
                zip.getInputStream(e).use { it.readBytes() }
            }
            val arsc = zip.getEntry("resources.arsc")?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
            if (manifest == null || arsc == null) return@runCatching null
            val resId = applicationIconResId(manifest) ?: return@runCatching null
            resolveFilePaths(arsc, resId)
                .filter { (_, path) -> isImage(path) && zip.getEntry(path) != null }
                .maxByOrNull { (density, _) -> densityRank(density) }
                ?.second
        }.getOrNull()
        return fromTable
            ?: heuristicIconPath(
                zip.entries().asSequence().filter {
                    !it.isDirectory
                }.map { it.name to it.size }.toList(),
            )
    }

    /** Fallback when the resource table gives no bitmap: best launcher-ish bitmap under res/. */
    internal fun heuristicIconPath(entries: List<Pair<String, Long>>): String? {
        val bitmaps = entries.filter { (name, _) ->
            name.startsWith("res/") && isImage(name) &&
                (
                    // Shrunk/obfuscated APKs flatten resources to res/<hash>.png.
                    name.count { it == '/' } == 1 ||
                        (
                            name.count { it == '/' } == 2 &&
                                (name.startsWith("res/mipmap") || name.startsWith("res/drawable"))
                            )
                    )
        }
        if (bitmaps.isEmpty()) return null
        fun score(name: String): Int {
            val file = name.substringAfterLast('/').substringBeforeLast('.').lowercase()
            var s = 0
            if (file.startsWith("ic_launcher")) s += 4
            if (file == "ic_launcher") s += 2
            if (file.contains("round")) s -= 1
            if (file.contains("foreground") || file.contains("background") || file.contains("monochrome")) s -= 3
            if (name.startsWith("res/mipmap")) s += 1
            return s
        }
        return bitmaps.maxWithOrNull(
            compareBy<Pair<String, Long>> { score(it.first) }
                .thenBy { densityRank(qualifierDensity(it.first.substringAfter("res/").substringBefore('/'))) }
                .thenBy { it.second },
        )?.first
    }

    private fun isImage(path: String) = IMAGE_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }

    private fun qualifierDensity(dir: String): Int {
        val parts = dir.split('-')
        for (p in parts) {
            QUALIFIER_DENSITY[p]?.let { return it }
            if (p.endsWith("dpi")) p.removeSuffix("dpi").toIntOrNull()?.let { return it }
        }
        return 0 // default density (mdpi-equivalent, but rank below explicit densities)
    }

    /** Bitmap selection prefers the highest real density; nodpi/anydpi/default rank lowest. */
    private fun densityRank(density: Int): Int = when (density) {
        0xFFFF, 0xFFFE -> 1
        0 -> 0
        else -> density + 10
    }

    /** Returns the raw resource id of `<application android:icon="@...">`, or null. */
    internal fun applicationIconResId(manifest: ByteArray): Int? {
        val buf = ByteBuffer.wrap(manifest).order(ByteOrder.LITTLE_ENDIAN)
        if (manifest.size < 8 || buf.getInt(0) != 0x00080003) return null
        var pos = 8
        var strings: List<String> = emptyList()
        while (pos + 8 <= manifest.size) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val size = buf.getInt(pos + 4)
            if (size < 8 || size > manifest.size - pos) return null
            when (type) {
                0x0001 -> strings = readStringPool(buf, manifest, pos)
                0x0102 -> {
                    val tag = strings.getOrNull(buf.getInt(pos + 20))
                    if (tag == "application") {
                        val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                        val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                        val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                        var a = pos + 16 + attrStart
                        repeat(attrCount) {
                            if (a + 20 > pos + size) return null
                            val name = strings.getOrNull(buf.getInt(a + 4))
                            val dataType = buf.get(a + 15).toInt() and 0xFF
                            val data = buf.getInt(a + 16)
                            if (name == "icon" && dataType == 0x01 && data != 0) return data
                            a += attrSize
                        }
                        return null
                    }
                }
            }
            pos += size
        }
        return null
    }

    /** Resolves a resource id to (density, file path) for every configuration that stores a string path. */
    internal fun resolveFilePaths(arsc: ByteArray, resId: Int, depth: Int = 0): List<Pair<Int, String>> {
        if (depth > 4) return emptyList()
        val buf = ByteBuffer.wrap(arsc).order(ByteOrder.LITTLE_ENDIAN)
        if (arsc.size < 12 || (buf.getShort(0).toInt() and 0xFFFF) != 0x0002) return emptyList()
        val wantPkg = (resId ushr 24) and 0xFF
        val wantType = (resId ushr 16) and 0xFF
        val wantEntry = resId and 0xFFFF
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        var pos = headerSize
        var globalStrings: List<String> = emptyList()
        val results = mutableListOf<Pair<Int, String>>()
        val references = mutableListOf<Int>()
        while (pos + 8 <= arsc.size) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val size = buf.getInt(pos + 4)
            if (size < 8 || size > arsc.size - pos) break
            when (type) {
                0x0001 -> globalStrings = readStringPool(buf, arsc, pos)
                0x0200 -> {
                    val pkgId = buf.getInt(pos + 8)
                    if (pkgId == wantPkg) {
                        val pkgHeader = buf.getShort(pos + 2).toInt() and 0xFFFF
                        var c = pos + pkgHeader
                        val end = pos + size
                        while (c + 8 <= end) {
                            val cType = buf.getShort(c).toInt() and 0xFFFF
                            val cHeader = buf.getShort(c + 2).toInt() and 0xFFFF
                            val cSize = buf.getInt(c + 4)
                            if (cSize < 8 || cSize > end - c) break
                            if (cType == 0x0201 && (buf.get(c + 8).toInt() and 0xFF) == wantType) {
                                readTypeEntry(buf, c, cHeader, cSize, wantEntry)?.let { (density, dataType, data) ->
                                    when (dataType) {
                                        0x03 -> globalStrings.getOrNull(data)?.let { results += density to it }
                                        0x01 -> references += data
                                    }
                                }
                            }
                            c += cSize
                        }
                    }
                }
            }
            pos += size
        }
        references.distinct().forEach { results += resolveFilePaths(arsc, it, depth + 1) }
        return results
    }

    private fun readTypeEntry(buf: ByteBuffer, c: Int, header: Int, size: Int, wantEntry: Int): Triple<Int, Int, Int>? {
        val flags = buf.get(c + 9).toInt() and 0xFF
        val entryCount = buf.getInt(c + 12)
        val entriesStart = buf.getInt(c + 16)
        val configStart = c + 20
        val density = if (buf.getInt(configStart) >= 16) buf.getShort(configStart + 14).toInt() and 0xFFFF else 0
        val sparse = flags and 0x01 != 0
        val offset16 = flags and 0x02 != 0
        val offsetsAt = c + header
        val entryOffset: Int = when {
            sparse -> {
                var found = -1
                for (i in 0 until entryCount) {
                    val idx = buf.getShort(offsetsAt + i * 4).toInt() and 0xFFFF
                    if (idx == wantEntry) {
                        found = (buf.getShort(offsetsAt + i * 4 + 2).toInt() and 0xFFFF) * 4
                        break
                    }
                }
                found
            }
            wantEntry >= entryCount -> -1
            offset16 -> {
                val v = buf.getShort(offsetsAt + wantEntry * 2).toInt() and 0xFFFF
                if (v == 0xFFFF) -1 else v * 4
            }
            else -> buf.getInt(offsetsAt + wantEntry * 4)
        }
        if (entryOffset < 0) return null
        val e = c + entriesStart + entryOffset
        if (e + 8 > c + size) return null
        val entrySize = buf.getShort(e).toInt() and 0xFFFF
        val entryFlags = buf.getShort(e + 2).toInt() and 0xFFFF
        if (entryFlags and 0x0008 != 0) { // FLAG_COMPACT: value packed in the entry itself
            return Triple(density, (entryFlags ushr 8) and 0xFF, buf.getInt(e + 4))
        }
        if (entryFlags and 0x0001 != 0) return null // complex/bag entries carry no file path
        val v = e + entrySize
        if (v + 8 > c + size) return null
        return Triple(density, buf.get(v + 3).toInt() and 0xFF, buf.getInt(v + 4))
    }

    private fun readStringPool(buf: ByteBuffer, bytes: ByteArray, pos: Int): List<String> {
        val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
        val chunkSize = buf.getInt(pos + 4)
        val count = buf.getInt(pos + 8)
        val utf8 = (buf.getInt(pos + 16) and (1 shl 8)) != 0
        val base = pos + buf.getInt(pos + 20)
        val out = ArrayList<String>(count.coerceIn(0, 1_000_000))
        for (i in 0 until count) {
            var p = base + buf.getInt(pos + headerSize + i * 4)
            if (p < 0 || p >= pos + chunkSize) {
                out += ""
                continue
            }
            if (utf8) {
                val l1 = bytes[p].toInt() and 0xFF
                p += if (l1 and 0x80 != 0) 2 else 1
                val b = bytes[p++].toInt() and 0xFF
                val len = if (b and 0x80 != 0) ((b and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF) else b
                out += if (p + len <= bytes.size) String(bytes, p, len, Charsets.UTF_8) else ""
            } else {
                var len = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                if (len and 0x8000 != 0) {
                    len = ((len and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
                    p += 2
                }
                out += if (p + len * 2 <= bytes.size) String(bytes, p, len * 2, Charsets.UTF_16LE) else ""
            }
        }
        return out
    }

    internal fun toPng(bytes: ByteArray): ByteArray? = runCatching {
        if (bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            return bytes
        }
        org.jetbrains.skia.Image.makeFromEncoded(bytes)
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
            ?.bytes
    }.getOrNull()
}
