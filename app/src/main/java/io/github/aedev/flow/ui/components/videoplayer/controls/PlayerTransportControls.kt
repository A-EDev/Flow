package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimContentDisabled

private enum class TransportIcon { Buffering, Replay, Pause, Play }

private val PlayPauseButtonSize = 62.dp
private val PlayPauseIconSize = 54.dp
private val BufferingIndicatorSlot = 48.dp
private val SkipButtonSize = 48.dp
private val SkipIconSize = 36.dp

/** Breathing room kept at each end, so the outer button never touches the player edge. */
private val TransportEdgeInset = 8.dp

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
    showFrameStep: Boolean,
    actions: PlayerControlActions,
    modifier: Modifier = Modifier,
    isLayerVisible: () -> Boolean = { true },
) {
    val showIndicator by remember(showBufferingSpinner, isLayerVisible) {
        derivedStateOf { showBufferingSpinner && isLayerVisible() }
    }
    val haptics = LocalHapticFeedback.current
    val iconSwapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val buttonCount = 1 + (if (showSkipButtons) 2 else 0) + (if (showFrameStep) 2 else 0)
        val rowLayout =
            transportRowLayout(
                availableWidth = maxWidth - TransportEdgeInset * 2,
                contentWidth = PlayPauseButtonSize + SkipButtonSize * (buttonCount - 1),
                gaps = buttonCount - 1,
            )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowLayout.spacing),
            modifier =
                Modifier.graphicsLayer {
                    scaleX = rowLayout.scale
                    scaleY = rowLayout.scale
                },
        ) {
            if (showSkipButtons) {
                SkipButton(
                    onClick = actions.onPrevious,
                    enabled = hasPrevious,
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_video),
                )
            }

            if (showFrameStep) {
                SkipButton(
                    onClick = { actions.onStepFrame(false) },
                    enabled = true,
                    icon = Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_frame),
                )
            }

            val playPauseInteractionSource = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(
                        if (isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    actions.onPlayPause()
                },
                shapes = IconButtonDefaults.shapes(),
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
                val transportIcon =
                    when {
                        showIndicator -> TransportIcon.Buffering
                        hasEnded -> TransportIcon.Replay
                        isPlaying -> TransportIcon.Pause
                        else -> TransportIcon.Play
                    }
                AnimatedContent(
                    targetState = transportIcon,
                    transitionSpec = {
                        (scaleIn(iconSwapSpec, initialScale = 0.7f) + fadeIn(iconSwapSpec)) togetherWith
                            (scaleOut(iconSwapSpec, targetScale = 0.7f) + fadeOut(iconSwapSpec))
                    },
                    label = "transportIcon",
                ) { icon ->
                    if (icon == TransportIcon.Buffering) {
                        FlowLoadingIndicator(modifier = Modifier.size(BufferingIndicatorSlot))
                    } else {
                        Icon(
                            imageVector =
                                when (icon) {
                                    TransportIcon.Replay -> Icons.Rounded.Replay
                                    TransportIcon.Pause -> Icons.Rounded.Pause
                                    else -> Icons.Rounded.PlayArrow
                                },
                            contentDescription =
                                when (icon) {
                                    TransportIcon.Replay -> stringResource(R.string.player_replay)
                                    TransportIcon.Pause -> stringResource(R.string.pause)
                                    else -> stringResource(R.string.play)
                                },
                            modifier = Modifier.size(PlayPauseIconSize),
                        )
                    }
                }
            }

            if (showFrameStep) {
                SkipButton(
                    onClick = { actions.onStepFrame(true) },
                    enabled = true,
                    icon = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_frame),
                )
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
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
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
