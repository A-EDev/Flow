package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R

private const val MIN_LABELLED_BITRATE = 1000

/**
 * One audio track in a track list. The selected track is marked by its label colour and the check
 * only — no container tint, because a track list is usually long enough that a tinted band reads
 * as a section rather than a selection.
 */
@Composable
fun MediaAudioTrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    shape: Shape = RectangleShape,
) {
    FlowSelectionRow(
        title = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        supportingText = supportingText,
        showSelectedContainer = false,
        shape = shape,
    )
}

/** "Audio Track 2" — the label for a track the extractor could not name. */
@Composable
fun audioTrackFallbackLabel(index: Int): String =
    stringResource(
        R.string.audio_track_number_template,
        stringResource(R.string.audio_track),
        index + 1,
    )

/** The bitrate line under a track label, or null when the stream does not report a usable one. */
@Composable
fun audioTrackBitrateLabel(averageBitrate: Int): String? =
    if (averageBitrate >= MIN_LABELLED_BITRATE) {
        stringResource(R.string.audio_bitrate_kbps_template, averageBitrate / MIN_LABELLED_BITRATE)
    } else {
        null
    }
