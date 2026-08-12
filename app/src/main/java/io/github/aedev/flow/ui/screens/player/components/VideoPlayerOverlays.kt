package io.github.aedev.flow.ui.screens.player.components

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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PlayerGestureOverlays(
    screenState: PlayerScreenState,
    allowVolumeBoost: Boolean,
    speedBoostSpeed: Float,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            SeekAnimationOverlay(
                showSeekBack = screenState.showSeekBackAnimation,
                showSeekForward = screenState.showSeekForwardAnimation,
                seekSeconds = screenState.seekAccumulation,
                modifier = Modifier.align(Alignment.Center),
            )

            BrightnessOverlay(
                isVisible = screenState.showBrightnessOverlay,
                brightnessLevel = { screenState.brightnessLevel },
                modifier =
                    Modifier
                        .align(Alignment.Center),
            )

            VolumeOverlay(
                isVisible = screenState.showVolumeOverlay,
                volumeLevel = { screenState.volumeLevel },
                maxVolumeLevel = if (allowVolumeBoost) 2f else 1f,
                modifier =
                    Modifier
                        .align(Alignment.Center),
            )

            SeekDragOverlay(
                isVisible = screenState.isSeekDragging,
                targetMs = { screenState.seekDragTargetMs },
                deltaMs = { screenState.seekDragDeltaMs },
                modifier = Modifier.align(Alignment.Center),
            )

            SpeedBoostOverlay(
                isVisible = screenState.isSpeedBoostActive,
                speed = speedBoostSpeed,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (screenState.isFullscreen) {
                                Modifier
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                                    .padding(top = 8.dp)
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

// Fraction of the player width each seek zone covers; mirrors SEEK_ZONE_FRACTION in the gesture layer.
private const val SEEK_ZONE_WIDTH_FRACTION = 1f / 3f
private const val SEEK_RIPPLE_ALPHA = 0.15f
private const val SEEK_RIPPLE_PULSE_ALPHA = 0.28f

@Composable
fun SeekAnimationOverlay(
    showSeekBack: Boolean,
    showSeekForward: Boolean,
    seekSeconds: Int = 10,
    modifier: Modifier = Modifier,
) {
    // Force LTR so CenterStart/CenterEnd always map to physical left/right,
    // regardless of the device's system language direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            SeekZoneRipple(
                visible = showSeekBack,
                forward = false,
                pulseKey = seekSeconds,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(SEEK_ZONE_WIDTH_FRACTION),
            )

            SeekZoneRipple(
                visible = showSeekForward,
                forward = true,
                pulseKey = seekSeconds,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(SEEK_ZONE_WIDTH_FRACTION),
            )

            AnimatedVisibility(
                visible = showSeekBack,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to forward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekForward) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
            ) {
                SeekChevronLabel(forward = false, seconds = seekSeconds)
            }

            AnimatedVisibility(
                visible = showSeekForward,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to backward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekBack) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
            ) {
                SeekChevronLabel(forward = true, seconds = seekSeconds)
            }
        }
    }
}

/**
 * The tinted zone that flashes behind a double-tap seek.
 *
 * Drawn as an oversized circle clipped to the zone so the outer edge sits flush against the screen
 * while the inner edge bulges — the shape reads as "this side of the player reacted" without any
 * shadow or glow. The animated alpha is read inside `graphicsLayer`, so repeated taps repaint
 * without recomposing anything.
 */
@Composable
private fun SeekZoneRipple(
    visible: Boolean,
    forward: Boolean,
    pulseKey: Int,
    modifier: Modifier = Modifier,
) {
    val rippleAlpha = remember { Animatable(0f) }

    LaunchedEffect(visible, pulseKey) {
        if (visible) {
            rippleAlpha.snapTo(SEEK_RIPPLE_PULSE_ALPHA)
            rippleAlpha.animateTo(SEEK_RIPPLE_ALPHA, tween(300, easing = FastOutSlowInEasing))
        } else {
            rippleAlpha.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer { alpha = rippleAlpha.value },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Smallest radius whose circle still covers both corners on the flush edge, so the shape
        // never leaves a sliver of untinted video at the screen border.
        val radius = w / 2f + (h * h) / (8f * w)
        val centerX = if (forward) radius else w - radius

        clipRect(left = 0f, top = 0f, right = w, bottom = h) {
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(centerX, h / 2f),
            )
        }
    }
}

@Composable
private fun SeekChevronLabel(
    forward: Boolean,
    seconds: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chevron")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "chevronProgress",
    )

    val offsetProgress = LinearOutSlowInEasing.transform(progress)
    val chevronOffset = if (forward) 24f * offsetProgress else -24f * offsetProgress

    val chevronAlpha =
        when {
            progress < 0.2f -> progress * 5f
            progress > 0.5f -> (1f - progress) * 2f
            else -> 1f
        }.coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!forward) {
            Text(
                text = "<",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp),
            )
        }
        Text(
            text = if (forward) "+$seconds" else "-$seconds",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (forward) {
            Text(
                text = ">",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp),
            )
        }
    }
}

@Composable
fun BrightnessOverlay(
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
fun VolumeOverlay(
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

/**
 * Target-time preview shown while dragging horizontally to seek.
 *
 * The drag only commits on release, so this is the sole feedback the gesture gives: the absolute
 * time it will land on, and how far that is from where playback currently sits.
 */
@Composable
fun SeekDragOverlay(
    isVisible: Boolean,
    targetMs: () -> Long,
    deltaMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(120)) + scaleIn(animationSpec = tween(120), initialScale = 0.92f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.94f),
        modifier = modifier,
    ) {
        val target = targetMs()
        val delta = deltaMs()
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = VideoPlayerUtils.formatTime(target, padMinutes = true),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    stringResource(
                        if (delta < 0L) R.string.player_seek_delta_back else R.string.player_seek_delta_forward,
                        VideoPlayerUtils.formatTime(abs(delta)),
                    ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
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
                    trackColor = Color.Black.copy(alpha = 0.42f),
                    strokeCap = StrokeCap.Round,
                )
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.54f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
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
                        .background(Color.Black.copy(alpha = 0.54f))
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = valueLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SpeedBoostOverlay(
    isVisible: Boolean,
    speed: Float = 2.0f,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier.wrapContentSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = VideoPlayerUtils.formatSpeedLabel(speed, maxSpeed = 4.0f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val SB_SKIP_DIM_DELAY_MS = 5_000L
private const val SB_SKIP_DIMMED_ALPHA = 0.45f

private fun sbCategoryLabelRes(category: String): Int? =
    when (category) {
        "sponsor" -> R.string.sb_category_sponsor
        "selfpromo" -> R.string.sb_category_selfpromo
        "interaction" -> R.string.sb_category_interaction
        "intro" -> R.string.sb_category_intro
        "outro" -> R.string.sb_category_outro
        "music_offtopic" -> R.string.sb_category_music_offtopic
        "filler" -> R.string.sb_category_filler
        "preview" -> R.string.sb_category_preview
        "exclusive_access" -> R.string.sb_category_exclusive_access
        else -> null
    }

/**
 * Overlay button that lets the user manually skip a SponsorBlock segment.
 */
@Composable
fun SponsorBlockSkipButton(
    sponsorSegments: List<SponsorBlockSegment>,
    currentPositionMs: Long,
    categoryActions: Map<String, SponsorBlockAction>,
    controlsVisible: Boolean,
    playbackEnded: Boolean,
    onSkipClick: (endPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var skippedUuids by remember(sponsorSegments) { mutableStateOf(emptySet<String>()) }

    val activeSegment =
        remember(
            sponsorSegments,
            currentPositionMs,
            skippedUuids,
            categoryActions,
            playbackEnded,
        ) {
            findActiveManualSponsorSegment(
                sponsorSegments = sponsorSegments,
                currentPositionMs = currentPositionMs,
                skippedUuids = skippedUuids,
                categoryActions = categoryActions,
                playbackEnded = playbackEnded,
            )
        }

    var displaySegment by remember { mutableStateOf<SponsorBlockSegment?>(null) }
    LaunchedEffect(activeSegment) {
        if (activeSegment != null) displaySegment = activeSegment
    }

    var isDimmed by remember { mutableStateOf(false) }
    LaunchedEffect(activeSegment?.uuid, controlsVisible) {
        if (activeSegment == null || controlsVisible) {
            isDimmed = false
        } else {
            isDimmed = false
            delay(SB_SKIP_DIM_DELAY_MS)
            isDimmed = true
        }
    }

    val buttonAlpha by animateFloatAsState(
        targetValue = if (isDimmed) SB_SKIP_DIMMED_ALPHA else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "sbSkipAlpha",
    )

    AnimatedVisibility(
        visible = activeSegment != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val seg = displaySegment ?: return@AnimatedVisibility
        val categoryRes = sbCategoryLabelRes(seg.category)
        val skipLabel =
            if (categoryRes != null) {
                stringResource(R.string.sb_skip_segment, stringResource(categoryRes))
            } else {
                stringResource(R.string.sb_manual_skip)
            }
        Surface(
            onClick = {
                skippedUuids = skippedUuids + seg.uuid
                onSkipClick((seg.endTime * 1000L).toLong())
            },
            color = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            tonalElevation = 0.dp,
            modifier = Modifier.alpha(buttonAlpha),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = skipLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

internal fun findActiveManualSponsorSegment(
    sponsorSegments: List<SponsorBlockSegment>,
    currentPositionMs: Long,
    skippedUuids: Set<String>,
    categoryActions: Map<String, SponsorBlockAction>,
    playbackEnded: Boolean,
): SponsorBlockSegment? {
    if (playbackEnded) return null

    val positionSeconds = currentPositionMs / 1000f
    return sponsorSegments.find { segment ->
        positionSeconds >= segment.startTime &&
            positionSeconds < segment.endTime &&
            segment.uuid !in skippedUuids &&
            (categoryActions[segment.category] ?: SponsorBlockAction.SKIP) != SponsorBlockAction.SKIP
    }
}
