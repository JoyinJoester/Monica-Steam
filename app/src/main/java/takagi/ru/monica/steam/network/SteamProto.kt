package takagi.ru.monica.steam.network

import java.io.ByteArrayOutputStream
import java.math.BigInteger

class SteamProtoWriter {
    private val out = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()

    fun writeVarint(field: Int, value: Long) {
        writeTag(field, 0)
        writeVarintRaw(value)
    }

    fun writeUint64(field: Int, value: Long) {
        writeVarint(field, value)
    }

    fun writeBool(field: Int, value: Boolean) {
        writeVarint(field, if (value) 1L else 0L)
    }

    fun writeString(field: Int, value: String) {
        writeBytes(field, value.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(field: Int, bytes: ByteArray) {
        writeTag(field, 2)
        writeVarintRaw(bytes.size.toLong())
        out.write(bytes)
    }

    fun writeMessage(field: Int, message: SteamProtoWriter) {
        writeBytes(field, message.toByteArray())
    }

    fun writeFixed64(field: Int, value: Long) {
        writeTag(field, 1)
        var current = value
        repeat(8) {
            out.write((current and 0xffL).toInt())
            current = current shr 8
        }
    }

    private fun writeTag(field: Int, wireType: Int) {
        writeVarintRaw(((field shl 3) or wireType).toLong())
    }

    private fun writeVarintRaw(value: Long) {
        var current = if (value < 0) BigInteger.valueOf(value).and(UNSIGNED_LONG_MASK) else BigInteger.valueOf(value)
        val mask = BigInteger.valueOf(0x7f)
        val cont = BigInteger.valueOf(0x80)
        while (current > mask) {
            out.write(current.and(mask).or(cont).toInt())
            current = current.shiftRight(7)
        }
        out.write(current.toInt())
    }

    companion object {
        private val UNSIGNED_LONG_MASK = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
    }
}

data class SteamProtoField(
    val number: Int,
    val wireType: Int,
    val varint: Long? = null,
    val bytes: ByteArray? = null
) {
    val asString: String
        get() = bytes?.toString(Charsets.UTF_8).orEmpty()

    val asInt: Int
        get() = varint?.toInt() ?: 0

    val asLong: Long
        get() = varint ?: 0L

    val asBool: Boolean
        get() = (varint ?: 0L) != 0L

    val asFixed64: Long
        get() {
            val value = fixed64BigInteger()
            return if (value > SIGNED_LONG_MAX) {
                value.subtract(UNSIGNED_LONG_BASE).longValueExact()
            } else {
                value.longValueExact()
            }
        }

    val asFixed64UnsignedString: String
        get() = fixed64BigInteger().toString()

    val asFixed32UnsignedLong: Long
        get() {
            val fixed = bytes ?: return 0L
            if (fixed.size < 4) return 0L
            var value = 0L
            for (index in 3 downTo 0) {
                value = (value shl 8) or (fixed[index].toInt() and 0xff).toLong()
            }
            return value
        }

    private fun fixed64BigInteger(): BigInteger {
        val fixed = bytes ?: return BigInteger.ZERO
        if (fixed.size < 8) return BigInteger.ZERO
        var value = BigInteger.ZERO
        for (index in 7 downTo 0) {
            value = value.shiftLeft(8).or(
                BigInteger.valueOf((fixed[index].toInt() and 0xff).toLong())
            )
        }
        return value
    }

    companion object {
        private val SIGNED_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
        private val UNSIGNED_LONG_BASE = BigInteger.ONE.shiftLeft(64)
    }
}

class SteamProtoReader(private val data: ByteArray) {
    private var pos = 0

    fun parseAll(): List<SteamProtoField> {
        val fields = mutableListOf<SteamProtoField>()
        while (pos < data.size) {
            val key = readVarintRaw()
            val field = (key shr 3).toInt()
            val wire = (key and 0x7L).toInt()
            when (wire) {
                0 -> fields += SteamProtoField(field, wire, varint = readVarintRaw())
                1 -> fields += SteamProtoField(field, wire, bytes = readFixed(8))
                2 -> {
                    val length = readVarintRaw().toInt()
                    fields += SteamProtoField(field, wire, bytes = readBytes(length))
                }
                5 -> fields += SteamProtoField(field, wire, bytes = readFixed(4))
                else -> error("Unsupported protobuf wire type $wire")
            }
        }
        return fields
    }

    fun parse(): Map<Int, SteamProtoField> = parseAll().associateBy { it.number }

    private fun readVarintRaw(): Long {
        var shift = 0
        var result = BigInteger.ZERO
        while (pos < data.size) {
            val byte = data[pos++].toInt() and 0xff
            result = result.or(BigInteger.valueOf((byte and 0x7f).toLong()).shiftLeft(shift))
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return if (result > SIGNED_LONG_MAX) result.toLong() else result.longValueExact()
    }

    private fun readBytes(length: Int): ByteArray {
        val bytes = data.copyOfRange(pos, pos + length)
        pos += length
        return bytes
    }

    private fun readFixed(length: Int): ByteArray = readBytes(length)

    companion object {
        private val SIGNED_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)

        fun decodePackedVarints(bytes: ByteArray): List<Long> {
            val reader = SteamProtoReader(bytes)
            val values = mutableListOf<Long>()
            while (reader.pos < bytes.size) {
                values += reader.readVarintRaw()
            }
            return values
        }
    }
}
