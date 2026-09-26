package io.github.aedev.flow.data.video.downloader

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.network.AppProxyManager
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

/** Range workers for the video (or only) stream; a mission always gets at least one. */
internal fun videoWorkerCount(threads: Int): Int = threads.coerceAtLeast(1)

/** Range workers for the separate audio stream: half the connections, at least two when there are two to give. */
internal fun audioWorkerCount(threads: Int): Int {
    val connections = threads.coerceAtLeast(1)
    return (connections / 2).coerceIn(minOf(2, connections), connections)
}

@Singleton
class ParallelDownloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ParallelDownloader"
            private const val BUFFER_SIZE = 512 * 1024
            private const val BLOCK_SIZE = 2L * 1024 * 1024
            private const val MAX_RETRIES = 5
            private const val INITIAL_RETRY_DELAY_MS = 2000L
        }

        private var client: OkHttpClient? = null

        /** Lazily build client with a connection pool scaled to thread count. */
        private fun getClient(threads: Int): OkHttpClient {
            val existing = client
            if (existing != null) return existing
            return AppProxyManager
                .applyTo(OkHttpClient.Builder())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(threads * 4, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
                .also { client = it }
        }

        /**
         * Start downloading a mission
         *
         * @param mission The download mission with url, audioUrl, totalBytes, etc.
         * @param onProgress Called periodically with overall progress (0..1).
         * @return true if all blocks completed, false if failed or paused.
         */
        suspend fun start(
            mission: FlowDownloadMission,
            onProgress: (Float) -> Unit,
        ): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "start: Beginning download for URL=${mission.url}, threads=${mission.threads}")
                    mission.status = MissionStatus.RUNNING
                    val client = getClient(mission.threads)

                    // --- Resolve base URLs ---
                    // For YouTube DASH streams the original URL may contain an embedded
                    // `range=0-N` parameter placed by the extractor. That cap prevents
                    // the CDN from serving the full file.  We strip it here so all block
                    // workers can append their own `&range=X-Y` per-block.
                    val videoBaseUrl =
                        if (YouTubeStreamUrls.isYouTubeStreamUrl(mission.url)) {
                            YouTubeStreamUrls.stripRangeParam(mission.url).also {
                                Log.d(TAG, "start: Stripped range from video URL (YouTube DASH)")
                            }
                        } else {
                            mission.url
                        }

                    val audioBaseUrl =
                        mission.audioUrl?.let { audioUrl ->
                            if (YouTubeStreamUrls.isYouTubeStreamUrl(audioUrl)) {
                                YouTubeStreamUrls.stripRangeParam(audioUrl).also {
                                    Log.d(TAG, "start: Stripped range from audio URL (YouTube DASH)")
                                }
                            } else {
                                audioUrl
                            }
                        }

                    // --- Resolve content lengths ---
                    if (mission.totalBytes == 0L) {
                        // Fast path: read clen= from the original URL (before stripping)
                        val clenFromUrl =
                            if (YouTubeStreamUrls.isYouTubeStreamUrl(mission.url)) {
                                YouTubeStreamUrls.extractClenFromUrl(mission.url)
                            } else {
                                -1L
                            }

                        val length =
                            if (clenFromUrl > 0) {
                                Log.d(TAG, "start: Video clen from URL=$clenFromUrl")
                                clenFromUrl
                            } else {
                                getContentLength(client, videoBaseUrl, mission.userAgent)
                            }

                        Log.d(TAG, "start: Video content length=$length")
                        if (length <= 0) {
                            mission.status = MissionStatus.FAILED
                            mission.error = context.getString(R.string.download_failed_video_length)
                            return@withContext false
                        }
                        mission.totalBytes = length
                    }

                    if (audioBaseUrl != null && mission.audioTotalBytes == 0L) {
                        val clenFromUrl =
                            if (YouTubeStreamUrls.isYouTubeStreamUrl(mission.audioUrl ?: "")) {
                                YouTubeStreamUrls.extractClenFromUrl(mission.audioUrl ?: "")
                            } else {
                                -1L
                            }

                        val audioLength =
                            if (clenFromUrl > 0) {
                                Log.d(TAG, "start: Audio clen from URL=$clenFromUrl")
                                clenFromUrl
                            } else {
                                getContentLength(client, audioBaseUrl, mission.userAgent)
                            }

                        Log.d(TAG, "start: Audio content length=$audioLength")
                        if (audioLength <= 0) {
                            mission.status = MissionStatus.FAILED
                            mission.error = context.getString(R.string.download_failed_audio_length)
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

                    mission.videoBlockCounter.set(0)
                    mission.audioBlockCounter.set(0)

                    val restoredVideoBytes =
                        mission.completedVideoBlocks.sumOf { idx ->
                            val s = idx.toLong() * BLOCK_SIZE
                            minOf(s + BLOCK_SIZE, mission.totalBytes) - s
                        } + mission.partialVideoBlockBytes.values.sumOf { it }
                    val restoredAudioBytes =
                        mission.completedAudioBlocks.sumOf { idx ->
                            val s = idx.toLong() * BLOCK_SIZE
                            minOf(s + BLOCK_SIZE, mission.audioTotalBytes) - s
                        } + mission.partialAudioBlockBytes.values.sumOf { it }
                    mission.downloadedBytesAtomic.set(restoredVideoBytes)
                    mission.audioDownloadedBytesAtomic.set(restoredAudioBytes)

                    Log.d(
                        TAG,
                        "start: video=${mission.totalBytes}B in $videoBlockCount blocks " +
                            "(${mission.completedVideoBlocks.size} already done), " +
                            "audio=${mission.audioTotalBytes}B in $audioBlockCount blocks " +
                            "(${mission.completedAudioBlocks.size} already done), threads=${mission.threads}",
                    )

                    coroutineScope {
                        // Launch worker threads for video
                        val videoDeferreds =
                            (0 until videoWorkerCount(mission.threads)).map { threadIndex ->
                                async(Dispatchers.IO) {
                                    workerLoop(
                                        client = client,
                                        mission = mission,
                                        file = videoFile,
                                        url = videoBaseUrl, // range-stripped base URL
                                        totalBytes = mission.totalBytes,
                                        blockCounter = mission.videoBlockCounter,
                                        totalBlocks = videoBlockCount,
                                        isAudio = false,
                                        threadName = "v$threadIndex",
                                    )
                                }
                            }

                        // Launch worker threads for audio (if DASH)
                        val audioDeferreds =
                            if (isDash && audioFile != null && audioBaseUrl != null) {
                                (0 until audioWorkerCount(mission.threads)).map { threadIndex ->
                                    async(Dispatchers.IO) {
                                        workerLoop(
                                            client = client,
                                            mission = mission,
                                            file = audioFile,
                                            url = audioBaseUrl, // range-stripped base URL
                                            totalBytes = mission.audioTotalBytes,
                                            blockCounter = mission.audioBlockCounter,
                                            totalBlocks = audioBlockCount,
                                            isAudio = true,
                                            threadName = "a$threadIndex",
                                        )
                                    }
                                }
                            } else {
                                emptyList()
                            }

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
                            } else if (mission.gatedHttp403) {
                                Log.w(TAG, "start: Download stopped because direct stream returned HTTP 403")
                                mission.error = mission.error ?: context.getString(R.string.download_url_expired)
                            } else {
                                Log.e(TAG, "start: One or more workers failed")
                                mission.status = MissionStatus.FAILED
                                mission.error = mission.error ?: context.getString(R.string.download_workers_failed)
                            }
                            false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Critical download error", e)
                    if (mission.status != MissionStatus.PAUSED && !mission.gatedHttp403) {
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
            threadName: String,
        ): Boolean {
            val completedBlocks = if (isAudio) mission.completedAudioBlocks else mission.completedVideoBlocks
            var blocksDownloaded = 0
            while (mission.status == MissionStatus.RUNNING) {
                val blockIndex = blockCounter.getAndIncrement()
                if (blockIndex >= totalBlocks) break

                if (completedBlocks.contains(blockIndex)) continue

                val startByte = blockIndex.toLong() * BLOCK_SIZE
                val endByte = min(startByte + BLOCK_SIZE - 1, totalBytes - 1)

                val success =
                    downloadBlockWithRetry(
                        client = client,
                        mission = mission,
                        file = file,
                        url = url,
                        startByte = startByte,
                        endByte = endByte,
                        isAudio = isAudio,
                        blockName = "$threadName-b$blockIndex",
                        blockIndex = blockIndex,
                    )

                if (!success) {
                    if (mission.status == MissionStatus.PAUSED) return false
                    Log.e(TAG, "Worker $threadName failed on block $blockIndex")
                    return false
                }
                completedBlocks.add(blockIndex)
                blocksDownloaded++
            }
            Log.d(TAG, "Worker $threadName finished ($blocksDownloaded blocks, ${completedBlocks.size} total completed)")
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
            blockName: String,
            blockIndex: Int,
        ): Boolean {
            var attempt = 0
            var lastError: Exception? = null

            while (attempt < MAX_RETRIES) {
                if (mission.status != MissionStatus.RUNNING) return false
                if (mission.gatedHttp403) return false

                try {
                    val result = downloadBlock(client, mission, file, url, startByte, endByte, isAudio, blockIndex)
                    if (result) return true
                    if (mission.status == MissionStatus.PAUSED) return false
                    if (mission.gatedHttp403) return false
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Block $blockName attempt ${attempt + 1} failed: ${e.message}")
                    if (mission.gatedHttp403) return false
                }

                attempt++
                if (attempt < MAX_RETRIES && mission.status == MissionStatus.RUNNING) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        return false
                    }
                }
            }

            Log.e(TAG, "Block $blockName failed after $MAX_RETRIES retries", lastError)
            mission.error = context.getString(R.string.download_block_failed, blockName, lastError?.message.orEmpty())
            return false
        }

        /**
         * Download a single byte-range block and write directly to RandomAccessFile.
         * No locks — progress is tracked via AtomicLong in mission.
         *
         * For YouTube DASH URLs (googlevideo.com):
         *   - Use `&range=X-Y` query parameter (CDN-native range)
         *   - Append YouTube headers to avoid bot-detection throttling
         *   - Accept HTTP 200 (full response for query range) as well as 206
         *
         * For all other URLs:
         *   - Use standard `Range: bytes=X-Y` HTTP header
         */
        private fun downloadBlock(
            client: OkHttpClient,
            mission: FlowDownloadMission,
            file: File,
            url: String,
            startByte: Long,
            endByte: Long,
            isAudio: Boolean,
            blockIndex: Int,
        ): Boolean {
            val partialBytesMap = if (isAudio) mission.partialAudioBlockBytes else mission.partialVideoBlockBytes
            val alreadyWritten = partialBytesMap[blockIndex] ?: 0L
            val resumeFrom = startByte + alreadyWritten

            if (resumeFrom > endByte) {
                partialBytesMap.remove(blockIndex)
                return true
            }

            val isYT = YouTubeStreamUrls.isYouTubeStreamUrl(url)
            val effectiveUserAgent = YouTubeStreamUrls.resolveUserAgent(url, mission.userAgent)

            val request =
                if (isYT) {
                    val rangedUrl = YouTubeStreamUrls.buildYouTubeBlockUrl(url, resumeFrom, endByte)
                    Request
                        .Builder()
                        .url(rangedUrl)
                        .header("User-Agent", effectiveUserAgent)
                        .header("Origin", "https://www.youtube.com")
                        .header("Referer", "https://www.youtube.com/")
                        .header("Accept", "*/*")
                        .header("Accept-Encoding", "identity")
                        .build()
                } else {
                    Request
                        .Builder()
                        .url(url)
                        .header("Range", "bytes=$resumeFrom-$endByte")
                        .header("User-Agent", effectiveUserAgent)
                        .build()
                }

            val call = client.newCall(request)
            mission.activeCalls.add(call)
            val response =
                try {
                    call.execute()
                } catch (e: Exception) {
                    mission.activeCalls.remove(call)
                    throw e
                }
            var bytesWrittenInSession = 0L
            try {
                if (!response.isSuccessful) {
                    val code = response.code
                    Log.e(TAG, "Block download failed: HTTP $code (range=$resumeFrom-$endByte, yt=$isYT)")
                    if (code == 403) {
                        mission.error = context.getString(R.string.download_url_expired)
                        mission.gatedHttp403 = true
                        mission.cancelActiveCalls()
                    }
                    return false
                }

                val inputStream = response.body?.byteStream() ?: return false
                val buffer = ByteArray(BUFFER_SIZE)

                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(resumeFrom)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (mission.status != MissionStatus.RUNNING) {
                            partialBytesMap[blockIndex] = alreadyWritten + bytesWrittenInSession
                            return false
                        }
                        raf.write(buffer, 0, bytesRead)
                        bytesWrittenInSession += bytesRead
                        mission.updateProgress(bytesRead.toLong(), isAudio)
                    }
                }

                partialBytesMap.remove(blockIndex)
                return true
            } catch (e: Exception) {
                partialBytesMap[blockIndex] = alreadyWritten + bytesWrittenInSession
                throw e
            } finally {
                mission.activeCalls.remove(call)
                response.close()
            }
        }

        private fun prepareFile(
            file: File,
            size: Long,
        ) {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() != size) raf.setLength(size)
            }
        }

        private fun getContentLength(
            client: OkHttpClient,
            url: String,
            userAgent: String,
        ): Long {
            val useQueryRange = YouTubeStreamUrls.isYouTubeStreamUrl(url)
            val effectiveUserAgent = YouTubeStreamUrls.resolveUserAgent(url, userAgent)
            return try {
                if (!useQueryRange) {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .head()
                            .header("User-Agent", effectiveUserAgent)
                            .build()
                    val response = client.newCall(request).execute()
                    val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    response.close()
                    if (length > 0) return length
                }
                // Fallback: Range bytes=0-0 → read Content-Range header
                val rangeRequest =
                    if (useQueryRange) {
                        // YouTube: use query-range
                        Request
                            .Builder()
                            .url(YouTubeStreamUrls.buildYouTubeBlockUrl(url, 0L, 0L))
                            .header("User-Agent", effectiveUserAgent)
                            .header("Origin", "https://www.youtube.com")
                            .header("Referer", "https://www.youtube.com/")
                            .build()
                    } else {
                        Request
                            .Builder()
                            .url(url)
                            .header("Range", "bytes=0-0")
                            .header("User-Agent", effectiveUserAgent)
                            .build()
                    }
                val rangeResponse = client.newCall(rangeRequest).execute()
                val total =
                    rangeResponse
                        .header("Content-Range")
                        ?.substringAfter("/")
                        ?.toLongOrNull() ?: -1L
                val bodyLen = rangeResponse.header("Content-Length")?.toLongOrNull() ?: -1L
                rangeResponse.close()
                if (total > 0) {
                    total
                } else if (bodyLen > 1) {
                    bodyLen
                } else {
                    -1L
                }
            } catch (e: Exception) {
                Log.e(TAG, "Content length check failed: url=$url, error=${e.message}")
                -1L
            }
        }
    }
