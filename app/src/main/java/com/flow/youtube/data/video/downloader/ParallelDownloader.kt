package com.flow.youtube.data.video.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class ParallelDownloader @Inject constructor() {

    companion object {
        private const val TAG = "ParallelDownloader"
        private const val BUFFER_SIZE = 512 * 1024   // 512KB read buffer
        private const val BLOCK_SIZE = 1L * 1024 * 1024  // 1MB work-stealing block
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private var _client: OkHttpClient? = null

    /** Lazily build client with a connection pool scaled to thread count. */
    private fun getClient(threads: Int): OkHttpClient {
        val existing = _client
        if (existing != null) return existing
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(threads * 3, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
            .also { _client = it }
    }

    /**
     * Start downloading a mission
     *
     * @param mission The download mission with url, audioUrl, totalBytes, etc.
     * @param onProgress Called periodically with overall progress (0..1).
     * @return true if all blocks completed, false if failed or paused.
     */
    suspend fun start(mission: FlowDownloadMission, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "start: Beginning download for URL=${mission.url}, threads=${mission.threads}")
                mission.status = MissionStatus.RUNNING
                val client = getClient(mission.threads)

                if (mission.totalBytes == 0L) {
                    val length = getContentLength(client, mission.url, mission.userAgent)
                    Log.d(TAG, "start: Video content length=$length")
                    if (length <= 0) {
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get video content length"
                        return@withContext false
                    }
                    mission.totalBytes = length
                }

                if (mission.audioUrl != null && mission.audioTotalBytes == 0L) {
                    val audioLength = getContentLength(client, mission.audioUrl, mission.userAgent)
                    Log.d(TAG, "start: Audio content length=$audioLength")
                    if (audioLength <= 0) {
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get audio content length"
                        return@withContext false
                    }
                    mission.audioTotalBytes = audioLength
                }

                // 2. Prepare files
                val isDash = mission.audioUrl != null
                val videoFile = if (isDash) File("${mission.savePath}.video.tmp") else File(mission.savePath)
                val audioFile = if (isDash) File("${mission.savePath}.audio.tmp") else null

                prepareFile(videoFile, mission.totalBytes)
                audioFile?.let { prepareFile(it, mission.audioTotalBytes) }

                // 3. Calculate block counts
                val videoBlockCount = ((mission.totalBytes + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
                val audioBlockCount = if (isDash) ((mission.audioTotalBytes + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt() else 0

                // Reset atomic block counters (for fresh or resumed download)
                mission.videoBlockCounter.set(0)
                mission.audioBlockCounter.set(0)

                Log.d(TAG, "start: Video blocks=$videoBlockCount, Audio blocks=$audioBlockCount, Threads=${mission.threads}")

                coroutineScope {
                    // Launch worker threads for video
                    val videoDeferreds = (0 until mission.threads).map { threadIndex ->
                        async(Dispatchers.IO) {
                            workerLoop(
                                client = client,
                                mission = mission,
                                file = videoFile,
                                url = mission.url,
                                totalBytes = mission.totalBytes,
                                blockCounter = mission.videoBlockCounter,
                                totalBlocks = videoBlockCount,
                                isAudio = false,
                                threadName = "v$threadIndex"
                            )
                        }
                    }

                    // Launch worker threads for audio (if DASH)
                    val audioDeferreds = if (isDash && audioFile != null) {
                        val audioThreads = (mission.threads / 2).coerceIn(2, mission.threads)
                        (0 until audioThreads).map { threadIndex ->
                            async(Dispatchers.IO) {
                                workerLoop(
                                    client = client,
                                    mission = mission,
                                    file = audioFile,
                                    url = mission.audioUrl!!,
                                    totalBytes = mission.audioTotalBytes,
                                    blockCounter = mission.audioBlockCounter,
                                    totalBlocks = audioBlockCount,
                                    isAudio = true,
                                    threadName = "a$threadIndex"
                                )
                            }
                        }
                    } else emptyList()

                    val allResults = (videoDeferreds + audioDeferreds).awaitAll()
                    val allSuccess = allResults.all { it }
                    Log.d(TAG, "start: All workers done. Success=$allSuccess")

                    if (allSuccess) {
                        if (!isDash) {
                            mission.status = MissionStatus.FINISHED
                            mission.finishTime = System.currentTimeMillis()
                        }
                        true
                    } else {
                        if (mission.status == MissionStatus.PAUSED) {
                            Log.d(TAG, "start: Download paused")
                        } else {
                            Log.e(TAG, "start: One or more workers failed")
                            mission.status = MissionStatus.FAILED
                            mission.error = mission.error ?: "One or more workers failed"
                        }
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical download error", e)
                if (mission.status != MissionStatus.PAUSED) {
                    mission.status = MissionStatus.FAILED
                    mission.error = e.message
                }
                false
            }
        }
    }

    /**
     * Worker loop: repeatedly grab the next block via atomic counter and download it.
     * Exits when all blocks are claimed or the mission is no longer running.
     */
    private fun workerLoop(
        client: OkHttpClient,
        mission: FlowDownloadMission,
        file: File,
        url: String,
        totalBytes: Long,
        blockCounter: java.util.concurrent.atomic.AtomicInteger,
        totalBlocks: Int,
        isAudio: Boolean,
        threadName: String
    ): Boolean {
        var blocksDownloaded = 0
        while (mission.status == MissionStatus.RUNNING) {
            val blockIndex = blockCounter.getAndIncrement()
            if (blockIndex >= totalBlocks) break 

            val startByte = blockIndex.toLong() * BLOCK_SIZE
            val endByte = min(startByte + BLOCK_SIZE - 1, totalBytes - 1)

            val success = downloadBlockWithRetry(
                client = client,
                mission = mission,
                file = file,
                url = url,
                startByte = startByte,
                endByte = endByte,
                isAudio = isAudio,
                blockName = "$threadName-b$blockIndex"
            )

            if (!success) {
                if (mission.status == MissionStatus.PAUSED) return false
                Log.e(TAG, "Worker $threadName failed on block $blockIndex")
                return false
            }
            blocksDownloaded++
        }
        Log.d(TAG, "Worker $threadName finished ($blocksDownloaded blocks)")
        return mission.status == MissionStatus.RUNNING || mission.status == MissionStatus.FINISHED
    }

    /**
     * Download a single block with exponential backoff retry.
     */
    private fun downloadBlockWithRetry(
        client: OkHttpClient,
        mission: FlowDownloadMission,
        file: File,
        url: String,
        startByte: Long,
        endByte: Long,
        isAudio: Boolean,
        blockName: String
    ): Boolean {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_RETRIES) {
            if (mission.status != MissionStatus.RUNNING) return false

            try {
                val result = downloadBlock(client, mission, file, url, startByte, endByte, isAudio)
                if (result) return true
                if (mission.status == MissionStatus.PAUSED) return false
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Block $blockName attempt ${attempt + 1} failed: ${e.message}")
            }

            attempt++
            if (attempt < MAX_RETRIES && mission.status == MissionStatus.RUNNING) {
                val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return false }
            }
        }

        Log.e(TAG, "Block $blockName failed after $MAX_RETRIES retries", lastError)
        mission.error = "Block $blockName failed: ${lastError?.message}"
        return false
    }

    /**
     * Download a single byte-range block and write directly to RandomAccessFile.
     * No locks — progress is tracked via AtomicLong in mission.
     */
    private fun downloadBlock(
        client: OkHttpClient,
        mission: FlowDownloadMission,
        file: File,
        url: String,
        startByte: Long,
        endByte: Long,
        isAudio: Boolean
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$startByte-$endByte")
            .header("User-Agent", mission.userAgent)
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val code = response.code
                Log.e(TAG, "Block download failed: HTTP $code (range=$startByte-$endByte)")
                if (code == 403) {
                    mission.error = "URL expired (403). Re-fetch needed."
                }
                return false
            }

            val inputStream = response.body?.byteStream() ?: return false
            val buffer = ByteArray(BUFFER_SIZE)
            var offset = startByte

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startByte)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (mission.status != MissionStatus.RUNNING) return false

                    raf.write(buffer, 0, bytesRead)
                    offset += bytesRead

                    mission.updateProgress(bytesRead.toLong(), isAudio)
                }
            }

            return true
        } finally {
            response.close()
        }
    }

    private fun prepareFile(file: File, size: Long) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() != size) raf.setLength(size)
        }
    }

    private fun getContentLength(client: OkHttpClient, url: String, userAgent: String): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent)
                .build()
            val response = client.newCall(request).execute()
            val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
            response.close()
            length
        } catch (e: Exception) {
            Log.e(TAG, "Content length check failed: url=$url, error=${e.message}")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", userAgent)
                    .build()
                val response = client.newCall(request).execute()
                val range = response.header("Content-Range")
                val total = range?.substringAfter("/")?.toLongOrNull() ?: -1L
                response.close()
                if (total > 0) return total
            } catch (e2: Exception) {
                Log.e(TAG, "Content length GET retry failed", e2)
            }
            -1L
        }
    }
}
