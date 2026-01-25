package com.flow.youtube.data.video.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

@Singleton
class ParallelDownloader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // 64KB buffer
    private val bufferSize = 64 * 1024 

    suspend fun start(mission: FlowDownloadMission, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mission.status = MissionStatus.RUNNING
                
                // 1. Get Content Lengths
                if (mission.totalBytes == 0L) {
                    val length = getContentLength(mission.url)
                    if (length <= 0) {
                        Log.e("ParallelDownloader", "Failed to get video content length")
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get video content length"
                        return@withContext false
                    }
                    mission.totalBytes = length
                }

                if (mission.audioUrl != null && mission.audioTotalBytes == 0L) {
                    val audioLength = getContentLength(mission.audioUrl)
                    if (audioLength <= 0) {
                        Log.e("ParallelDownloader", "Failed to get audio content length")
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get audio content length"
                        return@withContext false
                    }
                    mission.audioTotalBytes = audioLength
                }

                // Initialize parts if empty
                if (mission.parts.isEmpty()) {
                    createParts(mission)
                }

                // 2. Prepare files
                val isDash = mission.audioUrl != null
                val videoFile = if (isDash) File("${mission.savePath}.video.tmp") else File(mission.savePath)
                val audioFile = if (isDash) File("${mission.savePath}.audio.tmp") else null

                if (!videoFile.exists()) videoFile.createNewFile()
                RandomAccessFile(videoFile, "rw").use { raf ->
                    if (raf.length() != mission.totalBytes) raf.setLength(mission.totalBytes)
                }

                audioFile?.let { af ->
                    if (!af.exists()) af.createNewFile()
                    RandomAccessFile(af, "rw").use { raf ->
                        if (raf.length() != mission.audioTotalBytes) raf.setLength(mission.audioTotalBytes)
                    }
                }

                // 3. Parallel Download
                val progressMutex = Mutex()
                
                coroutineScope {
                    val deferreds = mission.parts.filter { !it.isFinished }.map { part ->
                        async(Dispatchers.IO) {
                            val targetFile = if (part.isAudio) audioFile!! else videoFile
                            downloadPart(
                                mission = mission,
                                part = part, 
                                file = targetFile,
                                progressMutex = progressMutex,
                                onProgress = onProgress
                            )
                        }
                    }
                    
                    // Wait for all parts
                    val results = deferreds.awaitAll()
                    
                    if (results.all { it }) {
                        if (!isDash) {
                            mission.status = MissionStatus.FINISHED
                            mission.finishTime = System.currentTimeMillis()
                        }
                        true
                    } else {
                        mission.status = MissionStatus.FAILED
                        mission.error = "One or more parts failed to download"
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("ParallelDownloader", "Critical download error", e)
                mission.status = MissionStatus.FAILED
                mission.error = e.message
                false
            }
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

    private suspend fun downloadPart(
        mission: FlowDownloadMission,
        part: FlowDownloadPart, 
        file: File,
        progressMutex: Mutex,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ParallelDownloader", "Part failed: HTTP ${response.code} for URL: $url")
                    return false
                }

                val inputStream = response.body?.byteStream() ?: return false
                
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(startByte)
                    
                    val buffer = ByteArray(bufferSize)
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
            }
            
            part.isFinished = true
            true
        } catch (e: Exception) {
            Log.e("ParallelDownloader", "Part failed exception", e)
            false
        }
    }

    private fun getContentLength(url: String): Long {
        try {
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
            response.close()
            return length
        } catch (e: Exception) {
            Log.e("ParallelDownloader", "Content Length Check Failed", e)
            return -1L
        }
    }
}
