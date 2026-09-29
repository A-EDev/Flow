package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.video.downloader.request.StoredArtist
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.github.aedev.flow.data.video.downloader.tags.displayArtist

/** A media file met on disk, as the recovery scan measured it. */
internal data class FoundFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val createdAt: Long,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isVideo: Boolean get() = extension in RecoveredDownload.VIDEO_EXTENSIONS
}

/**
 * The rows a file found on disk is recorded with. A file Flow tagged comes back as the download it
 * was, under its real video id with its channel, album and counts; any other file gets an id made
 * from its path and whatever title and artist it carries. Pure, so it is unit tested.
 */
internal object RecoveredDownload {
    val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "avi", "mov")
    val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg")
    const val LOCAL_FILE_ARTIST = "Local File"

    fun isMedia(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase().let {
            it in VIDEO_EXTENSIONS ||
                it in AUDIO_EXTENSIONS
        }

    fun idFor(
        path: String,
        tags: DownloadTags?,
    ): String = tags?.videoId?.takeIf { it.isNotBlank() } ?: "recovered_${path.hashCode().toLong() and 0xFFFFFFFFL}"

    fun mimeTypeOf(extension: String): String =
        when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "opus", "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }

    fun rows(
        file: FoundFile,
        tags: DownloadTags?,
        title: String?,
        artist: String?,
        coverPath: String?,
    ): Pair<DownloadEntity, DownloadItemEntity> {
        val videoId = idFor(file.path, tags)
        val kind = tags?.kind ?: if (file.isVideo) DownloadKind.VIDEO else DownloadKind.MUSIC
        val download =
            DownloadEntity(
                videoId = videoId,
                title = tags?.title?.takeIf { it.isNotBlank() } ?: title ?: file.name.substringBeforeLast('.'),
                uploader = tags?.displayArtist() ?: artist ?: LOCAL_FILE_ARTIST,
                duration = file.durationMs / 1000,
                thumbnailUrl = tags?.thumbnailUrl.orEmpty(),
                thumbnailPath = coverPath,
                createdAt = file.createdAt,
                kind = kind,
                channelId = tags?.channelId.orEmpty(),
                description = tags?.description.orEmpty(),
                releaseDate = tags?.releaseDate,
                viewCount = tags?.viewCount ?: 0L,
                likeCount = tags?.likeCount ?: 0L,
                album = tags?.album,
                albumId = tags?.albumId,
                artistsJson =
                    tags?.artists?.takeIf { it.isNotEmpty() }?.let { names ->
                        StoredArtist.encode(names.map { StoredArtist(it, null) })
                    },
                trackNumber = tags?.trackNumber,
            )
        val item =
            DownloadItemEntity(
                videoId = videoId,
                fileType = if (file.isVideo) DownloadFileType.VIDEO else DownloadFileType.AUDIO,
                fileName = file.name,
                filePath = file.path,
                format = file.extension,
                quality = LOCAL_QUALITY,
                mimeType = mimeTypeOf(file.extension),
                downloadedBytes = file.sizeBytes,
                totalBytes = file.sizeBytes,
                status = DownloadItemStatus.COMPLETED,
            )
        return download to item
    }

    private const val LOCAL_QUALITY = "Local"
}
