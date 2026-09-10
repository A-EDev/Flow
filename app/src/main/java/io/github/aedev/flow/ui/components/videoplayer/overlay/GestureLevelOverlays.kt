package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimGestureHud

private val LevelPillShape = RoundedCornerShape(14.dp)

/**
 * The brightness read-out shown mid-swipe.
 *
 * The level is read inside the `AnimatedVisibility` content, so the swipe recomposes the read-out
 * and nothing above it, and the whole thing — including the spring that chases the level — stops
 * existing the moment the gesture ends.
 */
@Composable
internal fun BrightnessOverlay(
    isVisible: Boolean,
    brightnessLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    GestureLevelHud(isVisible = isVisible, modifier = modifier) {
        val level = brightnessLevel()
        val isAuto = level < 0f
        val animatedBrightness =
            animateFloatAsState(
                targetValue = if (isAuto) 0f else level.coerceIn(0f, 1f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "brightness",
            )
        val iconVector =
            if (isAuto) {
                Icons.Rounded.BrightnessAuto
            } else if (level > 0.7f) {
                Icons.Rounded.BrightnessHigh
            } else if (level > 0.3f) {
                Icons.Rounded.BrightnessMedium
            } else {
                Icons.Rounded.BrightnessLow
            }

        GestureLevelHudContent(
            icon = iconVector,
            valueLabel =
                if (isAuto) {
                    stringResource(R.string.player_brightness_auto)
                } else {
                    stringResource(R.string.player_gesture_level_percent, (level.coerceIn(0f, 1f) * 100).toInt())
                },
            progress = { animatedBrightness.value.coerceIn(0f, 1f) },
            indicatorColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun VolumeOverlay(
    isVisible: Boolean,
    volumeLevel: () -> Float,
    maxVolumeLevel: Float = 2f,
    modifier: Modifier = Modifier,
) {
    GestureLevelHud(isVisible = isVisible, modifier = modifier) {
        val level = volumeLevel()
        val ceiling = maxVolumeLevel.coerceAtLeast(1f)
        val animatedVolume =
            animateFloatAsState(
                targetValue = level.coerceIn(0f, ceiling),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "volume",
            )
        val iconVector =
            if (level > 0.6f) {
                Icons.AutoMirrored.Rounded.VolumeUp
            } else if (level > 0.1f) {
                Icons.AutoMirrored.Rounded.VolumeDown
            } else {
                Icons.AutoMirrored.Rounded.VolumeMute
            }
        val indicatorColor =
            if (level > 1f) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }

        GestureLevelHudContent(
            icon = iconVector,
            valueLabel = stringResource(R.string.player_gesture_level_percent, (level * 100).toInt()),
            progress = { (animatedVolume.value / ceiling).coerceIn(0f, 1f) },
            indicatorColor = indicatorColor,
        )
    }
}

@Composable
private fun GestureLevelHud(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter =
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    initialScale = 0.86f,
                ),
        exit =
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.92f),
        modifier = modifier,
        content = { content() },
    )
}

/**
 * The ring stays a [CircularProgressIndicator]: it reports a level, not a wait, and the M3
 * Expressive `LoadingIndicator` has no determinate ring shape to put in its place. Its fraction
 * arrives as a provider so the spring driving it repaints the ring without recomposing the HUD.
 */
@Composable
private fun GestureLevelHudContent(
    icon: ImageVector,
    valueLabel: String,
    progress: () -> Float,
    indicatorColor: Color,
) {
    Column(
        modifier =
            Modifier
                .width(148.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                color = indicatorColor,
                strokeWidth = 8.dp,
                trackColor = PlayerScrim.copy(alpha = 0.42f),
                strokeCap = StrokeCap.Round,
            )
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PlayerScrimGestureHud),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PlayerScrimContent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .height(28.dp)
                    .clip(LevelPillShape)
                    .background(PlayerScrimGestureHud)
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = valueLabel,
                color = PlayerScrimContent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
