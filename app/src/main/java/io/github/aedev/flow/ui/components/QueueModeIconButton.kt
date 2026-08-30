package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

@Composable
internal fun QueueModeIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberFlowReduceMotion()
    val iconScale by
        animateFloatAsState(
            targetValue = if (selected && !reduceMotion) 1.06f else 1f,
            animationSpec =
                tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                    easing = FlowMotion.EnterEasing,
                ),
            label = "queueModeScale",
        )

    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.size(48.dp),
        colors =
            IconButtonDefaults.iconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        )
    }
}
