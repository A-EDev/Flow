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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerState
import io.github.aedev.flow.player.audio.AudioEffectsController

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
    PlayerSettingsSectionHeader(stringResource(R.string.video))
    PlayerSettingsNavRow(
        icon = Icons.Filled.HighQuality,
        label = stringResource(R.string.quality),
        value =
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
    PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
    PlayerSettingsNavRow(
        icon = Icons.Filled.Speed,
        label = stringResource(R.string.playback_speed),
        value =
            if (playerState.playbackSpeed == 1.0f) {
                stringResource(R.string.normal)
            } else {
                "${playerState.playbackSpeed}x"
            },
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Speed)
        },
    )

    // ── Audio Track ──
    PlayerSettingsSectionHeader(stringResource(R.string.audio_settings_title))
    PlayerSettingsNavRow(
        icon = Icons.Filled.AudioFile,
        label = stringResource(R.string.audio_track),
        value =
            audioTrackDisplayLabel(
                playerState.availableAudioTracks.getOrNull(playerState.currentAudioTrack),
                playerState.currentAudioTrack,
            ),
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Audio)
        },
    )

    // ── Captions ──
    PlayerSettingsSectionHeader(stringResource(R.string.captions))
    PlayerSettingsNavRow(
        icon = Icons.Filled.Subtitles,
        label = stringResource(R.string.filter_subtitles),
        value = if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off),
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Subtitles)
        },
    )

    PlayerSettingsNavRow(
        icon = Icons.Filled.Tune,
        label = stringResource(R.string.subtitle_style),
        value = "",
        onClick = { onShowSubtitleStyle() },
    )

    // ── Cast to TV ──
    PlayerSettingsSectionHeader(stringResource(R.string.player_settings_overlay_controls))
    PlayerSettingsNavRow(
        icon = Icons.Filled.Cast,
        label = stringResource(R.string.cast_to_tv),
        value = "",
        onClick = onCastClick,
    )

    // ── Picture-in-Picture ──
    PlayerSettingsNavRow(
        icon = Icons.Filled.PictureInPicture,
        label = stringResource(R.string.pip_mode),
        value = "",
        onClick = onPipClick,
    )

    // ── Sleep Timer ──
    PlayerSettingsNavRow(
        icon = Icons.Filled.Bedtime,
        label = stringResource(R.string.sleep_timer),
        value = "",
        onClick = onSleepTimerClick,
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

    // ── Loop Video ──
    PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
    PlayerSettingsToggleRow(
        icon = Icons.Rounded.Repeat,
        label = stringResource(R.string.loop_video),
        checked = playerState.isLooping,
        onToggle = onLoopToggle,
    )

    // ── Autoplay ──
    PlayerSettingsToggleRow(
        icon = Icons.Filled.SkipNext,
        label = stringResource(R.string.autoplay_next),
        checked = autoplayEnabled,
        enabled = !playerState.isLooping,
        onToggle = onAutoplayToggle,
    )

    // ── Audio Effects ──
    PlayerSettingsSectionHeader(stringResource(R.string.audio_effects))

    // ── Equalizer ──
    val eqProfile by AudioEffectsController.eqProfileName.collectAsState()
    PlayerSettingsNavRow(
        icon = Icons.Filled.Equalizer,
        label = stringResource(R.string.equalizer),
        value = eqProfile,
        onClick = {
            onNavigateToPage(PlayerSettingsPage.Equalizer)
        },
    )

    // ── Skip Silence ──
    PlayerSettingsToggleRow(
        icon = Icons.Rounded.GraphicEq,
        label = stringResource(R.string.player_settings_skip_silence),
        checked = playerState.isSkipSilenceEnabled,
        onToggle = onSkipSilenceToggle,
    )

    // ── Stable Voice ──
    PlayerSettingsToggleRow(
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        label = stringResource(R.string.player_settings_stable_voice),
        checked = playerState.isStableVolumeEnabled,
        onToggle = onStableVolumeToggle,
    )

    // ── Ambient Mode ──
    PlayerSettingsSectionHeader(stringResource(R.string.player_settings_display))
    PlayerSettingsToggleRow(
        icon = ImageVector.vectorResource(R.drawable.ic_ambient_mode),
        label = stringResource(R.string.player_settings_ambient_mode),
        checked = ambientModeEnabled,
        onToggle = onAmbientModeToggle,
    )
}
