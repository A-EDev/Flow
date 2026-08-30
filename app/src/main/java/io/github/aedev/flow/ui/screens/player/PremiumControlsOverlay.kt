package io.github.aedev.flow.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerOverlayPreferences
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.ui.screens.player.components.PortraitFullscreenEdgeScrims
import io.github.aedev.flow.ui.screens.player.controls.PlayerBottomBar
import io.github.aedev.flow.ui.screens.player.controls.PlayerBottomBarMetrics
import io.github.aedev.flow.ui.screens.player.controls.PlayerControlActions
import io.github.aedev.flow.ui.screens.player.controls.PlayerLockedControls
import io.github.aedev.flow.ui.screens.player.controls.PlayerSeekbarContent
import io.github.aedev.flow.ui.screens.player.controls.PlayerSeekbarRow
import io.github.aedev.flow.ui.screens.player.controls.PlayerTopBar
import io.github.aedev.flow.ui.screens.player.controls.PlayerTransportControls
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs

private const val LIVE_SCRUB_SEEK_INTERVAL_MS = 80L
private const val LIVE_SCRUB_IMMEDIATE_DELTA_MS = 750L
private val OverlayActionButtonSize = 48.dp
private val OverlayActionIconSize = 24.dp
private val OverlayActionSpacing = 8.dp
private val OverlayPillHeight = 28.dp
private val OverlayExpandIconSize = 18.dp
private val OverlayControlRowMinHeight = 48.dp
private val OverlayActionIconInset = (OverlayActionButtonSize - OverlayActionIconSize) / 2f

// How long the lock-mode unlock affordance stays on screen before it auto-hides
// for a clean, unobstructed locked view. A single tap re-reveals it (see issue #619).
private const val LOCKED_OVERLAY_AUTO_HIDE_MS = 3_000L

@Composable
fun PremiumControlsOverlay(
    isVisible: Boolean,
    isPlaying: Boolean,
    hasEnded: Boolean,
    isBuffering: Boolean,
    currentPosition: () -> Long,
    duration: Long,
    qualityLabel: String?,
    videoTitle: String?,
    playbackSpeed: Float = 1.0f,
    resizeMode: Int,
    onResizeClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onFullscreenClick: () -> Unit,
    isFullscreen: Boolean,
    isPipSupported: Boolean = false,
    onPipClick: () -> Unit = {},
    chapters: List<StreamSegment> = emptyList(),
    onChapterClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onSubtitleLongClick: () -> Unit = {},
    isSubtitlesEnabled: Boolean = false,
    autoplayEnabled: Boolean = true,
    isLooping: Boolean = false,
    onAutoplayToggle: (Boolean) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    bufferedPercentage: Float = 0f,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    sbSubmitEnabled: Boolean = false,
    onSbSubmitClick: () -> Unit = {},
    // Cast / Chromecast support
    onCastClick: () -> Unit = {},
    isCasting: Boolean = false,
    isLive: Boolean = false,
    onLiveClick: () -> Unit = {},
    isLiveChatAvailable: Boolean = false,
    onLiveChatClick: () -> Unit = {},
    isCommentsAvailable: Boolean = false,
    isCommentsPanelOpen: Boolean = false,
    onCommentsClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {},
    isTouchLocked: Boolean = false,
    lockModeEnabled: Boolean = false,
    lockOverlayRevealSignal: Int = 0,
    onTouchLockToggle: () -> Unit = {},
    onScrubbingChange: (Boolean) -> Unit = {},
    isPortraitFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val livePosition by rememberUpdatedState(currentPosition)
    val reduceMotion = rememberFlowReduceMotion()

    val primaryColor = MaterialTheme.colorScheme.primary
    val resizeModes =
        listOf(
            stringResource(R.string.resize_fit),
            stringResource(R.string.resize_fill),
            stringResource(R.string.resize_zoom),
        )
    val scrubScope = rememberCoroutineScope()

    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    var lastScrubSeekAt by remember { mutableLongStateOf(0L) }
    var lastScrubSeekPosition by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var pendingScrubSeekJob by remember { mutableStateOf<Job?>(null) }

    val displayedPosition: () -> Long = { scrubPosition ?: livePosition() }

    // The expanded seek bar and the thin always-visible one drive the same scrub, so the throttling
    // and hand-off logic lives here once instead of being copied into both call sites.
    val onScrubProgress: (Float, Long) -> Unit = { progress, seekDuration ->
        val newPosition = (progress * seekDuration).toLong()

        scrubPosition = newPosition

        if (!isScrubbing) {
            isScrubbing = true
            onScrubbingChange(true)
            EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(true)
        }

        // Live scrubbing only previews; the seek itself is issued once the thumb is released.
        if (!isLive) {
            pendingScrubSeekJob?.cancel()

            val now = SystemClock.elapsedRealtime()
            val remainingDelay = (LIVE_SCRUB_SEEK_INTERVAL_MS - (now - lastScrubSeekAt)).coerceAtLeast(0L)
            val movedFarEnough =
                lastScrubSeekPosition == Long.MIN_VALUE ||
                    abs(newPosition - lastScrubSeekPosition) >= LIVE_SCRUB_IMMEDIATE_DELTA_MS

            if (remainingDelay == 0L || movedFarEnough) {
                onSeek(newPosition)
                lastScrubSeekAt = now
                lastScrubSeekPosition = newPosition
            } else {
                pendingScrubSeekJob =
                    scrubScope.launch {
                        delay(remainingDelay)
                        val targetPosition = scrubPosition ?: return@launch
                        onSeek(targetPosition)
                        lastScrubSeekAt = SystemClock.elapsedRealtime()
                        lastScrubSeekPosition = targetPosition
                    }
            }
        }
    }

    val onScrubFinished: () -> Unit = {
        pendingScrubSeekJob?.cancel()
        pendingScrubSeekJob = null
        scrubPosition?.let { targetPosition ->
            onSeek(targetPosition)
            lastScrubSeekPosition = targetPosition
        }
        lastScrubSeekAt = 0L
        lastScrubSeekPosition = Long.MIN_VALUE
        isScrubbing = false
        onScrubbingChange(false)
        EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(false)
    }

    // Lock-mode unlock affordance auto-hide (issue #619). While touch-locked, the
    // unlock button hides itself after a short delay so the locked view is clean,
    // then a single tap anywhere re-reveals it and restarts the timer.
    var isLockOverlayVisible by remember { mutableStateOf(true) }
    // Bumped on every reveal so that re-revealing while already visible still
    // restarts the auto-hide timer (a no-op `isLockOverlayVisible = true` would not).
    var lockOverlayRevealTick by remember { mutableIntStateOf(0) }

    val revealLockOverlay: () -> Unit = {
        isLockOverlayVisible = true
        lockOverlayRevealTick++
    }

    // Reset the unlock affordance to visible whenever lock mode is (re-)entered.
    LaunchedEffect(isTouchLocked, lockOverlayRevealSignal) {
        if (isTouchLocked) {
            revealLockOverlay()
        }
    }

    // Auto-hide the unlock affordance after the delay while it is showing in lock mode.
    // Keyed on the reveal tick so each tap restarts the full delay window.
    LaunchedEffect(isTouchLocked, isLockOverlayVisible, lockOverlayRevealTick) {
        if (isTouchLocked && isLockOverlayVisible) {
            delay(LOCKED_OVERLAY_AUTO_HIDE_MS)
            isLockOverlayVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingScrubSeekJob?.cancel()
            onScrubbingChange(false)
            EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(false)
        }
    }

    val pendingScrubTarget = scrubPosition
    if (pendingScrubTarget != null && !isScrubbing) {
        val settledPosition = livePosition()
        LaunchedEffect(settledPosition, pendingScrubTarget) {
            if (abs(settledPosition - pendingScrubTarget) <= 1_000L) {
                scrubPosition = null
            }
        }
    }

    val currentChapter by remember(chapters) {
        derivedStateOf {
            val positionSeconds = displayedPosition() / 1000
            chapters.lastOrNull { it.startTimeSeconds <= positionSeconds }
        }
    }

    val sponsorSegments by EnhancedPlayerManager.getInstance().sponsorSegments.collectAsState()

    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val overlayPreferences by playerPreferences.overlayPreferences.collectAsState(
        initial = remember { PlayerOverlayPreferences() },
    )
    val sponsorSegmentColors =
        remember(overlayPreferences.sponsorCategoryColors) {
            overlayPreferences.sponsorCategoryColors.mapValues { (_, argb) -> Color(argb) }
        }
    val overlayCastEnabled = overlayPreferences.castEnabled
    val overlayCcEnabled = overlayPreferences.captionsEnabled
    val overlayPipEnabled = overlayPreferences.pipEnabled
    val overlayAutoplayEnabled = overlayPreferences.autoplayEnabled
    val overlaySleepTimerEnabled = overlayPreferences.sleepTimerEnabled
    val overlaySpeedIndicatorEnabled = overlayPreferences.speedIndicatorEnabled
    val overlayCommentsEnabled = overlayPreferences.commentsEnabled
    val showFullscreenTitle = overlayPreferences.fullscreenTitleEnabled
    val fullscreenSeekbarHorizontalPaddingDp = overlayPreferences.fullscreenSeekbarHorizontalPaddingDp
    val portraitSeekbarHorizontalPaddingDp = overlayPreferences.portraitSeekbarHorizontalPaddingDp
    val fullscreenSeekbarBottomPadding = if (isFullscreen) 30.dp else 0.dp
    val bottomControlHorizontalPadding = if (isFullscreen) 56.dp else 12.dp
    val topControlHorizontalPadding = (bottomControlHorizontalPadding - OverlayActionIconInset).coerceAtLeast(0.dp)
    val topControlVerticalPadding = if (isFullscreen) 8.dp else 4.dp
    val portraitFullscreenTopPadding =
        if (isFullscreen && isPortraitFullscreen) {
            WindowInsets.displayCutout
                .asPaddingValues()
                .calculateTopPadding()
                .coerceAtLeast(16.dp)
        } else {
            0.dp
        }
    val bottomControlsSeekbarOverlap = 0.dp
    val seekbarHorizontalPadding =
        if (isFullscreen) {
            fullscreenSeekbarHorizontalPaddingDp.dp
        } else {
            portraitSeekbarHorizontalPaddingDp.dp
        }
    val pillsRowMinHeight = if (isFullscreen) OverlayControlRowMinHeight else 30.dp
    val chapterMaxWidth = if (isFullscreen) 240.dp else 96.dp
    val compactQualityLabel = remember(qualityLabel) { qualityLabel?.toCompactQualityLabel() }
    val speedIndicatorLabel = remember(playbackSpeed) { VideoPlayerUtils.formatSpeedLabel(playbackSpeed) }

    val showControlsWhileLoading = overlayPreferences.showControlsWhileLoading
    val isInitialLoading by remember(isBuffering, duration) {
        derivedStateOf { isBuffering && duration <= 0L && displayedPosition() <= 0L }
    }
    // When the user opts in, keep the controls visible during the initial load so volume/brightness/
    // back/etc. can be used before the first frame arrives.
    val hideControlsForLoading = isInitialLoading && !showControlsWhileLoading

    val seekbarContent =
        remember(chapters, sponsorSegments, sponsorSegmentColors, bufferedPercentage) {
            PlayerSeekbarContent(
                chapters = chapters,
                sponsorSegments = sponsorSegments,
                sponsorColors = sponsorSegmentColors,
                bufferedPercentage = bufferedPercentage,
            )
        }
    val bottomBarMetrics =
        PlayerBottomBarMetrics(
            pillHeight = OverlayPillHeight,
            pillsRowMinHeight = pillsRowMinHeight,
            actionSpacing = OverlayActionSpacing,
            horizontalPadding = bottomControlHorizontalPadding,
            seekbarHorizontalPadding = seekbarHorizontalPadding,
            seekbarBottomPadding = fullscreenSeekbarBottomPadding,
            expandIconSize = OverlayExpandIconSize,
            chapterMaxWidth = chapterMaxWidth,
        )
    val controlActions =
        PlayerControlActions(
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onNext = onNext,
            onBack = onBack,
            onSettingsClick = onSettingsClick,
            onQualityClick = onQualityClick,
            onSpeedClick = onSpeedClick,
            onFullscreenClick = onFullscreenClick,
            onResizeClick = onResizeClick,
            onPipClick = onPipClick,
            onChapterClick = onChapterClick,
            onSubtitleClick = onSubtitleClick,
            onSubtitleLongClick = onSubtitleLongClick,
            onAutoplayToggle = onAutoplayToggle,
            onSbSubmitClick = onSbSubmitClick,
            onCastClick = onCastClick,
            onLiveClick = onLiveClick,
            onLiveChatClick = onLiveChatClick,
            onCommentsClick = onCommentsClick,
            onSleepTimerClick = onSleepTimerClick,
            onToggleRemainingTime = onToggleRemainingTime,
            onTouchLockToggle = onTouchLockToggle,
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets),
    ) {
        if (isFullscreen && isPortraitFullscreen) {
            PortraitFullscreenEdgeScrims(modifier = Modifier.matchParentSize())
        }

        val controlsAlpha = remember { Animatable(if (isVisible) 1f else 0f) }
        var controlsPlaced by remember { mutableStateOf(isVisible) }
        LaunchedEffect(isVisible, reduceMotion) {
            if (isVisible) controlsPlaced = true
            controlsAlpha.animateTo(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec =
                    tween(
                        durationMillis =
                            FlowMotion.durationFor(
                                if (isVisible) FlowMotion.ENTER_DURATION_MILLIS else FlowMotion.EXIT_DURATION_MILLIS,
                                reduceMotion,
                            ),
                        easing = if (isVisible) FlowMotion.EnterEasing else FlowMotion.ExitEasing,
                    ),
            )
            if (!isVisible) controlsPlaced = false
        }

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = controlsAlpha.value }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (controlsPlaced) placeable.place(0, 0)
                        }
                    }.background(
                        when {
                            isTouchLocked -> Color.Transparent
                            isInitialLoading -> PlayerScrim
                            else -> PlayerScrim.copy(alpha = 0.24f)
                        },
                    ),
        ) {
            if (isTouchLocked) {
                PlayerLockedControls(
                    isOverlayVisible = isLockOverlayVisible,
                    positionProvider = displayedPosition,
                    duration = duration,
                    isLive = isLive,
                    isFullscreen = isFullscreen,
                    showRemainingTime = showRemainingTime,
                    seekbarContent = seekbarContent,
                    pillHeight = OverlayPillHeight,
                    topPadding = portraitFullscreenTopPadding,
                    seekbarHorizontalPadding = seekbarHorizontalPadding,
                    seekbarBottomPadding = fullscreenSeekbarBottomPadding,
                    onRevealUnlock = revealLockOverlay,
                    onUnlock = onTouchLockToggle,
                )
            } else {
                if (!hideControlsForLoading) {
                    PlayerTopBar(
                        preferences = overlayPreferences,
                        isFullscreen = isFullscreen,
                        videoTitle = videoTitle,
                        speedIndicatorLabel = speedIndicatorLabel,
                        resizeMode = resizeMode,
                        resizeModeLabels = resizeModes,
                        isPipSupported = isPipSupported,
                        sbSubmitEnabled = sbSubmitEnabled,
                        isCasting = isCasting,
                        isSubtitlesEnabled = isSubtitlesEnabled,
                        isAutoplayOn = autoplayEnabled,
                        isLooping = isLooping,
                        isSleepTimerActive = isSleepTimerActive,
                        lockModeEnabled = lockModeEnabled,
                        isLiveChatAvailable = isLiveChatAvailable,
                        topPadding = portraitFullscreenTopPadding,
                        horizontalPadding = topControlHorizontalPadding,
                        verticalPadding = topControlVerticalPadding,
                        rowMinHeight = OverlayControlRowMinHeight,
                        pillHeight = OverlayPillHeight,
                        actionButtonSize = OverlayActionButtonSize,
                        actionIconSize = OverlayActionIconSize,
                        actionSpacing = OverlayActionSpacing,
                        actions = controlActions,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }

                PlayerTransportControls(
                    isPlaying = isPlaying,
                    hasEnded = hasEnded,
                    showBufferingSpinner = (isBuffering || isInitialLoading) && !isScrubbing,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    showSkipButtons = !hideControlsForLoading,
                    actions = controlActions,
                    modifier = Modifier.align(Alignment.Center),
                )

                if (!hideControlsForLoading) {
                    PlayerBottomBar(
                        positionProvider = displayedPosition,
                        duration = duration,
                        isLive = isLive,
                        isFullscreen = isFullscreen,
                        showRemainingTime = showRemainingTime,
                        showCommentsButton = overlayCommentsEnabled && isCommentsAvailable && isFullscreen,
                        isCommentsPanelOpen = isCommentsPanelOpen,
                        currentChapter = currentChapter,
                        compactQualityLabel = compactQualityLabel,
                        seekbarContent = seekbarContent,
                        metrics = bottomBarMetrics,
                        actions = controlActions,
                        onScrubProgress = onScrubProgress,
                        onScrubFinished = onScrubFinished,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isVisible && !isFullscreen && !isInitialLoading && !isTouchLocked,
            enter =
                fadeIn(
                    tween(
                        FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
                ),
            exit =
                fadeOut(
                    tween(
                        FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
                ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PlayerSeekbarRow(
                    positionProvider = displayedPosition,
                    duration = duration,
                    isLive = isLive,
                    content = seekbarContent,
                    edgeAligned = true,
                    horizontalPadding = seekbarHorizontalPadding,
                    onScrubProgress = onScrubProgress,
                    onScrubFinished = onScrubFinished,
                )
            }
        }
    }
}

@Composable
fun SleekLoadingAnimation(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        strokeWidth = 4.dp,
        strokeCap = StrokeCap.Round,
    )
}

private fun String.toCompactQualityLabel(): String {
    val height =
        Regex("""\d+""")
            .find(this)
            ?.value
            ?.toIntOrNull()
            ?.let(QualityManager::normalizeQualityHeight)
    return when (height) {
        2160 -> "4K"
        1440 -> "QHD"
        1080 -> "FHD"
        720 -> "HD"
        480 -> "SD"
        360 -> "360p"
        240 -> "240p"
        144 -> "144p"
        null -> this
        else -> "${height}p"
    }
}
