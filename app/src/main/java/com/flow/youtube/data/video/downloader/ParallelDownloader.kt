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
                
                // 1. Get Content Length if new
                if (mission.totalBytes == 0L) {
                    val length = getContentLength(mission.url)
                    if (length <= 0) {
                        Log.e("ParallelDownloader", "Failed to get content length or content is empty")
                        mission.status = MissionStatus.FAILED
                        mission.error = "Failed to get content length"
                        return@withContext false
                    }
                    mission.totalBytes = length
                    
                    // Initialize parts
                    createParts(mission, length)
                }

                // 2. Prepare file
                val file = File(mission.savePath)
                if (!file.exists()) {
                    file.createNewFile()
                }
                
                // Pre-allocate space
                RandomAccessFile(file, "rw").use { raf ->
                    if (raf.length() != mission.totalBytes) {
                        raf.setLength(mission.totalBytes)
                    }
                }

                // 3. Parallel Download
                val progressMutex = Mutex()
                
                coroutineScope {
                    val deferreds = mission.parts.filter { !it.isFinished }.map { part ->
                        async(Dispatchers.IO) {
                            downloadPart(
                                mission = mission, // Pass mission context
                                part = part, 
                                file = file,
                                progressMutex = progressMutex,
                                onProgress = onProgress
                            )
                        }
                    }
                    
                    // Wait for all parts
                    val results = deferreds.awaitAll()
                    
                    if (results.all { it }) {
                        mission.status = MissionStatus.FINISHED
                        mission.finishTime = System.currentTimeMillis()
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

    private fun createParts(mission: FlowDownloadMission, totalLength: Long) {
        val partSize = ceil(totalLength.toDouble() / mission.threads).toLong()
        
        for (i in 0 until mission.threads) {
            val start = i * partSize
            var end = start + partSize - 1
            if (i == mission.threads - 1) {
                end = totalLength - 1
            }
            
            val part = FlowDownloadPart(
                partName = "${mission.url}_$i",
                missionId = mission.id,
                startIndex = start,
                endIndex = end,
                currentOffset = 0
            )
            mission.parts.add(part)
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

            val request = Request.Builder()
                .url(mission.url)
                .header("Range", "bytes=$startByte-$endByte")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("ParallelDownloader", "Part failed: HTTP ${response.code}")
                return false
            }

            val inputStream = response.body?.byteStream() ?: return false
            
            // Critical: Use a dedicated RandomAccessFile per thread/part instance
            // But we need to be careful about file locking. 
            // Better approach: Open RAF here for this specific part writing.
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startByte)
                
                val buffer = ByteArray(bufferSize)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (mission.status != MissionStatus.RUNNING) {
                         // Download paused or cancelled
                         return false
                    }
                    
                    raf.write(buffer, 0, bytesRead)
                    part.currentOffset += bytesRead
                    
                    progressMutex.withLock {
                        mission.updateProgress(bytesRead.toLong())
                        onProgress(mission.progress)
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
