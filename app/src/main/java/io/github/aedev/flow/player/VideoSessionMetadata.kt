package io.github.aedev.flow.player

import android.net.Uri
import androidx.media3.common.MediaMetadata
import io.github.aedev.flow.data.model.Video

internal data class VideoSessionMetadata(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
)

internal fun Video.toVideoSessionMetadata(): VideoSessionMetadata =
    VideoSessionMetadata(
        mediaId = id,
        title = title,
        artist = channelName,
        artworkUrl = thumbnailUrl,
    )

internal fun VideoSessionMetadata.toMedia3Metadata(): MediaMetadata =
    MediaMetadata
        .Builder()
        .setTitle(title)
        .setDisplayTitle(title)
        .setArtist(artist)
        .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        .also { builder ->
            artworkUrl.takeIf(String::isNotBlank)?.let { builder.setArtworkUri(Uri.parse(it)) }
        }.build()
