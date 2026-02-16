package com.flow.youtube.data.video.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.min

@Singleton
class ParallelDownloader @Inject constructor() {

    companion object {
        private const val TAG = "ParallelDownloader"
        private const val BUFFER_SIZE = 128 * 1024  // 128KB buffer (up from 64KB)
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Start downloading a mission, splitting into parallel parts.
     * Supports resuming partially downloaded parts.
     * 
     * @param mission The download mission with url, audioUrl, etc.
     * @param onProgress Called with overall progress float (0..1)
     * @return true if all parts completed, false if failed or paused
     */
    suspend fun start(mission: FlowDownloadMission, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "start: Beginning download for URL=${mission.url}, AudioURL=${mission.audioUrl}")
                mission.status = MissionStatus.RUNNING

                // 1. Get Content Lengths
                if (mission.totalBytes == 0L) {
                    val length = getContentLength(mission.url, mission.userAgent)
                    Log.d(TAG, "start: Video content length=$length")
                    if (length <= 0) {
                        Log.e(TAG, "Failed to get video content length")
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get video content length"
                        return@withContext false
                    }
                    mission.totalBytes = length
                }

                if (mission.audioUrl != null && mission.audioTotalBytes == 0L) {
                    val audioLength = getContentLength(mission.audioUrl, mission.userAgent)
                    Log.d(TAG, "start: Audio content length=$audioLength")
                    if (audioLength <= 0) {
                        Log.e(TAG, "Failed to get audio content length")
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get audio content length"
                        return@withContext false
                    }
                    mission.audioTotalBytes = audioLength
                }

                if (mission.parts.isEmpty()) {
                    Log.d(TAG, "start: Creating parts...")
                    createParts(mission)
                    Log.d(TAG, "start: Created ${mission.parts.size} parts")
                }

                // 2. Prepare files
                val isDash = mission.audioUrl != null
                val videoFile = if (isDash) File("${mission.savePath}.video.tmp") else File(mission.savePath)
                val audioFile = if (isDash) File("${mission.savePath}.audio.tmp") else null
                
                Log.d(TAG, "start: Video file path=${videoFile.absolutePath}, exists=${videoFile.exists()}")
                if (audioFile != null) Log.d(TAG, "start: Audio file path=${audioFile.absolutePath}, exists=${audioFile.exists()}")

                prepareFile(videoFile, mission.totalBytes)
                audioFile?.let { prepareFile(it, mission.audioTotalBytes) }

                val progressMutex = Mutex()
                Log.d(TAG, "start: Launching parallel parts...")

                coroutineScope {
                    val deferreds = mission.parts.filter { !it.isFinished }.map { part ->
                         async(Dispatchers.IO) {
                            val targetFile = if (part.isAudio) audioFile!! else videoFile
                            downloadPartWithRetry(
                                mission = mission,
                                part = part,
                                file = targetFile,
                                progressMutex = progressMutex,
                                onProgress = onProgress
                            )
                        }
                    }

                    val results = deferreds.awaitAll()
                    val allSuccess = results.all { it }
                    Log.d(TAG, "start: Parts finished. All success=$allSuccess")

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
                            Log.e(TAG, "start: One or more parts failed")
                            mission.status = MissionStatus.FAILED
                            mission.error = mission.error ?: "One or more parts failed"
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

    private fun prepareFile(file: File, size: Long) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() != size) raf.setLength(size)
        }
    }

    private fun createParts(mission: FlowDownloadMission) {
        // Video parts
        val videoPartSize = ceil(mission.totalBytes.toDouble() / mission.threads).toLong()
        for (i in 0 until mission.threads) {
            val start = i * videoPartSize
            var end = start + videoPartSize - 1
            if (i == mission.threads - 1) end = mission.totalBytes - 1

            mission.parts.add(FlowDownloadPart(
                partName = "${mission.id}_v_$i",
                missionId = mission.id,
                startIndex = start,
                endIndex = end,
                currentOffset = 0,
                isAudio = false
            ))
        }

        // Audio parts (if DASH)
        mission.audioUrl?.let {
            val audioPartSize = ceil(mission.audioTotalBytes.toDouble() / mission.threads).toLong()
            for (i in 0 until mission.threads) {
                val start = i * audioPartSize
                var end = start + audioPartSize - 1
                if (i == mission.threads - 1) end = mission.audioTotalBytes - 1

                mission.parts.add(FlowDownloadPart(
                    partName = "${mission.id}_a_$i",
                    missionId = mission.id,
                    startIndex = start,
                    endIndex = end,
                    currentOffset = 0,
                    isAudio = true
                ))
            }
        }
    }

    /**
     * Download a part with exponential backoff retry (up to MAX_RETRIES).
     */
    private suspend fun downloadPartWithRetry(
        mission: FlowDownloadMission,
        part: FlowDownloadPart,
        file: File,
        progressMutex: Mutex,
        onProgress: (Float) -> Unit
    ): Boolean {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_RETRIES) {
            if (mission.status != MissionStatus.RUNNING) return false

            try {
                val result = downloadPart(mission, part, file, progressMutex, onProgress)
                if (result) return true

                if (mission.status == MissionStatus.PAUSED) return false
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Part ${part.partName} attempt ${attempt + 1} failed: ${e.message}")
            }

            attempt++
            if (attempt < MAX_RETRIES && mission.status == MissionStatus.RUNNING) {
                val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1)) 
                Log.d(TAG, "Retrying part ${part.partName} in ${delayMs}ms (attempt ${attempt + 1}/$MAX_RETRIES)")
                delay(delayMs)
            }
        }

        Log.e(TAG, "Part ${part.partName} failed after $MAX_RETRIES retries", lastError)
        mission.error = "Part ${part.partName} failed: ${lastError?.message}"
        return false
    }

    private suspend fun downloadPart(
        mission: FlowDownloadMission,
        part: FlowDownloadPart,
        file: File,
        progressMutex: Mutex,
        onProgress: (Float) -> Unit
    ): Boolean {
        val startByte = part.startIndex + part.currentOffset
        val endByte = part.endIndex

        if (startByte > endByte) {
            part.isFinished = true
            return true
        }

        val url = if (part.isAudio) mission.audioUrl!! else mission.url
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$startByte-$endByte")
            .header("User-Agent", mission.userAgent)
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val code = response.code
                Log.e(TAG, "Part ${part.partName} failed: HTTP $code")
                
                if (code == 403) {
                    mission.error = "URL expired (403). Re-fetch needed."
                }
                return false
            }

            val inputStream = response.body?.byteStream() ?: return false

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startByte)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (mission.status != MissionStatus.RUNNING) {
                        return false
                    }

                    raf.write(buffer, 0, bytesRead)
                    part.currentOffset += bytesRead

                    progressMutex.withLock {
                        mission.updateProgress(bytesRead.toLong(), part.isAudio)
                        onProgress(mission.progress)
                    }
                }
            }

            part.isFinished = true
            return true
        } finally {
            response.close()
        }
    }

    private fun getContentLength(url: String, userAgent: String): Long {
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
