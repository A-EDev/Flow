package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.pressScale
import io.github.aedev.flow.ui.screens.player.SleekLoadingAnimation
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimContentDisabled

/**
 * Previous / play-pause / next.
 *
 * [showSkipButtons] is false during the initial load, when the queue is not yet known well enough
 * for skipping to mean anything — the play button stays so the loading spinner has a home.
 */
@Composable
internal fun PlayerTransportControls(
    isPlaying: Boolean,
    hasEnded: Boolean,
    showBufferingSpinner: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    showSkipButtons: Boolean,
    actions: PlayerControlActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            if (showSkipButtons) {
                SkipButton(
                    onClick = actions.onPrevious,
                    enabled = hasPrevious,
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_video),
                )
            }

            val playPauseInteractionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(62.dp)
                        .pressScale(playPauseInteractionSource, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .background(PlayerScrimAffordance)
                        .clickable(
                            interactionSource = playPauseInteractionSource,
                            indication = ripple(color = PlayerScrimContent),
                        ) { actions.onPlayPause() },
            ) {
                if (showBufferingSpinner) {
                    SleekLoadingAnimation(modifier = Modifier.size(48.dp))
                } else {
                    Icon(
                        imageVector =
                            when {
                                hasEnded -> Icons.Rounded.Replay
                                isPlaying -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                        contentDescription =
                            when {
                                hasEnded -> stringResource(R.string.player_replay)
                                isPlaying -> stringResource(R.string.pause)
                                else -> stringResource(R.string.play)
                            },
                        tint = PlayerScrimContent,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }

            if (showSkipButtons) {
                SkipButton(
                    onClick = actions.onNext,
                    enabled = hasNext,
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next_video),
                )
            }
        }
    }
}

@Composable
private fun SkipButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(48.dp)
                .pressScale(interactionSource, pressedScale = 0.82f),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) PlayerScrimContent else PlayerScrimContentDisabled,
            modifier = Modifier.size(36.dp),
        )
    }
}
