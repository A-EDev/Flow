package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.utils.formatMultiplierLabel

@Composable
internal fun SpeedBoostOverlay(
    isVisible: Boolean,
    speed: Float = 2.0f,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter =
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.fastSpatialSpec(), expandFrom = Alignment.Top),
        exit =
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Surface(
            color = PlayerScrim.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier.wrapContentSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = formatMultiplierLabel(speed, maxValue = 4.0f),
                    color = PlayerScrimContent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = PlayerScrimContent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
