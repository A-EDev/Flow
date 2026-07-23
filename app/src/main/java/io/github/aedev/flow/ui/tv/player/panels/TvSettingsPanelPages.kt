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
import io.github.aedev.flow.player.state.SubtitleOption
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
    subtitlesAvailable: Boolean,
    audioTracksAvailable: Boolean,
    autoplayEnabled: Boolean,
    onToggleAutoplay: (Boolean) -> Unit,
    isLooping: Boolean,
    onToggleLoop: (Boolean) -> Unit,
    skipSilenceEnabled: Boolean,
    onToggleSkipSilence: (Boolean) -> Unit,
    stableVolumeEnabled: Boolean,
    onToggleStableVolume: (Boolean) -> Unit,
    ambientModeEnabled: Boolean,
    onToggleAmbientMode: (Boolean) -> Unit,
    onOpen: (TvPlayerPanel) -> Unit,
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
        item(key = "autoplay") {
            TvToggleRow(
                label = stringResource(R.string.autoplay),
                checked = autoplayEnabled,
                onCheckedChange = onToggleAutoplay,
            )
        }
        item(key = "loop") {
            TvToggleRow(
                label = stringResource(R.string.loop_video),
                checked = isLooping,
                onCheckedChange = onToggleLoop,
            )
        }
        item(key = "skip-silence") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_skip_silence),
                checked = skipSilenceEnabled,
                onCheckedChange = onToggleSkipSilence,
            )
        }
        item(key = "stable-volume") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_stable_voice),
                checked = stableVolumeEnabled,
                onCheckedChange = onToggleStableVolume,
            )
        }
        item(key = "ambient") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_ambient_mode),
                checked = ambientModeEnabled,
                onCheckedChange = onToggleAmbientMode,
            )
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

/**
 * Lists the player's full caption set ([SubtitleOption], the same list the
 * mobile picker shows — incl. auto-translations), index-aligned with the
 * player's text tracks so selection maps 1:1.
 */
@Composable
fun TvSubtitlesPage(
    subtitles: List<SubtitleOption>,
    selectedUrl: String?,
    subtitlesEnabled: Boolean,
    onDisable: () -> Unit,
    onSelect: (Int, SubtitleOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val automaticLabel = stringResource(R.string.quality_auto)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "off") {
            TvSelectionRow(
                label = stringResource(R.string.off),
                selected = !subtitlesEnabled,
                onClick = onDisable,
            )
        }
        items(count = subtitles.size, key = { subtitles[it].url + it }) { index ->
            val subtitle = subtitles[index]
            TvSelectionRow(
                label = if (subtitle.isAutoGenerated) {
                    stringResource(
                        R.string.subtitle_auto_generated_template,
                        subtitle.label,
                        automaticLabel,
                    )
                } else {
                    subtitle.label
                },
                supportingText = subtitle.language.takeIf { it.isNotBlank() },
                selected = subtitlesEnabled && subtitle.url == selectedUrl,
                onClick = { onSelect(index, subtitle) },
            )
        }
    }
}

fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"
