package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.GestureOverlayStyle
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimGestureHud

private val LevelPillShape = RoundedCornerShape(14.dp)
private val BarTrackShape = RoundedCornerShape(10.dp)

private val VerticalBarWidth = 46.dp
private val VerticalBarHeight = 176.dp
private val HorizontalBarWidth = 208.dp
private val HorizontalBarHeight = 44.dp

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
    style: GestureOverlayStyle,
    modifier: Modifier = Modifier,
) {
    GestureLevelHud(isVisible = isVisible, modifier = modifier) {
        val level = brightnessLevel()
        val isAuto = level < 0f
        val animatedBrightness =
            animateFloatAsState(
                targetValue = if (isAuto) 0f else level.coerceIn(0f, 1f),
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
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
            style = style,
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
    style: GestureOverlayStyle,
    maxVolumeLevel: Float = 2f,
    modifier: Modifier = Modifier,
) {
    GestureLevelHud(isVisible = isVisible, modifier = modifier) {
        val level = volumeLevel()
        val ceiling = maxVolumeLevel.coerceAtLeast(1f)
        val animatedVolume =
            animateFloatAsState(
                targetValue = level.coerceIn(0f, ceiling),
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
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
            style = style,
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
 * The read-out itself, in whichever shape the user picked (#1029).
 *
 * Every style takes the same four inputs and the fraction always arrives as a provider, so the
 * spring driving it repaints the bar or the ring without recomposing the HUD around it.
 */
@Composable
private fun GestureLevelHudContent(
    style: GestureOverlayStyle,
    icon: ImageVector,
    valueLabel: String,
    progress: () -> Float,
    indicatorColor: Color,
) {
    when (style) {
        GestureOverlayStyle.CIRCULAR -> {
            CircularLevelHud(icon, valueLabel, progress, indicatorColor)
        }

        GestureOverlayStyle.VERTICAL -> {
            VerticalLevelHud(icon, valueLabel, progress, indicatorColor)
        }

        GestureOverlayStyle.HORIZONTAL -> {
            HorizontalLevelHud(icon, valueLabel, progress, indicatorColor)
        }

        GestureOverlayStyle.MINIMAL -> {
            MinimalLevelHud(icon, valueLabel)
        }
    }
}

/**
 * The ring stays a [CircularProgressIndicator]: it reports a level, not a wait, and the M3
 * Expressive `LoadingIndicator` has no determinate ring shape to put in its place.
 */
@Composable
private fun CircularLevelHud(
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
                trackColor = PlayerScrimAffordance,
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
        LevelPill(valueLabel)
    }
}

/**
 * A column that fills from the bottom, sitting against the edge the gesture came from. The point of
 * this style is that it leaves the middle of the frame — the part being adjusted — uncovered.
 */
@Composable
private fun VerticalLevelHud(
    icon: ImageVector,
    valueLabel: String,
    progress: () -> Float,
    indicatorColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelPill(valueLabel)
        Box(
            modifier =
                Modifier
                    .size(width = VerticalBarWidth, height = VerticalBarHeight)
                    .clip(BarTrackShape)
                    .background(PlayerScrimGestureHud),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val filled = size.height * progress().coerceIn(0f, 1f)
                if (filled <= 0f) return@Canvas
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(0f, size.height - filled),
                    size = Size(size.width, filled),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PlayerScrimContent,
                modifier =
                    Modifier
                        .padding(bottom = 10.dp)
                        .size(24.dp),
            )
        }
    }
}

/** A single bar with the icon inside it and the value at the end. */
@Composable
private fun HorizontalLevelHud(
    icon: ImageVector,
    valueLabel: String,
    progress: () -> Float,
    indicatorColor: Color,
) {
    Box(
        modifier =
            Modifier
                .size(width = HorizontalBarWidth, height = HorizontalBarHeight)
                .clip(BarTrackShape)
                .background(PlayerScrimGestureHud),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val filled = size.width * progress().coerceIn(0f, 1f)
            if (filled <= 0f) return@Canvas
            drawRect(
                color = indicatorColor.copy(alpha = 0.55f),
                size = Size(filled, size.height),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PlayerScrimContent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = valueLabel,
                color = PlayerScrimContent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Icon and number only — the smallest thing that still answers "what am I changing, and to what". */
@Composable
private fun MinimalLevelHud(
    icon: ImageVector,
    valueLabel: String,
) {
    Row(
        modifier =
            Modifier
                .clip(LevelPillShape)
                .background(PlayerScrimGestureHud)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PlayerScrimContent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = valueLabel,
            color = PlayerScrimContent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LevelPill(valueLabel: String) {
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
