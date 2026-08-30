package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.pressScale
import io.github.aedev.flow.ui.screens.player.components.LockModeTouchShield
import io.github.aedev.flow.ui.screens.player.components.PlayerTimePill
import io.github.aedev.flow.ui.screens.player.components.SeekbarWithPreview
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.PlayerLiveIndicator
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/** Backdrop for the unlock button, a shade darker than a normal affordance so it reads as modal. */
private const val UNLOCK_AFFORDANCE_ALPHA = 0.42f

@Composable
internal fun BoxScope.PlayerLockedControls(
    isOverlayVisible: Boolean,
    positionProvider: () -> Long,
    duration: Long,
    isLive: Boolean,
    isFullscreen: Boolean,
    showRemainingTime: Boolean,
    seekbarContent: PlayerSeekbarContent,
    pillHeight: Dp,
    topPadding: Dp,
    seekbarHorizontalPadding: Dp,
    seekbarBottomPadding: Dp,
    onRevealUnlock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val reduceMotion = rememberFlowReduceMotion()
    val enterDuration = FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion)
    val exitDuration = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion)

    LockModeTouchShield(
        onRevealUnlock = onRevealUnlock,
        onUnlock = onUnlock,
        modifier = Modifier.matchParentSize(),
    )

    AnimatedVisibility(
        visible = isOverlayVisible,
        enter =
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = enterDuration,
                        easing = FlowMotion.EnterEasing,
                    ),
            ),
        exit =
            fadeOut(
                animationSpec =
                    tween(
                        durationMillis = exitDuration,
                        easing = FlowMotion.ExitEasing,
                    ),
            ),
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            color = PlayerScrim.copy(alpha = UNLOCK_AFFORDANCE_ALPHA),
            shape = CircleShape,
            modifier =
                Modifier
                    .padding(top = topPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .size(48.dp)
                    .pressScale(interactionSource, pressedScale = 0.96f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = PlayerScrimContent),
                        onClick = onUnlock,
                    ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = stringResource(R.string.player_unlock_controls),
                    tint = PlayerScrimContent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isOverlayVisible,
        enter =
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = enterDuration,
                        easing = FlowMotion.EnterEasing,
                    ),
            ),
        exit =
            fadeOut(
                animationSpec =
                    tween(
                        durationMillis = exitDuration,
                        easing = FlowMotion.ExitEasing,
                    ),
            ),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = seekbarHorizontalPadding)
                    .padding(bottom = seekbarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerTimePill(
                positionProvider = positionProvider,
                duration = duration,
                isLive = isLive,
                showRemainingTime = showRemainingTime,
                onClick = null,
                modifier =
                    Modifier
                        .height(pillHeight)
                        .align(Alignment.Start),
            )
            LockedSeekbar(
                positionProvider = positionProvider,
                duration = duration,
                isLive = isLive,
                isFullscreen = isFullscreen,
                content = seekbarContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Read-only progress. Locking exists so a pocket or a passing hand cannot scrub, so this reports
 * position without accepting input.
 */
@Composable
private fun LockedSeekbar(
    positionProvider: () -> Long,
    duration: Long,
    isLive: Boolean,
    isFullscreen: Boolean,
    content: PlayerSeekbarContent,
    modifier: Modifier = Modifier,
) {
    if (isLive && duration <= 0L) {
        Box(
            modifier =
                modifier
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(PlayerLiveIndicator),
        )
        return
    }

    val seekDuration = if (isLive) duration.coerceAtLeast(positionProvider()) else duration
    SeekbarWithPreview(
        value = {
            if (seekDuration > 0) {
                (positionProvider().toFloat() / seekDuration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        },
        onValueChange = {},
        enabled = false,
        chapters = content.chapters,
        sponsorSegments = content.sponsorSegments,
        sponsorColors = content.sponsorColors,
        duration = seekDuration,
        bufferedValue = content.bufferedPercentage,
        edgeAligned = !isFullscreen,
        modifier = modifier,
    )
}
