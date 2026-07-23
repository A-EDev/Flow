package io.github.aedev.flow.ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.components.TvIconButton
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.player.state.TvPlayerPanel

@Composable
fun TvTransportRow(
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    subtitlesAvailable: Boolean,
    subtitlesEnabled: Boolean,
    autoplayEnabled: Boolean,
    isLooping: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onToggleLoop: () -> Unit,
    onClose: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    isLive: Boolean,
    playPauseFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvRowFocus(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIconButton(
            icon = Icons.Outlined.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            onClick = onPrevious,
            enabled = hasPrevious,
        )
        TvIconButton(
            icon = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (isPlaying) {
                stringResource(R.string.pause)
            } else {
                stringResource(R.string.play)
            },
            onClick = onTogglePlayback,
            focusRequester = playPauseFocusRequester,
        )
        TvIconButton(
            icon = Icons.Outlined.SkipNext,
            contentDescription = stringResource(R.string.next),
            onClick = onNext,
            enabled = hasNext,
        )
        if (subtitlesAvailable) {
            TvIconButton(
                icon = Icons.Outlined.ClosedCaption,
                contentDescription = stringResource(R.string.filter_subtitles),
                onClick = onToggleCaptions,
                active = subtitlesEnabled,
            )
        }
        TvIconButton(
            icon = Icons.Outlined.PlaylistPlay,
            contentDescription = stringResource(R.string.autoplay),
            onClick = onToggleAutoplay,
            active = autoplayEnabled,
        )
        TvIconButton(
            icon = Icons.Outlined.Repeat,
            contentDescription = stringResource(R.string.loop_video),
            onClick = onToggleLoop,
            active = isLooping,
        )
        TvIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.tv_player_settings),
            onClick = { onOpenPanel(TvPlayerPanel.SETTINGS) },
        )
        TvIconButton(
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
            contentDescription = stringResource(R.string.tv_player_queue),
            onClick = { onOpenPanel(TvPlayerPanel.QUEUE) },
        )
        if (isLive) {
            TvIconButton(
                icon = Icons.Outlined.Forum,
                contentDescription = stringResource(R.string.live_chat),
                onClick = { onOpenPanel(TvPlayerPanel.LIVE_CHAT) },
            )
        } else {
            TvIconButton(
                icon = Icons.Outlined.Comment,
                contentDescription = stringResource(R.string.comments),
                onClick = { onOpenPanel(TvPlayerPanel.COMMENTS) },
            )
        }
        TvIconButton(
            icon = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.description),
            onClick = { onOpenPanel(TvPlayerPanel.DESCRIPTION) },
        )
        TvIconButton(
            icon = Icons.Outlined.Timer,
            contentDescription = stringResource(R.string.sleep_timer),
            onClick = { onOpenPanel(TvPlayerPanel.SLEEP_TIMER) },
        )
        TvIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.tv_player_close),
            onClick = onClose,
        )
    }
}
