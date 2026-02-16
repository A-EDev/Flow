package com.flow.youtube.data.video.downloader

import java.io.Serializable

/**
 * Represents a single chunk/part of a parallel download.
 * Used in-memory only by ParallelDownloader.
 */
data class FlowDownloadPart(
    val partName: String, // missionUrl + index
    val missionId: String,
    val startIndex: Long,
    val endIndex: Long,
    var currentOffset: Long, // How many bytes downloaded relative to start
    var isFinished: Boolean = false,
    var isAudio: Boolean = false
) : Serializable {
    val totalBytes: Long
        get() = endIndex - startIndex + 1
        
    val bytesWritten: Long
        get() = currentOffset
        
    val remainingBytes: Long
        get() = totalBytes - bytesWritten
}
