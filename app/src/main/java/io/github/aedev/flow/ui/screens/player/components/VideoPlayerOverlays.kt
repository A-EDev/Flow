package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
                modifier = Modifier.align(Alignment.Center)
            )

            BrightnessOverlay(
                isVisible = screenState.showBrightnessOverlay,
                brightnessLevel = screenState.brightnessLevel,
                modifier = Modifier
                    .align(Alignment.Center)
            )

            VolumeOverlay(
                isVisible = screenState.showVolumeOverlay,
                volumeLevel = screenState.volumeLevel,
                maxVolumeLevel = if (allowVolumeBoost) 2f else 1f,
                modifier = Modifier
                    .align(Alignment.Center)
            )

            SpeedBoostOverlay(
                isVisible = screenState.isSpeedBoostActive,
                speed = speedBoostSpeed,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun SeekAnimationOverlay(
    showSeekBack: Boolean,
    showSeekForward: Boolean,
    seekSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    // Force LTR so CenterStart/CenterEnd always map to physical left/right,
    // regardless of the device's system language direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showSeekBack,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to forward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekForward) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
            ) {
                SeekChevronLabel(forward = false, seconds = seekSeconds)
            }

            AnimatedVisibility(
                visible = showSeekForward,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to backward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekBack) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
            ) {
                SeekChevronLabel(forward = true, seconds = seekSeconds)
            }
        }
    }
}

@Composable
private fun SeekChevronLabel(forward: Boolean, seconds: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "chevron")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevronProgress"
    )

    val offsetProgress = LinearOutSlowInEasing.transform(progress)
    val chevronOffset = if (forward) 24f * offsetProgress else -24f * offsetProgress
    
    val chevronAlpha = when {
        progress < 0.2f -> progress * 5f
        progress > 0.5f -> (1f - progress) * 2f
        else -> 1f
    }.coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!forward) {
            Text(
                text = "<",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp)
            )
        }
        Text(
            text = if (forward) "+${seconds}s" else "-${seconds}s",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (forward) {
            Text(
                text = ">",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp)
            )
        }
    }
}

@Composable
fun BrightnessOverlay(
    isVisible: Boolean,
    brightnessLevel: Float,
    modifier: Modifier = Modifier
) {
    val isAuto = brightnessLevel < 0f
    val animatedBrightness by animateFloatAsState(
        targetValue = if (isAuto) 0f else brightnessLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "brightness"
    )
    val iconVector = if (isAuto) {
        Icons.Rounded.BrightnessAuto
    } else if (brightnessLevel > 0.7f) Icons.Rounded.BrightnessHigh
    else if (brightnessLevel > 0.3f) Icons.Rounded.BrightnessMedium
    else Icons.Rounded.BrightnessLow

    CircularGestureLevelOverlay(
        isVisible = isVisible,
        icon = iconVector,
        valueLabel = if (isAuto) "Auto" else "${(brightnessLevel.coerceIn(0f, 1f) * 100).toInt()}%",
        progress = animatedBrightness,
        indicatorColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun VolumeOverlay(
    isVisible: Boolean,
    volumeLevel: Float,
    maxVolumeLevel: Float = 2f,
    modifier: Modifier = Modifier
) {
    val animatedVolume by animateFloatAsState(
        targetValue = volumeLevel.coerceIn(0f, maxVolumeLevel.coerceAtLeast(1f)),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "volume"
    )
    val fillFraction = (animatedVolume / maxVolumeLevel.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val iconVector = if (volumeLevel > 1.0f) Icons.AutoMirrored.Rounded.VolumeUp
    else if (volumeLevel > 0.6f) Icons.AutoMirrored.Rounded.VolumeUp
    else if (volumeLevel > 0.1f) Icons.AutoMirrored.Rounded.VolumeDown
    else Icons.AutoMirrored.Rounded.VolumeMute
    val indicatorColor = if (volumeLevel > 1f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    CircularGestureLevelOverlay(
        isVisible = isVisible,
        icon = iconVector,
        valueLabel = "${(volumeLevel * 100).toInt()}%",
        progress = fillFraction,
        indicatorColor = indicatorColor,
        modifier = modifier
    )
}

@Composable
private fun CircularGestureLevelOverlay(
    isVisible: Boolean,
    icon: ImageVector,
    valueLabel: String,
    progress: Float,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(120)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialScale = 0.86f
        ),
        exit = fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.92f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(148.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = indicatorColor,
                    strokeWidth = 8.dp,
                    trackColor = Color.Black.copy(alpha = 0.42f),
                    strokeCap = StrokeCap.Round
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.54f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.54f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SpeedBoostOverlay(
    isVisible: Boolean,
    speed: Float = 2.0f,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = VideoPlayerUtils.formatSpeedLabel(speed, maxSpeed = 4.0f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private const val SB_SKIP_DIM_DELAY_MS = 5_000L
private const val SB_SKIP_DIMMED_ALPHA = 0.45f

private fun sbCategoryLabelRes(category: String): Int? = when (category) {
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
    modifier: Modifier = Modifier
) {
    var skippedUuids by remember(sponsorSegments) { mutableStateOf(emptySet<String>()) }

    val activeSegment = remember(
        sponsorSegments,
        currentPositionMs,
        skippedUuids,
        categoryActions,
        playbackEnded
    ) {
        findActiveManualSponsorSegment(
            sponsorSegments = sponsorSegments,
            currentPositionMs = currentPositionMs,
            skippedUuids = skippedUuids,
            categoryActions = categoryActions,
            playbackEnded = playbackEnded
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
        label = "sbSkipAlpha"
    )

    AnimatedVisibility(
        visible = activeSegment != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        val seg = displaySegment ?: return@AnimatedVisibility
        val categoryRes = sbCategoryLabelRes(seg.category)
        val skipLabel = if (categoryRes != null) {
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
            modifier = Modifier.alpha(buttonAlpha)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = skipLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
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
    playbackEnded: Boolean
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
