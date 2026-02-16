package com.flow.youtube.data.video.downloader

import com.flow.youtube.data.model.Video
import java.util.UUID

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
    
    // Progress tracking
    var totalBytes: Long = 0,
    var audioTotalBytes: Long = 0,
    var downloadedBytes: Long = 0,
    var audioDownloadedBytes: Long = 0,
    var status: MissionStatus = MissionStatus.PENDING,
    var threads: Int = 3,
    
    // Parts management
    val parts: MutableList<FlowDownloadPart> = mutableListOf(),
    
    // Timestamps
    val createdTime: Long = System.currentTimeMillis(),
    var finishTime: Long = 0,
    
    // Error tracking
    var error: String? = null
) {
    val progress: Float
        get() {
            val total = totalBytes + audioTotalBytes
            val current = downloadedBytes + audioDownloadedBytes
            return if (total > 0) current.toFloat() / total.toFloat() else 0f
        }
        
    fun updateProgress(bytesRead: Long, isAudio: Boolean = false) {
        if (isAudio) {
            audioDownloadedBytes += bytesRead
        } else {
            downloadedBytes += bytesRead
        }
    }
    
    fun isRunning(): Boolean = status == MissionStatus.RUNNING
    fun isFinished(): Boolean = status == MissionStatus.FINISHED
    fun isFailed(): Boolean = status == MissionStatus.FAILED
}
