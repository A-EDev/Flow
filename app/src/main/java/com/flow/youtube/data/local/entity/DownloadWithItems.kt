package com.flow.youtube.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation wrapper that combines a Download with all its DownloadItems.
 * Used for querying a download along with its associated files.
 */
data class DownloadWithItems(
    @Embedded val download: DownloadEntity,
    @Relation(
        parentColumn = "videoId",
        entityColumn = "videoId"
    )
    val items: List<DownloadItemEntity>
) {
    /** Whether this download has a VIDEO file */
    val hasVideo: Boolean get() = items.any { it.fileType == DownloadFileType.VIDEO }

    /** Whether this download has an AUDIO file (audio-only, no video) */
    val isAudioOnly: Boolean get() = items.any { it.fileType == DownloadFileType.AUDIO } && !hasVideo

    /** Overall status — worst status wins */
    val overallStatus: DownloadItemStatus
        get() = when {
            items.any { it.status == DownloadItemStatus.DOWNLOADING } -> DownloadItemStatus.DOWNLOADING
            items.any { it.status == DownloadItemStatus.FAILED } -> DownloadItemStatus.FAILED
            items.any { it.status == DownloadItemStatus.PAUSED } -> DownloadItemStatus.PAUSED
            items.any { it.status == DownloadItemStatus.PENDING } -> DownloadItemStatus.PENDING
            items.all { it.status == DownloadItemStatus.COMPLETED } -> DownloadItemStatus.COMPLETED
            items.any { it.status == DownloadItemStatus.CANCELLED } -> DownloadItemStatus.CANCELLED
            else -> DownloadItemStatus.PENDING
        }

    /** Total file size across all items */
    val totalSize: Long get() = items.sumOf { it.totalBytes }

    /** Total downloaded bytes across all items */
    val downloadedSize: Long get() = items.sumOf { it.downloadedBytes }

    /** Overall download progress (0.0 to 1.0) */
    val progress: Float
        get() {
            val total = totalSize
            return if (total > 0) downloadedSize.toFloat() / total.toFloat() else 0f
        }

    /** The primary file path (VIDEO file if present, otherwise first AUDIO file) */
    val primaryFilePath: String?
        get() = items.firstOrNull { it.fileType == DownloadFileType.VIDEO }?.filePath
            ?: items.firstOrNull { it.fileType == DownloadFileType.AUDIO }?.filePath
}
