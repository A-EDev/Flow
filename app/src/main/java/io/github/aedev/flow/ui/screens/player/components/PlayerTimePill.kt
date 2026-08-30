package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.pressScale
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.ui.theme.PlayerLiveIndicator
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

private val InteractiveTimePillMinHeight = 48.dp

@Composable
fun PlayerTimePill(
    /**
     * Position provider rather than a value, so the playhead tick recomposes this pill instead of
     * the whole player-controls overlay that hosts it.
     */
    positionProvider: () -> Long,
    duration: Long,
    isLive: Boolean,
    showRemainingTime: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val currentPosition = positionProvider()
    val reduceMotion = rememberFlowReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val interactionModifier =
        if (onClick != null) {
            Modifier
                .heightIn(min = InteractiveTimePillMinHeight)
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
        } else {
            Modifier
        }

    Surface(
        color = PlayerScrimAffordance,
        shape = CircleShape,
        modifier =
            modifier
                .clip(CircleShape)
                .then(interactionModifier),
    ) {
        Row(
            modifier =
                Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLive) {
                Text(
                    text = VideoPlayerUtils.formatTime(currentPosition, padMinutes = true),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerScrimContent,
                    fontWeight = FontWeight.Bold,
                )
                TimeSeparator()
                val dotAlpha =
                    if (reduceMotion) {
                        1f
                    } else {
                        val animatedDotAlpha by rememberInfiniteTransition(label = "liveDot").animateFloat(
                            initialValue = 1f,
                            targetValue = 0.2f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "dotAlpha",
                        )
                        animatedDotAlpha
                    }
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PlayerLiveIndicator.copy(alpha = dotAlpha)),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.player_live_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerLiveIndicator,
                    fontWeight = FontWeight.ExtraBold,
                )
            } else {
                Text(
                    text =
                        if (showRemainingTime) {
                            "-${VideoPlayerUtils.formatTime((duration - currentPosition).coerceAtLeast(0), padMinutes = true)}"
                        } else {
                            VideoPlayerUtils.formatTime(currentPosition, padMinutes = true)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerScrimContent,
                    fontWeight = FontWeight.Bold,
                )
                TimeSeparator()
                Text(
                    text = VideoPlayerUtils.formatTime(duration, padMinutes = true),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerScrimContent.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TimeSeparator() {
    Text(
        text = stringResource(R.string.player_time_separator),
        style = MaterialTheme.typography.labelMedium,
        color = PlayerScrimContent.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}
