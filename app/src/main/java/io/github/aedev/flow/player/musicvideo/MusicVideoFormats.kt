package io.github.aedev.flow.player.musicvideo

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.VideoCodecUtils

/**
 * The picture stream for a song shown in the music player. The video fills only the artwork box,
 * so it stays at or under [maxHeight] and prefers the codec phones decode in hardware.
 */
internal object MusicVideoFormats {
    const val WIFI_MAX_HEIGHT = 720
    const val CELLULAR_MAX_HEIGHT = 480

    fun maxHeight(onWifi: Boolean): Int = if (onWifi) WIFI_MAX_HEIGHT else CELLULAR_MAX_HEIGHT

    fun pick(
        formats: List<PlayerResponse.StreamingData.Format>,
        maxHeight: Int,
    ): PlayerResponse.StreamingData.Format? {
        val playable = formats.filter(::isSingleFileVideo)
        val fitting = playable.filter { (it.height ?: 0) <= maxHeight }
        val byQuality =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
                .thenBy { VideoCodecUtils.playbackCodecRank(VideoCodecUtils.codecKeyFromMimeType(it.mimeType)) }
        return fitting.minWithOrNull(byQuality) ?: playable.minByOrNull { it.height ?: Int.MAX_VALUE }
    }

    // The player reads these as one DASH segment, which needs the file's init and index ranges.
    private fun isSingleFileVideo(format: PlayerResponse.StreamingData.Format): Boolean =
        !format.isAudio &&
            !format.url.isNullOrEmpty() &&
            format.height != null &&
            format.initRange?.end != null &&
            format.indexRange?.end != null
}
