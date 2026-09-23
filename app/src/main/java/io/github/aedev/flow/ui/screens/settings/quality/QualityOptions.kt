package io.github.aedev.flow.ui.screens.settings.quality

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.local.VideoQuality

/** Resolutions offered for regular video, best first. */
internal val VideoQualities =
    listOf(
        VideoQuality.AUTO,
        VideoQuality.Q_2160P,
        VideoQuality.Q_1440P,
        VideoQuality.Q_1080P,
        VideoQuality.Q_720P,
        VideoQuality.Q_480P,
        VideoQuality.Q_360P,
        VideoQuality.Q_240P,
        VideoQuality.Q_144P,
    )

/** Shorts are served no higher than 1080p, so the picker stops there. */
internal val ShortsQualities = VideoQualities.filter { it == VideoQuality.AUTO || it.height <= VideoQuality.Q_1080P.height }

internal val MusicQualities = MusicAudioQuality.entries.toList()

@StringRes
internal fun videoQualityLabel(quality: VideoQuality): Int =
    when (quality) {
        VideoQuality.AUTO -> R.string.quality_auto
        VideoQuality.Q_144P -> R.string.quality_144p
        VideoQuality.Q_240P -> R.string.quality_240p
        VideoQuality.Q_360P -> R.string.quality_360p
        VideoQuality.Q_480P -> R.string.quality_480p
        VideoQuality.Q_720P -> R.string.quality_720p_hd
        VideoQuality.Q_1080P -> R.string.quality_1080p_full_hd
        VideoQuality.Q_1440P -> R.string.quality_1440p_qhd
        VideoQuality.Q_2160P -> R.string.quality_2160p_4k
    }

@StringRes
internal fun musicQualityLabel(quality: MusicAudioQuality): Int =
    when (quality) {
        MusicAudioQuality.AUTO -> R.string.music_quality_auto
        MusicAudioQuality.HIGH -> R.string.music_quality_high
        MusicAudioQuality.MEDIUM -> R.string.music_quality_medium
        MusicAudioQuality.LOW -> R.string.music_quality_low
    }

/** The fallback choices for [preferred]: every codec but the preferred one. */
internal fun fallbackCodecs(preferred: VideoCodec): List<VideoCodec> = VideoCodec.entries.filter { it != preferred }

/** A fallback equal to the new preferred codec is meaningless, so it drops back to automatic. */
internal fun fallbackAfterPreferredChange(
    preferred: VideoCodec,
    fallback: VideoCodec,
): VideoCodec = if (fallback == preferred) VideoCodec.AUTO else fallback

/** Codec names are product names and stay as they are; only "automatic" is translated. */
@Composable
internal fun codecLabel(codec: VideoCodec): String =
    if (codec == VideoCodec.AUTO) stringResource(R.string.player_settings_video_codec_fallback_auto) else codec.label
