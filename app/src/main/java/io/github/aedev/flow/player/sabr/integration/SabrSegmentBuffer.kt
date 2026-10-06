package io.github.aedev.flow.player.sabr.integration

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

enum class SegmentAppendResult {
    ACCEPTED,
    CAPACITY_EXCEEDED,
    CLOSED,
}

class SabrSegmentBuffer(
    val maxBufferedBytes: Long = DEFAULT_MAX_BUFFERED_BYTES,
) {
    companion object {
        const val DEFAULT_MAX_BUFFERED_BYTES = 32L * 1024 * 1024
        private const val READ_POLL_TIMEOUT_MS = 250L
    }

    private var queue = LinkedBlockingQueue<BufferEntry>()
    private val stateLock = Any()
    private val readLock = Any()

    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    private var retainedBytes = 0L
    private var generation = 0L
    private var closed = false
    private var endOfStream = false

    val highWaterBytes: Long get() = (maxBufferedBytes / 2).coerceAtLeast(1)

    val bufferedBytes: Long
        get() = synchronized(stateLock) { retainedBytes }

    val isAtHighWater: Boolean
        get() = synchronized(stateLock) { retainedBytes >= highWaterBytes }

    init {
        require(maxBufferedBytes > 0) { "maxBufferedBytes must be positive" }
    }

    fun appendSegment(data: ByteArray): SegmentAppendResult =
        synchronized(stateLock) {
            if (closed || endOfStream) return@synchronized SegmentAppendResult.CLOSED
            if (data.isEmpty()) return@synchronized SegmentAppendResult.ACCEPTED
            val size = data.size.toLong()
            if (size > maxBufferedBytes - retainedBytes) {
                return@synchronized SegmentAppendResult.CAPACITY_EXCEEDED
            }

            queue.offer(BufferEntry.Segment(generation, data))
            retainedBytes += size
            SegmentAppendResult.ACCEPTED
        }

    fun signalEndOfStream() {
        synchronized(stateLock) {
            if (closed || endOfStream) return
            endOfStream = true
            queue.offer(BufferEntry.EndOfStream(generation))
        }
    }

    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        synchronized(readLock) read@{
            if (length == 0) return@read 0
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            val snapshot =
                synchronized(stateLock) state@{
                    if (closed) return@state null
                    generation to queue
                } ?: return@read -1
            val readGeneration = snapshot.first
            val readQueue = snapshot.second

            var totalRead = 0
            while (totalRead < length) {
                val current =
                    synchronized(stateLock) state@{
                        if (closed || generation != readGeneration) {
                            return@state null
                        }
                        currentChunk?.let { chunk ->
                            val available = chunk.size - currentOffset
                            val toRead = minOf(available, length - totalRead)
                            System.arraycopy(chunk, currentOffset, buffer, offset + totalRead, toRead)
                            currentOffset += toRead
                            totalRead += toRead
                            if (currentOffset == chunk.size) {
                                retainedBytes -= chunk.size
                                currentChunk = null
                                currentOffset = 0
                            }
                            true
                        } ?: false
                    }

                if (current == null) return@read if (totalRead > 0) totalRead else -1
                if (current) continue

                val ended =
                    synchronized(stateLock) state@{
                        if (closed || generation != readGeneration) {
                            return@read if (totalRead > 0) totalRead else -1
                        }
                        endOfStream && queue.isEmpty()
                    }
                if (ended) return@read if (totalRead > 0) totalRead else -1

                val next =
                    try {
                        if (totalRead > 0) {
                            readQueue.poll()
                        } else {
                            readQueue.poll(READ_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        }
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Interrupted while waiting for SABR media", error)
                    }
                if (next == null) {
                    if (totalRead > 0) return@read totalRead
                    continue
                }

                synchronized(stateLock) state@{
                    if (closed || generation != readGeneration) {
                        return@read if (totalRead > 0) totalRead else -1
                    }

                    when (next) {
                        is BufferEntry.Segment -> {
                            if (next.generation == generation) {
                                currentChunk = next.data
                                currentOffset = 0
                            }
                        }

                        is BufferEntry.EndOfStream -> {
                            if (next.generation == generation) {
                                return@read if (totalRead > 0) totalRead else -1
                            }
                        }

                        is BufferEntry.ResetWakeup -> {
                            Unit
                        }
                    }
                }
            }
            totalRead
        }

    fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true
            queue.clear()
            currentChunk = null
            currentOffset = 0
            retainedBytes = 0
            queue.offer(BufferEntry.EndOfStream(generation))
        }
    }

    fun reset() {
        synchronized(stateLock) {
            val previousGeneration = generation
            val oldQueue = queue
            generation++
            queue = LinkedBlockingQueue()
            oldQueue.clear()
            oldQueue.offer(BufferEntry.ResetWakeup(previousGeneration))
            currentChunk = null
            currentOffset = 0
            retainedBytes = 0
            closed = false
            endOfStream = false
        }
    }

    private sealed interface BufferEntry {
        val generation: Long

        data class Segment(
            override val generation: Long,
            val data: ByteArray,
        ) : BufferEntry

        data class EndOfStream(
            override val generation: Long,
        ) : BufferEntry

        data class ResetWakeup(
            override val generation: Long,
        ) : BufferEntry
    }
}
