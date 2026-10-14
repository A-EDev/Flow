package io.github.aedev.flow.player.musicvideo

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import io.github.aedev.flow.utils.MusicPlayerUtils

/**
 * The music player's sources: the song's audio as always, joined by its picture for an item
 * marked by [MusicVideoItems]. The picture comes from the playback the song already resolved,
 * which the Song/Video switch fetches before it marks an item, so no request is made here. An
 * item whose playback is not at hand plays as audio.
 */
@UnstableApi
internal class MusicMediaSourceFactory(
    private val audio: MediaSource.Factory,
    private val videoDataSource: DataSource.Factory,
    private val maxVideoHeight: () -> Int,
) : MediaSource.Factory by audio {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audioSource = audio.createMediaSource(mediaItem)
        if (!MusicVideoItems.showsVideo(mediaItem)) return audioSource
        val video = videoSource(mediaItem.mediaId) ?: return audioSource
        return MergingMediaSource(true, true, audioSource, video)
    }

    private fun videoSource(mediaId: String): MediaSource? {
        val playback = MusicPlayerUtils.cachedPlayback(mediaId)
        val format = playback?.let { MusicVideoFormats.pick(it.videoFormats, maxVideoHeight()) }
        if (format == null) {
            Log.d(TAG, "No picture at hand for $mediaId, playing audio")
            return null
        }
        val durationMs =
            format.approxDurationMs?.toLongOrNull()
                ?: playback.videoDetails
                    ?.lengthSeconds
                    ?.toLongOrNull()
                    ?.times(MS_PER_SECOND)
                ?: 0L
        return MusicVideoManifest.source(format, durationMs, mediaId, videoDataSource)
    }

    private companion object {
        const val TAG = "MusicMediaSource"
        const val MS_PER_SECOND = 1000L
    }
}
