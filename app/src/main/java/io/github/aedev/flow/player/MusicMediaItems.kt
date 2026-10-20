package io.github.aedev.flow.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.musicvideo.MusicVideoItems

/**
 * The media item every music queue is built from, by the app and by playback resumption alike. A
 * streamed song is cached under its video id, so its audio is found again whichever URI asked.
 */
internal fun musicMediaItem(
    track: MusicTrack,
    uri: Uri,
    useCacheKey: Boolean = !LocalMediaIds.isLocal(track.videoId),
): MediaItem {
    val builder =
        MediaItem
            .Builder()
            .setUri(uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.highResThumbnailUrl))
                    .setDurationMs(track.duration.takeIf { it > 0 }?.let { it * MILLIS_PER_SECOND })
                    .build(),
            )
    if (useCacheKey) builder.setCustomCacheKey(track.videoId)
    return builder.build()
}

private const val MILLIS_PER_SECOND = 1000L

/** A song as the audio it streams, or its MediaStore file when it is on the device. */
internal fun musicAudioUri(videoId: String): Uri = LocalMediaIds.audioUri(videoId) ?: MusicVideoItems.streamUri(videoId, showsVideo = false)
