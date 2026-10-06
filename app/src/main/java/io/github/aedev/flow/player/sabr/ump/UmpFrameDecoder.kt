package io.github.aedev.flow.player.sabr.ump

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException

data class UmpFrame(
    val type: Int,
    val payload: ByteArray,
) {
    override fun toString(): String = "UmpFrame(type=${UmpPartType.nameOf(type)}, payloadSize=${payload.size})"
}

class UmpFrameDecoder(
    maxRetainedBytes: Int = DEFAULT_MAX_RETAINED_BYTES,
) {
    companion object {
        private const val TAG = "UmpFrameDecoder"
        const val DEFAULT_MAX_RETAINED_BYTES = 32 * 1024 * 1024
        private const val MAX_QUEUED_FRAMES = 4096
    }

    class ResourceLimitExceeded(
        message: String,
        /** The media itag the overflow belongs to, when it is known; null for stream-level rejects. */
        val itag: Int? = null,
    ) : IOException(message)

    private val maxRetainedBytes =
        maxRetainedBytes.toLong().also {
            require(it > 0) { "maxRetainedBytes must be positive" }
        }

    private val buffer = ByteArrayOutputStream(8192)
    private var bufferData = ByteArray(0)
    private var bufferPos = 0
    private var bufferLen = 0
    private var dirty = false

    private var currentType = -1
    private var currentSize = -1L
    private var currentPayloadRead = 0L
    private var payloadAccumulator: ByteArrayOutputStream? = null

    private val frameQueue = ArrayDeque<UmpFrame>()
    private var queuedPayloadBytes = 0L

    fun feed(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
    ) {
        require(offset >= 0 && length >= 0 && offset <= data.size - length) { "Invalid UMP input range" }
        if (length <= 0) return
        val retained = retainedByteCount()
        if (length.toLong() > maxRetainedBytes - retained) {
            reject("UMP buffered data exceeds $maxRetainedBytes bytes")
        }

        if (dirty || bufferPos < bufferLen) {
            buffer.write(data, offset, length)
            dirty = true
        } else {
            bufferData = data
            bufferPos = offset
            bufferLen = offset + length
            dirty = false
        }

        parseAvailable()
    }

    fun hasNext(): Boolean = frameQueue.isNotEmpty()

    fun next(): UmpFrame {
        val frame = frameQueue.removeFirst()
        queuedPayloadBytes -= frame.payload.size
        return frame
    }

    fun reset() {
        buffer.reset()
        bufferData = ByteArray(0)
        bufferPos = 0
        bufferLen = 0
        dirty = false
        currentType = -1
        currentSize = -1L
        currentPayloadRead = 0L
        payloadAccumulator = null
        frameQueue.clear()
        queuedPayloadBytes = 0L
    }

    private fun parseAvailable() {
        if (dirty) {
            val leftover =
                if (bufferPos < bufferLen) {
                    bufferData.copyOfRange(bufferPos, bufferLen)
                } else {
                    ByteArray(0)
                }
            val accumulated = buffer.toByteArray()
            buffer.reset()

            bufferData =
                if (leftover.isNotEmpty()) {
                    leftover + accumulated
                } else {
                    accumulated
                }
            bufferPos = 0
            bufferLen = bufferData.size
            dirty = false
        }

        while (bufferPos < bufferLen) {
            if (currentType == -1) {
                val typeResult = tryReadVarInt() ?: break
                currentType = typeResult.toInt()
            }

            if (currentSize == -1L) {
                val sizeResult = tryReadVarInt() ?: break
                currentSize = sizeResult
                if (currentSize > maxRetainedBytes) {
                    reject("UMP frame declares $currentSize bytes; limit is $maxRetainedBytes")
                }
                currentPayloadRead = 0L
                payloadAccumulator =
                    if (currentSize > 0) {
                        ByteArrayOutputStream(currentSize.coerceAtMost(65536).toInt())
                    } else {
                        null
                    }
            }

            val remaining = currentSize - currentPayloadRead
            if (remaining > 0) {
                val available = (bufferLen - bufferPos).toLong()
                val toRead = remaining.coerceAtMost(available).toInt()
                if (toRead > 0) {
                    payloadAccumulator?.write(bufferData, bufferPos, toRead)
                    bufferPos += toRead
                    currentPayloadRead += toRead
                }

                if (currentPayloadRead < currentSize) {
                    break
                }
            }

            val payload = payloadAccumulator?.toByteArray() ?: ByteArray(0)
            if (frameQueue.size >= MAX_QUEUED_FRAMES) {
                reject("UMP frame queue exceeds $MAX_QUEUED_FRAMES frames")
            }
            frameQueue.addLast(UmpFrame(currentType, payload))
            queuedPayloadBytes += payload.size

            Log.v(TAG, "Decoded frame: ${UmpPartType.nameOf(currentType)}, size=${payload.size}")

            currentType = -1
            currentSize = -1L
            currentPayloadRead = 0L
            payloadAccumulator = null
        }

        if (bufferPos < bufferLen) {
            val leftover = bufferData.copyOfRange(bufferPos, bufferLen)
            buffer.reset()
            buffer.write(leftover)
            bufferData = ByteArray(0)
            bufferPos = 0
            bufferLen = 0
            dirty = true
        }
    }

    private fun tryReadVarInt(): Long? {
        if (bufferPos >= bufferLen) return null

        val firstByte = bufferData[bufferPos].toInt() and 0xFF
        val size =
            try {
                UmpVarInt.sizeOf(firstByte)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid varint first byte: 0x${firstByte.toString(16)}", e)
                bufferPos++
                return null
            }

        if (bufferPos + size > bufferLen) return null

        val value = UmpVarInt.decode(bufferData, bufferPos)
        bufferPos += size
        return value
    }

    private fun retainedByteCount(): Long {
        val pendingInputBytes =
            if (dirty) {
                buffer.size().toLong()
            } else {
                (bufferLen - bufferPos).toLong()
            }
        return queuedPayloadBytes + currentPayloadRead + pendingInputBytes
    }

    private fun reject(message: String): Nothing {
        reset()
        throw ResourceLimitExceeded(message)
    }
}
