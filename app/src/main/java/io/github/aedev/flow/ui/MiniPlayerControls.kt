package io.github.aedev.flow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.state.EnhancedPlayerState

/**
 * Mini Player Controls - Dynamically arranges Play/Pause, Rewind/FastForward, and Next/Previous.
 */
@Composable
internal fun MiniPlayerControls(
    playerState: EnhancedPlayerState,
    showSkipControls: Boolean,
    showNextPrevControls: Boolean,
    sizeScale: Float = 1f,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    val scaleMult = sizeScale.coerceIn(1f, 1.6f)

    val baseTouchSize = if (isTablet) 44.dp else 36.dp
    val baseBgSize  = if (isTablet) 34.dp else 24.dp
    val baseIconSize = if (isTablet) 30.dp else 24.dp
    val finalTouchSize = baseTouchSize * scaleMult
    val finalBgSize   = baseBgSize   * scaleMult
    val finalIconSize = baseIconSize * scaleMult
    val topTouchSize = if (isTablet) 50.dp else 42.dp
    val topBgSize = if (isTablet) 42.dp else 34.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(topTouchSize)
        ) {
            MiniPlayerButtonBackground(
                backgroundSize = topBgSize,
                backgroundAlpha = 0.28f
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (isTablet) 30.dp else 24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = when {
                            playerState.hasEnded -> Icons.Rounded.Replay
                            playerState.playWhenReady -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            playerState.hasEnded -> "Replay"
                            playerState.playWhenReady -> "Pause"
                            else -> "Play"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(if (isTablet) 42.dp else 34.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = {
                EnhancedPlayerManager.getInstance().stop()
                GlobalPlayerState.hideMiniPlayer()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(topTouchSize)
        ) {
            MiniPlayerButtonBackground(
                backgroundSize = topBgSize,
                backgroundAlpha = 0.28f
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(if (isTablet) 34.dp else 30.dp)
                )
            }
        }

        if (showSkipControls || showNextPrevControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showNextPrevControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onPrevious
                    )
                }

                if (showSkipControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Skip Back 10s",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onSkipBack
                    )
                }

                if (showSkipControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = "Skip Forward 10s",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onSkipForward
                    )
                }

                if (showNextPrevControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onNext
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    touchSize: Dp,
    backgroundSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(touchSize)
    ) {
        MiniPlayerButtonBackground(backgroundSize = backgroundSize) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun MiniPlayerButtonBackground(
    backgroundSize: Dp,
    backgroundAlpha: Float = 0.36f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(backgroundSize)
            .background(Color.Black.copy(alpha = backgroundAlpha), CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}
