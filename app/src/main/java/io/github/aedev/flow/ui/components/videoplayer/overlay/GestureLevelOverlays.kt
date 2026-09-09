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

@Composable
internal fun BrightnessOverlay(
    isVisible: Boolean,
    brightnessLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    val level = brightnessLevel()
    val isAuto = level < 0f
    val animatedBrightness by animateFloatAsState(
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

    CircularGestureLevelOverlay(
        isVisible = isVisible,
        icon = iconVector,
        valueLabel =
            if (isAuto) {
                stringResource(R.string.player_brightness_auto)
            } else {
                stringResource(R.string.player_gesture_level_percent, (level.coerceIn(0f, 1f) * 100).toInt())
            },
        progress = animatedBrightness,
        indicatorColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
internal fun VolumeOverlay(
    isVisible: Boolean,
    volumeLevel: () -> Float,
    maxVolumeLevel: Float = 2f,
    modifier: Modifier = Modifier,
) {
    val level = volumeLevel()
    val animatedVolume by animateFloatAsState(
        targetValue = level.coerceIn(0f, maxVolumeLevel.coerceAtLeast(1f)),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "volume",
    )
    val fillFraction = (animatedVolume / maxVolumeLevel.coerceAtLeast(1f)).coerceIn(0f, 1f)
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

    CircularGestureLevelOverlay(
        isVisible = isVisible,
        icon = iconVector,
        valueLabel = stringResource(R.string.player_gesture_level_percent, (level * 100).toInt()),
        progress = fillFraction,
        indicatorColor = indicatorColor,
        modifier = modifier,
    )
}

@Composable
private fun CircularGestureLevelOverlay(
    isVisible: Boolean,
    icon: ImageVector,
    valueLabel: String,
    progress: Float,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter =
            fadeIn(tween(120)) +
                scaleIn(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    initialScale = 0.86f,
                ),
        exit = fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.92f),
        modifier = modifier,
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
                    progress = { progress.coerceIn(0f, 1f) },
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
                        .clip(RoundedCornerShape(14.dp))
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
}
