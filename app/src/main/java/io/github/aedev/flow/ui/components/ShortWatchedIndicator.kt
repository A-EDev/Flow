package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/**
 * Shows watch progress on a Shorts thumbnail and marks reels that already crossed the watched threshold.
 *
 * Progress comes from the shared watch-progress store, so a grid keeps one history observer while
 * only thumbnails whose entries change need to redraw.
 */
@Composable
fun ShortWatchedIndicator(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val progress = rememberWatchProgress(videoId) ?: return
    val reduceMotion = rememberFlowReduceMotion()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = progress >= WATCHED_PROGRESS_THRESHOLD,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            enter =
                fadeIn(
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
                ),
            exit =
                fadeOut(
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
                ),
            label = "shortWatchedBadge",
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.cd_short_watched),
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .size(14.dp),
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
        )
    }
}
