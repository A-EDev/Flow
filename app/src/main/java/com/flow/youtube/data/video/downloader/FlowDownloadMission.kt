package com.flow.youtube.data.video.downloader

import com.flow.youtube.data.model.Video
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class MissionStatus {
    PENDING,
    RUNNING,
    PAUSED,
    FINISHED,
    FAILED
}

data class FlowDownloadMission(
    val id: String = UUID.randomUUID().toString(),
    val video: Video,
    val url: String, // Video URL
    val audioUrl: String? = null, // Optional Audio URL for DASH
    val quality: String,
    val savePath: String,
    val fileName: String,
    
    // User-Agent (important for YouTube streams)
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
    
    // Progress tracking — AtomicLong for lock-free thread-safe updates
    var totalBytes: Long = 0,
    var audioTotalBytes: Long = 0,
    var status: MissionStatus = MissionStatus.PENDING,
    var threads: Int = 3,
    
    // Timestamps
    val createdTime: Long = System.currentTimeMillis(),
    var finishTime: Long = 0,
    
    // Error tracking
    var error: String? = null
) {
    // Atomic counters — updated from multiple download threads without locks
    @Transient val downloadedBytesAtomic = AtomicLong(0L)
    @Transient val audioDownloadedBytesAtomic = AtomicLong(0L)
    
    @Transient val videoBlockCounter = AtomicInteger(0)
    @Transient val audioBlockCounter = AtomicInteger(0)
    
    /** Convenience accessors for current downloaded bytes */
    val downloadedBytes: Long get() = downloadedBytesAtomic.get()
    val audioDownloadedBytes: Long get() = audioDownloadedBytesAtomic.get()
    
    val progress: Float
        get() {
            val total = totalBytes + audioTotalBytes
            val current = downloadedBytesAtomic.get() + audioDownloadedBytesAtomic.get()
            return if (total > 0) current.toFloat() / total.toFloat() else 0f
        }
        
    /** Lock-free progress update — safe to call from any thread */
    fun updateProgress(bytesRead: Long, isAudio: Boolean = false) {
        if (isAudio) {
            audioDownloadedBytesAtomic.addAndGet(bytesRead)
        } else {
            downloadedBytesAtomic.addAndGet(bytesRead)
        }
    }
    
    fun isRunning(): Boolean = status == MissionStatus.RUNNING
    fun isFinished(): Boolean = status == MissionStatus.FINISHED
    fun isFailed(): Boolean = status == MissionStatus.FAILED
}
