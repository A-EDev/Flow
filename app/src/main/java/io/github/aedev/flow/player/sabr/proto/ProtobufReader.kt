package io.github.aedev.flow.player.sabr.proto

class ProtobufReader(private val data: ByteArray) {
    private var pos = 0

    val isAtEnd: Boolean get() = pos >= data.size

    data class Field(
        val fieldNumber: Int,
        val wireType: Int,
        val varintValue: Long = 0,
        val bytesValue: ByteArray = EMPTY_BYTES
    ) {
        fun asInt(): Int = varintValue.toInt()
        fun asLong(): Long = varintValue
        fun asBool(): Boolean = varintValue != 0L
        fun asString(): String = bytesValue.toString(Charsets.UTF_8)
        fun asBytes(): ByteArray = bytesValue
        fun asMessage(): ProtobufReader = ProtobufReader(bytesValue)

        companion object {
            private val EMPTY_BYTES = ByteArray(0)
        }
    }

    fun readField(): Field? {
        if (isAtEnd) return null

        val tag = readRawVarint()
        val fieldNumber = (tag shr 3).toInt()
        val wireType = (tag and 0x7).toInt()

        return when (wireType) {
            WIRE_VARINT -> {
                val value = readRawVarint()
                Field(fieldNumber, wireType, varintValue = value)
            }
            WIRE_FIXED64 -> {
                var value = 0L
                for (i in 0 until 8) {
                    value = value or ((data[pos++].toLong() and 0xFF) shl (i * 8))
                }
                Field(fieldNumber, wireType, varintValue = value)
            }
            WIRE_LENGTH_DELIMITED -> {
                val length = readRawVarint().toInt()
                val bytes = data.copyOfRange(pos, pos + length)
                pos += length
                Field(fieldNumber, wireType, bytesValue = bytes)
            }
            WIRE_FIXED32 -> {
                var value = 0
                for (i in 0 until 4) {
                    value = value or ((data[pos++].toInt() and 0xFF) shl (i * 8))
                }
                Field(fieldNumber, wireType, varintValue = value.toLong())
            }
            3, 4 -> {
                Field(fieldNumber, wireType)
            }
            else -> throw IllegalStateException("Unknown wire type: $wireType at position $pos")
        }
    }

    fun readAllFields(): Map<Int, List<Field>> {
        val result = mutableMapOf<Int, MutableList<Field>>()
        while (!isAtEnd) {
            val field = readField() ?: break
            result.getOrPut(field.fieldNumber) { mutableListOf() }.add(field)
        }
        return result
    }

    fun forEachField(handler: (Field) -> Unit) {
        while (!isAtEnd) {
            val field = readField() ?: break
            handler(field)
        }
    }

    private fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift >= 64) throw IllegalStateException("Varint too long")
        }
        return result
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5
    }
}
