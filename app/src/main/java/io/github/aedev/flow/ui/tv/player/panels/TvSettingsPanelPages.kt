package io.github.aedev.flow.ui.tv.player.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.player.state.AudioTrackOption
import io.github.aedev.flow.ui.screens.player.SubtitleInfo
import io.github.aedev.flow.ui.tv.components.TvNavRow
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.components.TvToggleRow
import io.github.aedev.flow.ui.tv.player.state.TvPlayerPanel
import androidx.compose.ui.unit.dp

val TV_PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Player-settings pages rendered inside the shared side panel. Mirrors the
 * mobile PlayerSettingsMenu's data plumbing without its drag-sheet machinery.
 */
@Composable
fun TvSettingsMainPage(
    currentQualityLabel: String,
    currentSpeed: Float,
    currentAudioLabel: String?,
    currentSubtitleLabel: String?,
    onOpen: (TvPlayerPanel) -> Unit,
    subtitlesAvailable: Boolean,
    audioTracksAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "quality") {
            TvNavRow(
                label = stringResource(R.string.quality),
                value = currentQualityLabel,
                onClick = { onOpen(TvPlayerPanel.QUALITY) },
            )
        }
        item(key = "speed") {
            TvNavRow(
                label = stringResource(R.string.playback_speed),
                value = formatSpeedLabel(currentSpeed),
                onClick = { onOpen(TvPlayerPanel.SPEED) },
            )
        }
        if (audioTracksAvailable) {
            item(key = "audio") {
                TvNavRow(
                    label = stringResource(R.string.audio_track),
                    value = currentAudioLabel,
                    onClick = { onOpen(TvPlayerPanel.AUDIO) },
                )
            }
        }
        if (subtitlesAvailable) {
            item(key = "subtitles") {
                TvNavRow(
                    label = stringResource(R.string.filter_subtitles),
                    value = currentSubtitleLabel,
                    onClick = { onOpen(TvPlayerPanel.SUBTITLES) },
                )
            }
        }
    }
}

@Composable
fun TvQualityPage(
    qualities: List<VideoQuality>,
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = qualities.sortedByDescending { it.height }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = sorted.size, key = { sorted[it].name }) { index ->
            val quality = sorted[index]
            TvSelectionRow(
                label = quality.label,
                selected = quality == selected,
                onClick = { onSelect(quality) },
            )
        }
    }
}

@Composable
fun TvSpeedPage(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = TV_PLAYBACK_SPEEDS.size, key = { TV_PLAYBACK_SPEEDS[it] }) { index ->
            val speed = TV_PLAYBACK_SPEEDS[index]
            TvSelectionRow(
                label = formatSpeedLabel(speed),
                selected = speed == currentSpeed,
                onClick = { onSelect(speed) },
            )
        }
    }
}

@Composable
fun TvAudioPage(
    tracks: List<AudioTrackOption>,
    currentIndex: Int,
    onSelect: (AudioTrackOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = tracks.size, key = { tracks[it].index }) { i ->
            val track = tracks[i]
            TvSelectionRow(
                label = track.label,
                supportingText = track.language.takeIf { it.isNotBlank() },
                selected = track.index == currentIndex,
                onClick = { onSelect(track) },
            )
        }
    }
}

@Composable
fun TvSubtitlesPage(
    subtitles: List<SubtitleInfo>,
    selected: SubtitleInfo?,
    subtitlesEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSelect: (SubtitleInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "toggle") {
            TvToggleRow(
                label = stringResource(R.string.filter_subtitles),
                checked = subtitlesEnabled,
                onCheckedChange = onToggle,
            )
        }
        items(count = subtitles.size, key = { subtitles[it].url + subtitles[it].languageCode }) { i ->
            val subtitle = subtitles[i]
            TvSelectionRow(
                label = subtitle.language,
                supportingText = if (subtitle.isAutoGenerated) subtitle.format else null,
                selected = subtitle == selected,
                onClick = { onSelect(subtitle) },
            )
        }
    }
}

fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"
