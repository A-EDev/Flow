package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerState
import io.github.aedev.flow.player.QualityOption
import io.github.aedev.flow.ui.components.audio.EqualizerEditor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenuDialog(
    playerState: EnhancedPlayerState,
    autoplayEnabled: Boolean,
    subtitlesEnabled: Boolean,
    onDismiss: () -> Unit,
    initialPage: PlayerSettingsPage = PlayerSettingsPage.Main,
    onQualitySelected: (QualityOption) -> Unit = {},
    onAudioTrackSelected: (Int) -> Unit = {},
    onSpeedSelected: (Float) -> Unit = {},
    selectedSubtitleUrl: String? = null,
    onSubtitleSelected: (Int) -> Unit = {},
    onDisableSubtitles: () -> Unit = {},
    onAutoplayToggle: (Boolean) -> Unit,
    onSkipSilenceToggle: (Boolean) -> Unit,
    onStableVolumeToggle: (Boolean) -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    ambientModeEnabled: Boolean = false,
    onAmbientModeToggle: (Boolean) -> Unit = {},
    onCastClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    useGroupedQualitySelector: Boolean = false,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(initialPage) }
    val sheetProgress =
        if (expandedHeightPx > 0f) {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        } else {
            0f
        }
    SideEffect {
        onSheetProgressChange(sheetProgress)
    }
    val currentTitle =
        when (currentPage) {
            PlayerSettingsPage.Main -> stringResource(R.string.player_settings)
            PlayerSettingsPage.Quality -> stringResource(R.string.video_quality_title)
            PlayerSettingsPage.Speed -> stringResource(R.string.playback_speed)
            PlayerSettingsPage.Audio -> stringResource(R.string.audio_track)
            PlayerSettingsPage.Subtitles -> stringResource(R.string.filter_subtitles)
            PlayerSettingsPage.Equalizer -> stringResource(R.string.equalizer)
        }

    fun animateToExpanded() {
        if (!enableVerticalDismiss) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
        }
    }

    fun animateToDismiss(afterDismiss: () -> Unit = {}) {
        if (isAnimatingOut) return
        if (!enableVerticalDismiss) {
            latestOnDismiss()
            afterDismiss()
            return
        }
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            latestOnDismiss()
            afterDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx) {
        if (isAnimatingOut) return@LaunchedEffect
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss) {
            sheetHeightPx.snapTo(expandedHeightPx)
            return@LaunchedEffect
        }
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )
    }

    LaunchedEffect(initialPage) {
        currentPage = initialPage
    }

    BackHandler(onBack = {
        if (currentPage == PlayerSettingsPage.Main) {
            animateToDismiss()
        } else {
            currentPage = PlayerSettingsPage.Main
        }
    })

    val headerDragModifier =
        if (enableVerticalDismiss) {
            Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (isAnimatingOut) return@detectVerticalDragGestures
                        velocityTracker.addPointerInputChange(change)
                        coroutineScope.launch {
                            val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                            sheetHeightPx.snapTo(nextValue)
                        }
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        if (!isAnimatingOut) animateToExpanded()
                    },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        velocityTracker.resetTracking()
                        when {
                            velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                            else -> animateToExpanded()
                        }
                    },
                )
            }
        } else {
            Modifier
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            ) {
                // ── Sheet title ──
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(headerDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentPage != PlayerSettingsPage.Main) {
                        IconButton(
                            onClick = { currentPage = PlayerSettingsPage.Main },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { animateToDismiss() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                ) {
                    when (currentPage) {
                        PlayerSettingsPage.Main -> {
                            PlayerSettingsMainPage(
                                playerState = playerState,
                                autoplayEnabled = autoplayEnabled,
                                subtitlesEnabled = subtitlesEnabled,
                                ambientModeEnabled = ambientModeEnabled,
                                onNavigateToPage = { currentPage = it },
                                onShowSubtitleStyle = onShowSubtitleStyle,
                                onCastClick = { animateToDismiss(onCastClick) },
                                onPipClick = { animateToDismiss(onPipClick) },
                                onSleepTimerClick = { animateToDismiss(onSleepTimerClick) },
                                onLoopToggle = onLoopToggle,
                                onAutoplayToggle = onAutoplayToggle,
                                onSkipSilenceToggle = onSkipSilenceToggle,
                                onStableVolumeToggle = onStableVolumeToggle,
                                onAmbientModeToggle = onAmbientModeToggle,
                            )
                        }

                        PlayerSettingsPage.Quality -> {
                            PlayerSettingsQualityPage(
                                availableQualities = playerState.availableQualities,
                                currentQuality = playerState.currentQuality,
                                currentQualityKey = playerState.currentQualityKey,
                                useGroupedQualitySelector = useGroupedQualitySelector,
                                onQualitySelected = {
                                    onQualitySelected(it)
                                    animateToDismiss()
                                },
                            )
                        }

                        PlayerSettingsPage.Speed -> {
                            PlayerSettingsSpeedPage(
                                currentSpeed = playerState.playbackSpeed,
                                onSpeedSelected = onSpeedSelected,
                                onSpeedSelectionFinished = { animateToDismiss() },
                            )
                        }

                        PlayerSettingsPage.Audio -> {
                            PlayerSettingsAudioPage(
                                availableAudioTracks = playerState.availableAudioTracks,
                                currentAudioTrack = playerState.currentAudioTrack,
                                onTrackSelected = {
                                    onAudioTrackSelected(it)
                                    animateToDismiss()
                                },
                            )
                        }

                        PlayerSettingsPage.Equalizer -> {
                            EqualizerEditor(
                                modifier =
                                    Modifier
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 8.dp, bottom = 16.dp),
                            )
                        }

                        PlayerSettingsPage.Subtitles -> {
                            PlayerSettingsSubtitlesPage(
                                availableSubtitles = playerState.availableSubtitles,
                                selectedSubtitleUrl = selectedSubtitleUrl,
                                subtitlesEnabled = subtitlesEnabled,
                                onSubtitleSelected = { index ->
                                    onSubtitleSelected(index)
                                    animateToDismiss()
                                },
                                onDisableSubtitles = {
                                    onDisableSubtitles()
                                    animateToDismiss()
                                },
                                onShowStyleCustomizer = {
                                    currentPage = PlayerSettingsPage.Main
                                    animateToDismiss(onShowSubtitleStyle)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class PlayerSettingsPage {
    Main,
    Quality,
    Speed,
    Audio,
    Subtitles,
    Equalizer,
}
