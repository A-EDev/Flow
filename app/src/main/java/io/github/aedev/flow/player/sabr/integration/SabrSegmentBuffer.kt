package io.github.aedev.flow.player.sabr.integration

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SabrSegmentBuffer {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    private val closed = AtomicBoolean(false)
    private val endOfStream = AtomicBoolean(false)

    fun appendSegment(data: ByteArray) {
        if (closed.get()) return
        if (data.isNotEmpty()) {
            queue.put(data)
        }
    }

    fun signalEndOfStream() {
        endOfStream.set(true)
        queue.put(ByteArray(0))
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed.get()) return -1

        var totalRead = 0
        while (totalRead < length) {
            if (currentChunk == null || currentOffset >= currentChunk!!.size) {
                val next = if (totalRead > 0) {
                    queue.poll()
                } else {
                    queue.poll(2, TimeUnit.SECONDS)
                }

                if (next == null) {
                    if (endOfStream.get() && queue.isEmpty()) return if (totalRead > 0) totalRead else -1
                    return if (totalRead > 0) totalRead else 0
                }

                if (next.isEmpty()) {
                    return if (totalRead > 0) totalRead else -1
                }

                currentChunk = next
                currentOffset = 0
            }

            val chunk = currentChunk!!
            val available = chunk.size - currentOffset
            val toRead = minOf(available, length - totalRead)
            System.arraycopy(chunk, currentOffset, buffer, offset + totalRead, toRead)
            currentOffset += toRead
            totalRead += toRead
        }
        return totalRead
    }

    fun close() {
        closed.set(true)
        queue.clear()
        currentChunk = null
        currentOffset = 0
    }

    fun reset() {
        queue.clear()
        currentChunk = null
        currentOffset = 0
        closed.set(false)
        endOfStream.set(false)
    }
}
