package io.github.aedev.flow.player.sabr.ump

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UmpVarInt {

    fun sizeOf(firstByte: Int): Int {
        val b = firstByte and 0xFF
        return when {
            b and 0x80 == 0    -> 1
            b and 0xC0 == 0x80 -> 2
            b and 0xE0 == 0xC0 -> 3
            b and 0xF0 == 0xE0 -> 4
            b and 0xF0 == 0xF0 -> 5
            else -> throw IllegalStateException("Invalid UMP varint first byte: 0x${b.toString(16)}")
        }
    }

    fun decode(data: ByteArray, offset: Int): Long {
        val firstByte = data[offset].toInt() and 0xFF
        val size = sizeOf(firstByte)
        return decodeBytes(data, offset, size, firstByte)
    }

    fun read(input: InputStream): Long {
        val firstByte = input.read()
        if (firstByte == -1) return -1
        val size = sizeOf(firstByte)

        if (size == 1) return (firstByte and 0x7F).toLong()

        val remaining = ByteArray(size - 1)
        var read = 0
        while (read < remaining.size) {
            val n = input.read(remaining, read, remaining.size - read)
            if (n == -1) throw IllegalStateException("Unexpected end of stream reading UMP varint")
            read += n
        }

        val full = ByteArray(size)
        full[0] = firstByte.toByte()
        System.arraycopy(remaining, 0, full, 1, remaining.size)
        return decodeBytes(full, 0, size, firstByte)
    }

    // Canonical UMP varint layout (gsuberland/UMP_Format, LuanRT/googlevideo UmpReader):
    // the remaining bits of the FIRST byte are the LOW-order bits of the value, and the
    // following bytes are combined little-endian, shifted above them.
    //   2-byte: (b0 & 0x3F) | (b1 << 6)
    //   3-byte: (b0 & 0x1F) | ((b1 | (b2 << 8)) << 5)
    //   4-byte: (b0 & 0x0F) | ((b1 | (b2 << 8) | (b3 << 16)) << 4)
    //   5-byte: prefix bits ignored; bytes 1..4 are a little-endian uint32
    private fun decodeBytes(data: ByteArray, offset: Int, size: Int, firstByte: Int): Long {
        return when (size) {
            1 -> (firstByte and 0x7F).toLong()

            2 -> {
                val b1 = (data[offset + 1].toInt() and 0xFF).toLong()
                (firstByte and 0x3F).toLong() or (b1 shl 6)
            }

            3 -> {
                val b1 = (data[offset + 1].toInt() and 0xFF).toLong()
                val b2 = (data[offset + 2].toInt() and 0xFF).toLong()
                (firstByte and 0x1F).toLong() or ((b1 or (b2 shl 8)) shl 5)
            }

            4 -> {
                val b1 = (data[offset + 1].toInt() and 0xFF).toLong()
                val b2 = (data[offset + 2].toInt() and 0xFF).toLong()
                val b3 = (data[offset + 3].toInt() and 0xFF).toLong()
                (firstByte and 0x0F).toLong() or ((b1 or (b2 shl 8) or (b3 shl 16)) shl 4)
            }

            5 -> {
                val buf = ByteBuffer.wrap(data, offset + 1, 4).order(ByteOrder.LITTLE_ENDIAN)
                buf.int.toLong() and 0xFFFFFFFFL
            }

            else -> throw IllegalStateException("Invalid UMP varint size: $size")
        }
    }

    fun encode(value: Long): ByteArray {
        return when {
            value < 0 -> throw IllegalArgumentException("UMP varint cannot be negative: $value")

            value <= 0x7F -> byteArrayOf(value.toByte())

            value <= 0x3FFF -> byteArrayOf(
                (0x80 or (value and 0x3F).toInt()).toByte(),
                ((value shr 6) and 0xFF).toByte()
            )

            value <= 0x1FFFFF -> byteArrayOf(
                (0xC0 or (value and 0x1F).toInt()).toByte(),
                ((value shr 5) and 0xFF).toByte(),
                ((value shr 13) and 0xFF).toByte()
            )

            value <= 0x0FFFFFFF -> byteArrayOf(
                (0xE0 or (value and 0x0F).toInt()).toByte(),
                ((value shr 4) and 0xFF).toByte(),
                ((value shr 12) and 0xFF).toByte(),
                ((value shr 20) and 0xFF).toByte()
            )

            value <= 0xFFFFFFFFL -> {
                val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(value.toInt())
                byteArrayOf(0xF0.toByte(), buf.array()[0], buf.array()[1], buf.array()[2], buf.array()[3])
            }

            else -> throw IllegalArgumentException("UMP varint value too large: $value")
        }
    }

    fun encodedSize(value: Long): Int = when {
        value <= 0x7F -> 1
        value <= 0x3FFF -> 2
        value <= 0x1FFFFF -> 3
        value <= 0x0FFFFFFF -> 4
        value <= 0xFFFFFFFFL -> 5
        else -> throw IllegalArgumentException("UMP varint value too large: $value")
    }
}
