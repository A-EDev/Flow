package io.github.aedev.flow.ui.screens.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.*
import io.github.aedev.flow.player.stream.VideoCodecUtils
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPEED_SELECTION_DISMISS_DELAY_MS = 1_500L

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
    onSubtitleSelected: (Int, String) -> Unit = { _, _ -> },
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
    modifier: Modifier = Modifier
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
    var speedDismissJob by remember { mutableStateOf<Job?>(null) }
    val sheetProgress = if (expandedHeightPx > 0f) {
        ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    SideEffect {
        onSheetProgressChange(sheetProgress)
    }
    val currentTitle = when (currentPage) {
        PlayerSettingsPage.Main -> stringResource(R.string.player_settings)
        PlayerSettingsPage.Quality -> stringResource(R.string.video_quality_title)
        PlayerSettingsPage.Speed -> stringResource(R.string.playback_speed)
        PlayerSettingsPage.Audio -> stringResource(R.string.audio_track)
        PlayerSettingsPage.Subtitles -> stringResource(R.string.filter_subtitles)
    }

    fun animateToExpanded() {
        if (!enableVerticalDismiss) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    fun animateToDismiss(afterDismiss: () -> Unit = {}) {
        if (isAnimatingOut) return
        speedDismissJob?.cancel()
        speedDismissJob = null
        if (!enableVerticalDismiss) {
            latestOnDismiss()
            afterDismiss()
            return
        }
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            latestOnDismiss()
            afterDismiss()
        }
    }

    fun scheduleSpeedDismiss() {
        speedDismissJob?.cancel()
        speedDismissJob = coroutineScope.launch {
            delay(SPEED_SELECTION_DISMISS_DELAY_MS)
            speedDismissJob = null
            animateToDismiss()
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
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
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

    val headerDragModifier = if (enableVerticalDismiss) Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
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
            }
        )
    } else Modifier

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Sheet title ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(headerDragModifier),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(headerDragModifier)
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage != PlayerSettingsPage.Main) {
                    IconButton(
                        onClick = { currentPage = PlayerSettingsPage.Main },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { animateToDismiss() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

            // ── Quality ──
            when (currentPage) {
                PlayerSettingsPage.Main -> {
            PlayerSettingsSectionHeader(stringResource(R.string.video))
            PlayerSettingsNavRow(
                icon = Icons.Filled.HighQuality,
                label = stringResource(R.string.quality),
                value = if (playerState.currentQuality == 0) stringResource(R.string.quality_auto)
                        else "${playerState.currentQuality}p",
                onClick = {
                    currentPage = PlayerSettingsPage.Quality
                }
            )

            // ── Playback Speed ──
            PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.playback_speed),
                value = if (playerState.playbackSpeed == 1.0f) stringResource(R.string.normal)
                        else "${playerState.playbackSpeed}x",
                onClick = {
                    currentPage = PlayerSettingsPage.Speed
                }
            )

            // ── Audio Track ──
            PlayerSettingsSectionHeader(stringResource(R.string.audio_settings_title))
            PlayerSettingsNavRow(
                icon = Icons.Filled.AudioFile,
                label = stringResource(R.string.audio_track),
                value = audioTrackDisplayLabel(
                    playerState.availableAudioTracks.getOrNull(playerState.currentAudioTrack),
                    playerState.currentAudioTrack
                ),
                onClick = {
                    currentPage = PlayerSettingsPage.Audio
                }
            )

            // ── Captions ──
            PlayerSettingsSectionHeader(stringResource(R.string.captions))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Subtitles,
                label = stringResource(R.string.filter_subtitles),
                value = if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off),
                onClick = {
                    currentPage = PlayerSettingsPage.Subtitles
                }
            )

            PlayerSettingsNavRow(
                icon = Icons.Filled.Tune,
                label = stringResource(R.string.subtitle_style),
                value = "",
                onClick = { onShowSubtitleStyle() }
            )

            // ── Cast to TV ──
            PlayerSettingsSectionHeader(stringResource(R.string.player_settings_overlay_controls))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Cast,
                label = stringResource(R.string.cast_to_tv),
                value = "",
                onClick = { animateToDismiss(onCastClick) }
            )

            // ── Picture-in-Picture ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.PictureInPicture,
                label = stringResource(R.string.pip_mode),
                value = "",
                onClick = { animateToDismiss(onPipClick) }
            )

            // ── Sleep Timer ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.Bedtime,
                label = stringResource(R.string.sleep_timer),
                value = "",
                onClick = { animateToDismiss(onSleepTimerClick) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── Loop Video ──
            PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.Repeat,
                label = stringResource(R.string.loop_video),
                checked = playerState.isLooping,
                onToggle = onLoopToggle
            )

            // ── Autoplay ──
            PlayerSettingsToggleRow(
                icon = Icons.Filled.SkipNext,
                label = stringResource(R.string.autoplay_next),
                checked = autoplayEnabled,
                enabled = !playerState.isLooping,
                onToggle = onAutoplayToggle
            )

            // ── Skip Silence ──
            PlayerSettingsSectionHeader(stringResource(R.string.audio_effects))
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.player_settings_skip_silence),
                checked = playerState.isSkipSilenceEnabled,
                onToggle = onSkipSilenceToggle
            )

            // ── Stable Voice ──
            PlayerSettingsToggleRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = stringResource(R.string.player_settings_stable_voice),
                checked = playerState.isStableVolumeEnabled,
                onToggle = onStableVolumeToggle
            )

            // ── Ambient Mode ──
            PlayerSettingsSectionHeader(stringResource(R.string.player_settings_display))
            PlayerSettingsToggleRow(
                icon = ImageVector.vectorResource(R.drawable.ic_ambient_mode),
                label = stringResource(R.string.player_settings_ambient_mode),
                checked = ambientModeEnabled,
                onToggle = onAmbientModeToggle
            )
                }
                PlayerSettingsPage.Quality -> PlayerSettingsQualityPage(
                    availableQualities = playerState.availableQualities,
                    currentQuality = playerState.currentQuality,
                    currentQualityKey = playerState.currentQualityKey,
                    useGroupedQualitySelector = useGroupedQualitySelector,
                    onQualitySelected = {
                        onQualitySelected(it)
                        animateToDismiss()
                    }
                )
                PlayerSettingsPage.Speed -> PlayerSettingsSpeedPage(
                    currentSpeed = playerState.playbackSpeed,
                    onSpeedSelected = onSpeedSelected,
                    onSpeedSelectionFinished = ::scheduleSpeedDismiss
                )
                PlayerSettingsPage.Audio -> PlayerSettingsAudioPage(
                    availableAudioTracks = playerState.availableAudioTracks,
                    currentAudioTrack = playerState.currentAudioTrack,
                    onTrackSelected = {
                        onAudioTrackSelected(it)
                        animateToDismiss()
                    }
                )
                PlayerSettingsPage.Subtitles -> PlayerSettingsSubtitlesPage(
                    availableSubtitles = playerState.availableSubtitles,
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    subtitlesEnabled = subtitlesEnabled,
                    onSubtitleSelected = { index, url ->
                        onSubtitleSelected(index, url)
                        animateToDismiss()
                    },
                    onDisableSubtitles = {
                        onDisableSubtitles()
                        animateToDismiss()
                    },
                    onShowStyleCustomizer = {
                        currentPage = PlayerSettingsPage.Main
                        animateToDismiss(onShowSubtitleStyle)
                    }
                )
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
    Subtitles
}

@Composable
private fun PlayerSettingsQualityPage(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    currentQualityKey: String?,
    useGroupedQualitySelector: Boolean,
    onQualitySelected: (QualityOption) -> Unit
) {
    val autoLabel = stringResource(R.string.quality_auto)
    val selectorOptions = availableQualities.map { quality ->
        PlayerQualitySelectorOption(
            item = quality,
            height = quality.height,
            label = if (quality.height == 0) autoLabel else quality.displayLabel(),
            codecKey = quality.codecKey,
            codecLabel = quality.codecKey
                .takeIf { it.isNotBlank() }
                ?.let(VideoCodecUtils::codecLabelFromKey)
                .orEmpty(),
            streamKey = quality.streamKey,
            selected = quality.isSelected(currentQuality, currentQualityKey)
        )
    }

    PlayerQualitySelectorContent(
        options = selectorOptions,
        groupedByResolution = useGroupedQualitySelector,
        onOptionSelected = onQualitySelected
    )
}

data class PlayerQualitySelectorOption<T>(
    val item: T,
    val height: Int,
    val label: String,
    val selected: Boolean,
    val supportingText: String? = null,
    val codecKey: String = "",
    val codecLabel: String = "",
    val streamKey: String? = null
)

@Composable
fun <T> PlayerQualitySelectorContent(
    options: List<PlayerQualitySelectorOption<T>>,
    groupedByResolution: Boolean,
    onOptionSelected: (T) -> Unit
) {
    if (!groupedByResolution) {
        options
            .sortedByDescending { it.height }
            .forEach { option ->
                PlayerSettingsSelectionRow(
                    label = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) }
                )
            }
        return
    }

    options.firstOrNull { it.height == 0 }?.let { auto ->
        PlayerSettingsSelectionRow(
            label = auto.label,
            selected = auto.selected,
            onClick = { onOptionSelected(auto.item) }
        )
    }
    options
        .filter { it.height != 0 }
        .groupBy { it.height }
        .entries
        .sortedByDescending { it.key }
        .forEach { (_, options) ->
            val codecOptions = options.filter { it.codecKey.isNotBlank() || it.codecLabel.isNotBlank() }
            if (codecOptions.isEmpty()) {
                val option = options.first()
                PlayerSettingsSelectionRow(
                    label = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) }
                )
            } else {
                PlayerSettingsQualityCodecRow(
                    qualityLabel = options.first().resolutionLabel(),
                    codecOptions = codecOptions,
                    onOptionSelected = onOptionSelected
                )
            }
        }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> PlayerSettingsQualityCodecRow(
    qualityLabel: String,
    codecOptions: List<PlayerQualitySelectorOption<T>>,
    onOptionSelected: (T) -> Unit
) {
    val rowSelected = codecOptions.any { it.selected }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = qualityLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (rowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(84.dp)
        )
        Spacer(Modifier.width(12.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            codecOptions.forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) },
                    label = { Text(option.codecLabel.ifBlank { option.label }) },
                    leadingIcon = if (option.selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

private fun QualityOption.displayLabel(): String {
    return label.takeIf { it.isNotBlank() } ?: "${height}p"
}

private fun PlayerQualitySelectorOption<*>.resolutionLabel(): String {
    return if (codecLabel.isNotBlank() && label.endsWith(" $codecLabel")) {
        label.removeSuffix(" $codecLabel")
    } else {
        label
    }
}

private fun QualityOption.isSelected(currentQuality: Int, currentQualityKey: String?): Boolean {
    return if (height == 0) {
        currentQuality == 0
    } else {
        streamKey != null && streamKey == currentQualityKey
    }
}

@Composable
private fun PlayerSettingsSpeedPage(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: () -> Unit
) {
    val context = LocalContext.current
    val playerPrefs = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val customSpeedsEnabled by playerPrefs.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPrefs.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPrefs.speedSliderEnabled.collectAsState(initial = false)
    val defaultSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
    val speeds = if (customSpeedsEnabled && customSpeedPresetsRaw.isNotBlank()) {
        customSpeedPresetsRaw
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in 0.1f..10.0f }
            .sortedBy { it }
            .ifEmpty { defaultSpeeds }
    } else {
        defaultSpeeds
    }

    if (speedSliderEnabled) {
        val sliderPresets = if (customSpeedsEnabled) {
            speeds.filter { it in 0.1f..4.0f }
        } else {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        }
        SpeedSliderContent(
            currentSpeed = currentSpeed,
            quickPresets = sliderPresets,
            onSpeedSelected = onSpeedSelected,
            onSpeedSelectionFinished = onSpeedSelectionFinished
        )
    } else {
        speeds.forEach { speed ->
            PlayerSettingsSelectionRow(
                label = if (speed == 1.0f) stringResource(R.string.normal) else "${speed}x",
                selected = speed == currentSpeed,
                onClick = {
                    onSpeedSelected(speed)
                    onSpeedSelectionFinished()
                }
            )
        }
    }
}

@Composable
private fun PlayerSettingsAudioPage(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onTrackSelected: (Int) -> Unit
) {
    availableAudioTracks.forEachIndexed { index, track ->
        PlayerSettingsSelectionRow(
            label = audioTrackDisplayLabel(track, index),
            supportingText = track.language.takeIf { it.isNotBlank() },
            selected = index == currentAudioTrack,
            showSelectedContainer = false,
            onClick = { onTrackSelected(index) }
        )
    }
}

@Composable
private fun PlayerSettingsSubtitlesPage(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onSubtitleSelected: (Int, String) -> Unit,
    onDisableSubtitles: () -> Unit,
    onShowStyleCustomizer: () -> Unit
) {
    val automaticLabel = stringResource(R.string.quality_auto)
    PlayerSettingsSelectionRow(
        label = stringResource(R.string.off),
        selected = !subtitlesEnabled,
        onClick = onDisableSubtitles
    )
    availableSubtitles.forEachIndexed { index, subtitle ->
        PlayerSettingsSelectionRow(
            label = if (subtitle.isAutoGenerated) {
                stringResource(
                    R.string.subtitle_auto_generated_template,
                    subtitle.label,
                    automaticLabel
                )
            } else {
                subtitle.label
            },
            supportingText = subtitle.language.takeIf { it.isNotBlank() },
            selected = subtitle.url == selectedSubtitleUrl && subtitlesEnabled,
            onClick = { onSubtitleSelected(index, subtitle.url) }
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    PlayerSettingsNavRow(
        icon = Icons.Filled.Tune,
        label = stringResource(R.string.subtitle_style),
        value = "",
        onClick = onShowStyleCustomizer
    )
}

@Composable
private fun audioTrackDisplayLabel(track: AudioTrackOption?, fallbackIndex: Int): String =
    track?.label?.takeIf { it.isNotBlank() }
        ?: stringResource(
            R.string.audio_track_number_template,
            stringResource(R.string.audio_track),
            fallbackIndex + 1
        )

@Composable
private fun PlayerSettingsSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    showSelectedContainer: Boolean = true
) {
    Surface(
        onClick = onClick,
        color = if (selected && showSelectedContainer) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun PlayerSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun PlayerSettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerSettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { if (enabled) onToggle(!checked) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null
            )
        }
    }
}

/**
 * Quadratic slider mapping: provides finer control near 1.0x and coarser at extremes.
 */
private fun sliderToSpeed(progress: Float): Float {
    val center = 0.5f
    return if (progress <= center) {
        val t = progress / center          
        0.1f + t * t * (1.0f - 0.1f)
    } else {
        val t = (progress - center) / center
        1.0f + t * t * (4.0f - 1.0f)
    }
}

private fun speedToSlider(speed: Float): Float {
    val clamped = speed.coerceIn(0.1f, 4.0f)
    return if (clamped <= 1.0f) {
        val t = kotlin.math.sqrt((clamped - 0.1f) / (1.0f - 0.1f))
        t * 0.5f
    } else {
        val t = kotlin.math.sqrt((clamped - 1.0f) / (4.0f - 1.0f))
        0.5f + t * 0.5f
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpeedSliderContent(
    currentSpeed: Float,
    quickPresets: List<Float>,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: () -> Unit
) {
    val step = 0.05f
    var sliderProgress by remember { mutableStateOf(speedToSlider(currentSpeed)) }
    var lastAppliedSpeed by remember { mutableFloatStateOf(currentSpeed) }
    val displaySpeed = sliderToSpeed(sliderProgress)
    val roundedSpeed = (kotlin.math.round(displaySpeed.toDouble() / step) * step)
        .toFloat().coerceIn(0.1f, 4.0f)

    LaunchedEffect(currentSpeed) {
        if (kotlin.math.abs(lastAppliedSpeed - currentSpeed) >= 0.001f) {
            lastAppliedSpeed = currentSpeed
            sliderProgress = speedToSlider(currentSpeed)
        }
    }

    fun selectSpeed(speed: Float) {
        val selectedSpeed = speed.coerceIn(0.1f, 4.0f)
        sliderProgress = speedToSlider(selectedSpeed)
        if (kotlin.math.abs(lastAppliedSpeed - selectedSpeed) >= 0.001f) {
            lastAppliedSpeed = selectedSpeed
            onSpeedSelected(selectedSpeed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current speed display
        Text(
            text = if (roundedSpeed == 1.0f) stringResource(R.string.normal)
                   else "${"%.2f".format(roundedSpeed)}×",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        // Slider with − / + buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    val newSpeed = (roundedSpeed - step).coerceIn(0.1f, 4.0f)
                    selectSpeed(newSpeed)
                    onSpeedSelectionFinished()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.speed_slider_decrease)
                )
            }
            Slider(
                value = sliderProgress,
                onValueChange = { progress ->
                    sliderProgress = progress
                    val speed = (kotlin.math.round(sliderToSpeed(progress).toDouble() / step) * step)
                        .toFloat().coerceIn(0.1f, 4.0f)
                    if (kotlin.math.abs(lastAppliedSpeed - speed) >= 0.001f) {
                        lastAppliedSpeed = speed
                        onSpeedSelected(speed)
                    }
                },
                onValueChangeFinished = onSpeedSelectionFinished,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val newSpeed = (roundedSpeed + step).coerceIn(0.1f, 4.0f)
                    selectSpeed(newSpeed)
                    onSpeedSelectionFinished()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.speed_slider_increase)
                )
            }
        }

        // Range labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0.1×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "4.0×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // Quick-tap preset chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            quickPresets.forEach { preset ->
                val isActive = kotlin.math.abs(roundedSpeed - preset) < 0.01f
                FilterChip(
                    selected = isActive,
                    onClick = {
                        selectSpeed(preset)
                        onSpeedSelectionFinished()
                    },
                    label = {
                        Text(
                            text = if (preset == 1.0f) stringResource(R.string.normal)
                                   else "${preset}×"
                        )
                    }
                )
            }
        }

    }
}
