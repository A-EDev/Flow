package io.github.aedev.flow.data.localmedia

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack

/** A video or song file on the device, as MediaStore describes it. */
data class LocalMediaItem(
    val id: Long,
    val isVideo: Boolean,
    val contentUri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val folderId: String = "",
    val folderName: String = "",
    /** The folder path relative to the volume ("DCIM/Camera/"), for matching hidden folders. */
    val path: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
) {
    val mediaId: String get() = LocalMediaIds.of(id)

    val isPortrait: Boolean get() = height > width && width > 0
}

/** Everything the device holds, or [failed] when MediaStore could not be read. */
data class LocalLibrary(
    val videos: List<LocalMediaItem> = emptyList(),
    val music: List<LocalMediaItem> = emptyList(),
    val failed: Boolean = false,
)

/** The file as the video player's item: its folder stands in for a channel. */
fun LocalMediaItem.toVideo(): Video =
    Video(
        id = mediaId,
        title = title,
        channelName = folderName,
        channelId = "",
        thumbnailUrl = contentUri,
        duration = (durationMs / MILLIS_PER_SECOND).toInt(),
        viewCount = 0,
        uploadDate = "",
    )

/** The file as the music player's track; a song without artist tags is credited to its folder. */
fun LocalMediaItem.toMusicTrack(): MusicTrack =
    MusicTrack(
        videoId = mediaId,
        title = title,
        artist = artist.ifBlank { folderName },
        thumbnailUrl = artworkUri.orEmpty(),
        duration = (durationMs / MILLIS_PER_SECOND).toInt(),
        album = album,
    )

private const val MILLIS_PER_SECOND = 1_000L
