package io.github.aedev.flow.player.musicvideo

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import androidx.media3.exoplayer.source.MediaSource
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.VideoCodecUtils

/**
 * A one-file video stream described to Media3 as a single-segment DASH representation, so it is
 * read by ranged requests through the file's own index rather than as one throttled download.
 */
@UnstableApi
internal object MusicVideoManifest {
    fun source(
        format: PlayerResponse.StreamingData.Format,
        durationMs: Long,
        mediaId: String,
        dataSourceFactory: DataSource.Factory,
    ): MediaSource? {
        val manifest = manifest(format, durationMs) ?: return null
        val item =
            MediaItem
                .Builder()
                .setMediaId(mediaId)
                .setUri(format.url)
                .build()
        return DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, item)
    }

    fun manifest(
        format: PlayerResponse.StreamingData.Format,
        durationMs: Long,
    ): DashManifest? {
        val url = format.url ?: return null
        val init = format.initRange?.span() ?: return null
        val index = format.indexRange?.span() ?: return null
        if (durationMs <= 0) return null
        val representation =
            Representation.newInstance(
                0,
                media3Format(format),
                listOf(BaseUrl(url)),
                SegmentBase.SingleSegmentBase(RangedUri(null, init.first, init.second), 1, 0, index.first, index.second),
            )
        val adaptationSet = AdaptationSet(0, C.TRACK_TYPE_VIDEO, listOf(representation), emptyList(), emptyList(), emptyList())
        return DashManifest(
            C.TIME_UNSET,
            durationMs,
            C.TIME_UNSET,
            false,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            null,
            null,
            null,
            null,
            listOf(Period("0", 0, listOf(adaptationSet))),
        )
    }

    private fun media3Format(format: PlayerResponse.StreamingData.Format): Format {
        val codecs = VideoCodecUtils.codecStringFromMimeType(format.mimeType)
        return Format
            .Builder()
            .setId(format.itag.toString())
            .setContainerMimeType(format.mimeType.substringBefore(';').trim())
            .setSampleMimeType(MimeTypes.getVideoMediaMimeType(codecs))
            .setCodecs(codecs)
            .setWidth(format.width ?: Format.NO_VALUE)
            .setHeight(format.height ?: Format.NO_VALUE)
            .setFrameRate(format.fps?.toFloat() ?: Format.NO_VALUE.toFloat())
            .setAverageBitrate(format.averageBitrate ?: Format.NO_VALUE)
            .setPeakBitrate(format.bitrate)
            .build()
    }

    /** Start and length of an inclusive byte range. */
    private fun PlayerResponse.StreamingData.Format.Range.span(): Pair<Long, Long>? {
        val start = start?.toLongOrNull() ?: return null
        val end = end?.toLongOrNull() ?: return null
        return if (end >= start) start to end - start + 1 else null
    }
}
