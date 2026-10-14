package io.github.aedev.flow.player.musicvideo

import android.net.Uri
import androidx.media3.common.MediaItem

/**
 * Marks a queue item that should play with its picture. The mark is part of the item's uri, so
 * swapping it changes the item's configuration and the player rebuilds its source instead of
 * updating the old one in place.
 */
object MusicVideoItems {
    private const val SCHEME = "music"
    private const val VIDEO_PARAM = "video"
    private const val VIDEO_ON = "1"

    fun streamUri(
        videoId: String,
        showsVideo: Boolean,
    ): Uri = Uri.parse(if (showsVideo) "$SCHEME://$videoId?$VIDEO_PARAM=$VIDEO_ON" else "$SCHEME://$videoId")

    fun showsVideo(item: MediaItem): Boolean = item.localConfiguration?.uri?.getQueryParameter(VIDEO_PARAM) == VIDEO_ON
}
