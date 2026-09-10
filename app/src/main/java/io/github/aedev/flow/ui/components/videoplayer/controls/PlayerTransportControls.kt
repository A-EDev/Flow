package io.github.aedev.flow.ui.components.videoplayer.controls

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimContentDisabled

private val PlayPauseButtonSize = 62.dp
private val PlayPauseIconSize = 54.dp
private val BufferingIndicatorSlot = 48.dp
private val SkipButtonSize = 48.dp
private val SkipIconSize = 36.dp

/**
 * Previous / play-pause / next.
 *
 * [showSkipButtons] is false during the initial load, when the queue is not yet known well enough
 * for skipping to mean anything — the play button stays so the loading spinner has a home.
 *
 * [isLayerVisible] gates the buffering indicator: the controls stay composed behind the video while
 * hidden, and a morphing indicator nobody can see still costs a frame every frame.
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
    isLayerVisible: () -> Boolean = { true },
) {
    val showIndicator by remember(showBufferingSpinner, isLayerVisible) {
        derivedStateOf { showBufferingSpinner && isLayerVisible() }
    }

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
            FilledIconButton(
                onClick = actions.onPlayPause,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = PlayerScrimAffordance,
                        contentColor = PlayerScrimContent,
                    ),
                interactionSource = playPauseInteractionSource,
                modifier =
                    Modifier
                        .size(PlayPauseButtonSize)
                        .pressScale(playPauseInteractionSource, pressedScale = 0.88f),
            ) {
                if (showIndicator) {
                    FlowLoadingIndicator(modifier = Modifier.size(BufferingIndicatorSlot))
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
                        modifier = Modifier.size(PlayPauseIconSize),
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
                .size(SkipButtonSize)
                .pressScale(interactionSource, pressedScale = 0.82f),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) PlayerScrimContent else PlayerScrimContentDisabled,
            modifier = Modifier.size(SkipIconSize),
        )
    }
}
