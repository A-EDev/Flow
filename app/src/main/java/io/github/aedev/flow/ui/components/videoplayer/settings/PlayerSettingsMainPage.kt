package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerState
import io.github.aedev.flow.player.audio.AudioEffectsController
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.components.shared.playbackSpeedLabel

@Composable
internal fun PlayerSettingsMainPage(
    playerState: EnhancedPlayerState,
    autoplayEnabled: Boolean,
    subtitlesEnabled: Boolean,
    ambientModeEnabled: Boolean,
    onNavigateToPage: (PlayerSettingsPage) -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onCastClick: () -> Unit,
    onPipClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onSkipSilenceToggle: (Boolean) -> Unit,
    onStableVolumeToggle: (Boolean) -> Unit,
    onAmbientModeToggle: (Boolean) -> Unit,
) {
    FlowSectionHeader(stringResource(R.string.video))
    FlowNavRow(
        leadingIcon = Icons.Filled.HighQuality,
        title = stringResource(R.string.quality),
        trailingText =
            if (playerState.currentQuality == 0) {
                stringResource(R.string.quality_auto)
            } else {
                "${playerState.currentQuality}p"
            },
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Quality)
        },
    )

    // ── Playback Speed ──
    FlowSectionHeader(stringResource(R.string.playback_header))
    FlowNavRow(
        leadingIcon = Icons.Filled.Speed,
        title = stringResource(R.string.playback_speed),
        trailingText = playbackSpeedLabel(playerState.playbackSpeed),
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Speed)
        },
    )

    // ── Audio Track ──
    FlowSectionHeader(stringResource(R.string.audio_settings_title))
    FlowNavRow(
        leadingIcon = Icons.Filled.AudioFile,
        title = stringResource(R.string.audio_track),
        trailingText =
            audioTrackDisplayLabel(
                playerState.availableAudioTracks.getOrNull(playerState.currentAudioTrack),
                playerState.currentAudioTrack,
            ),
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Audio)
        },
    )

    // ── Captions ──
    FlowSectionHeader(stringResource(R.string.captions))
    FlowNavRow(
        leadingIcon = Icons.Filled.Subtitles,
        title = stringResource(R.string.filter_subtitles),
        trailingText =
            if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off),
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Subtitles)
        },
    )

    FlowNavRow(
        leadingIcon = Icons.Filled.Tune,
        title = stringResource(R.string.subtitle_style),
        onClick = { onShowSubtitleStyle() },
    )

    // ── Cast to TV ──
    FlowSectionHeader(stringResource(R.string.player_settings_overlay_controls))
    FlowNavRow(
        leadingIcon = Icons.Filled.Cast,
        title = stringResource(R.string.cast_to_tv),
        onClick = onCastClick,
    )

    // ── Picture-in-Picture ──
    FlowNavRow(
        leadingIcon = Icons.Filled.PictureInPicture,
        title = stringResource(R.string.pip_mode),
        onClick = onPipClick,
    )

    // ── Sleep Timer ──
    FlowNavRow(
        leadingIcon = Icons.Filled.Bedtime,
        title = stringResource(R.string.sleep_timer),
        onClick = onSleepTimerClick,
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

    // ── Loop Video ──
    FlowSectionHeader(stringResource(R.string.playback_header))
    FlowSwitchRow(
        leadingIcon = Icons.Rounded.Repeat,
        title = stringResource(R.string.loop_video),
        checked = playerState.isLooping,
        onCheckedChange = onLoopToggle,
    )

    // ── Autoplay ──
    FlowSwitchRow(
        leadingIcon = Icons.Filled.SkipNext,
        title = stringResource(R.string.autoplay_next),
        checked = autoplayEnabled,
        enabled = !playerState.isLooping,
        onCheckedChange = onAutoplayToggle,
    )

    // ── Audio Effects ──
    FlowSectionHeader(stringResource(R.string.audio_effects))

    // ── Equalizer ──
    val eqProfile by AudioEffectsController.eqProfileName.collectAsStateWithLifecycle()
    FlowNavRow(
        leadingIcon = Icons.Filled.Equalizer,
        title = stringResource(R.string.equalizer),
        trailingText = eqProfile,
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Equalizer)
        },
    )

    // ── Skip Silence ──
    FlowSwitchRow(
        leadingIcon = Icons.Rounded.GraphicEq,
        title = stringResource(R.string.player_settings_skip_silence),
        checked = playerState.isSkipSilenceEnabled,
        onCheckedChange = onSkipSilenceToggle,
    )

    // ── Stable Voice ──
    FlowSwitchRow(
        leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
        title = stringResource(R.string.player_settings_stable_voice),
        checked = playerState.isStableVolumeEnabled,
        onCheckedChange = onStableVolumeToggle,
    )

    // ── Ambient Mode ──
    FlowSectionHeader(stringResource(R.string.player_settings_display))
    FlowSwitchRow(
        leadingIcon = ImageVector.vectorResource(R.drawable.ic_ambient_mode),
        title = stringResource(R.string.player_settings_ambient_mode),
        checked = ambientModeEnabled,
        onCheckedChange = onAmbientModeToggle,
    )
}
