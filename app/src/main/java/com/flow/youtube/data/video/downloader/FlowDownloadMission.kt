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
    val url: String,
    val quality: String,
    val savePath: String,
    val fileName: String,
    
    // Progress tracking
    var totalBytes: Long = 0,
    var downloadedBytes: Long = 0,
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
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
        
    fun updateProgress(bytesRead: Long) {
        downloadedBytes += bytesRead
    }
    
    fun isRunning(): Boolean = status == MissionStatus.RUNNING
    fun isFinished(): Boolean = status == MissionStatus.FINISHED
    fun isFailed(): Boolean = status == MissionStatus.FAILED
}
