package mihon.desktop.library.backup

import java.io.ByteArrayOutputStream

/**
 * The minimal subset of the ProtoBuf wire format the Android backup layout needs: varints,
 * length-delimited fields and fixed32 floats.
 *
 * Hand-rolled on purpose. Using the kotlinx serializer would mean reading the test payloads through
 * the same annotations under test, which is exactly what [AndroidWireFormat] must stay independent of.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun long(field: Int, value: Long) {
        tag(field, VARINT)
        writeVarint(value)
    }

    fun int(field: Int, value: Int) = long(field, value.toLong())

    fun bool(field: Int, value: Boolean) = long(field, if (value) 1L else 0L)

    fun float(field: Int, value: Float) {
        tag(field, FIXED32)
        val bits = value.toRawBits()
        repeat(4) { index -> out.write((bits ushr (8 * index)) and 0xff) }
    }

    fun string(field: Int, value: String?) {
        if (value == null) return
        bytes(field, value.encodeToByteArray())
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, block: ProtoWriter.() -> Unit) {
        bytes(field, ProtoWriter().apply(block).toByteArray())
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint((field.toLong() shl 3) or wireType.toLong())

    private fun writeVarint(value: Long) {
        var remaining = value
        while (remaining and 0x7fL.inv() != 0L) {
            out.write(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        out.write(remaining.toInt())
    }

    companion object {
        const val VARINT = 0
        const val FIXED32 = 5
        const val LENGTH_DELIMITED = 2
    }
}

class ProtoReader(private val bytes: ByteArray) {
    private var index = 0
    private var wireType = 0

    val hasMore: Boolean get() = index < bytes.size

    fun nextField(): Int {
        val tag = readVarint().toInt()
        wireType = tag and 7
        return tag ushr 3
    }

    fun readLong(): Long = readVarint()

    fun readBool(): Boolean = readVarint() != 0L

    fun readFloat(): Float {
        var bits = 0
        repeat(4) { shift ->
            bits = bits or ((bytes[index++].toInt() and 0xff) shl (8 * shift))
        }
        return Float.fromBits(bits)
    }

    fun readString(): String = readBytes().decodeToString()

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        val slice = bytes.copyOfRange(index, index + length)
        index += length
        return slice
    }

    fun skip() {
        when (wireType) {
            ProtoWriter.VARINT -> readVarint()
            ProtoWriter.FIXED32 -> index += 4
            1 -> index += 8
            ProtoWriter.LENGTH_DELIMITED -> readBytes()
            else -> error("unsupported wire type $wireType at $index")
        }
    }

    /** Reads the whole message once, so repeated lookups do not each re-walk the stream. */
    fun collect(): Fields {
        val values = mutableMapOf<Int, Any>()
        while (hasMore) {
            val field = nextField()
            when (wireType) {
                ProtoWriter.VARINT -> values[field] = readVarint()
                ProtoWriter.LENGTH_DELIMITED -> values[field] = readBytes()
                ProtoWriter.FIXED32 -> values[field] = readFloat()
                1 -> index += 8
                else -> error("unsupported wire type $wireType at $index")
            }
        }
        return Fields(values)
    }

    class Fields(private val values: Map<Int, Any>) {
        fun long(field: Int): Long = when (val value = values[field]) {
            null -> 0L
            is Long -> value
            is ByteArray -> value.decodeToString().toLong()
            else -> error("field $field is not a varint: $value")
        }

        fun string(field: Int): String? = when (val value = values[field]) {
            null -> null
            is ByteArray -> value.decodeToString()
            else -> error("field $field is not length-delimited: $value")
        }
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val current = bytes[index++].toInt()
            result = result or ((current and 0x7f).toLong() shl shift)
            if (current and 0x80 == 0) return result
            shift += 7
        }
    }
}
