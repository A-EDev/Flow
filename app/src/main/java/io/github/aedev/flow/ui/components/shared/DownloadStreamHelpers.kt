package io.github.aedev.flow.ui.components.shared

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.VideoCodecUtils

/**
 * Approximate download size for one quality, or null when no estimate is available — the caller
 * omits the line entirely rather than showing a placeholder.
 */
@Composable
fun approxDownloadSizeLabel(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val context = LocalContext.current
    return stringResource(R.string.download_size_estimate, Formatter.formatShortFileSize(context, bytes))
}

// Every video download is written as an MP4, whatever its codec.
internal fun codecOptionLabel(
    codecKey: String,
    separator: String,
): String = "${VideoCodecUtils.codecLabelFromKey(codecKey)}${separator}MP4"

internal data class AudioLabelStrings(
    val unknownFormat: String,
    val kbps: String,
    val separator: String,
)

/** The compact dialog's audio option. It is persisted as the last audio choice, so its shape must not drift. */
internal fun audioOptionLabel(
    stream: PlayerResponse.StreamingData.Format,
    strings: AudioLabelStrings,
): String {
    val format = DownloadStreamPolicy.audioFormatLabel(stream, strings.unknownFormat)
    val bitrate = DownloadStreamPolicy.audioBitrateKbps(stream)
    val lang = DownloadStreamPolicy.audioLanguageLabel(stream)
    return listOfNotNull("$format${strings.separator}$bitrate${strings.kbps}", lang).joinToString(strings.separator)
}
