package com.flow.youtube.data.video.downloader

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "download_parts")
data class FlowDownloadPart(
    @PrimaryKey
    val partName: String, // missionUrl + index
    val missionId: String,
    val startIndex: Long,
    val endIndex: Long,
    var currentOffset: Long, // How many bytes downloaded relative to start
    var isFinished: Boolean = false
) : Serializable {
    val totalBytes: Long
        get() = endIndex - startIndex + 1
        
    val bytesWritten: Long
        get() = currentOffset
        
    val remainingBytes: Long
        get() = totalBytes - bytesWritten
}
