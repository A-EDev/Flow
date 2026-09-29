package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.videoplayer.MINI_PLAYER_CORNER_RADIUS_DP
import io.github.aedev.flow.ui.components.videoplayer.VideoPlayerSurface
import io.github.aedev.flow.ui.components.videoplayer.gesture.videoPlayerControls
import io.github.aedev.flow.ui.components.videoplayer.gesture.videoPlayerZoom
import io.github.aedev.flow.ui.components.videoplayer.placedWhen
import io.github.aedev.flow.ui.components.videoplayer.subtitle.Media3SubtitleOverlay
import io.github.aedev.flow.utils.ThumbnailUrlResolver

private const val EXIT_DRAG_MIN_SCALE = 0.94f

/**
 * The player's video slot: the surface itself, the gesture and zoom transforms wrapped around it,
 * and the overlays and controls that mount on top once the sheet is at rest near expanded.
 */
@UnstableApi
@Composable
internal fun VideoStage(
    modifier: Modifier,
    session: VideoPlayerStageSession,
    isMinimized: Boolean,
    localIsInPipMode: Boolean,
    expandedSurfacesMounted: Boolean,
    expandedSurfacesPlaced: () -> Boolean,
    videoAspectRatio: Float,
    canGoPrevious: Boolean,
    isCommentsAvailable: Boolean,
    canUseFullscreenSidePanel: Boolean,
    updateBrightnessLevel: (Float) -> Unit,
    rememberSubtitleLanguage: (String) -> Unit,
    onDecodedVideoAspectRatioChanged: (Float) -> Unit,
    onSbSubmitClick: () -> Unit,
    onCastClick: () -> Unit,
) {
    val video = session.video
    val screenState = session.screenState
    val playerState = session.playerState
    val playerUiState = session.uiState
    val playerSheetState = session.sheetState
    val prefs = session.prefs
    val scope = session.scope
    val activity = session.activity
    val audioSystemInfo = session.audioSystemInfo

    // ALWAYS use the same video surface
    val gestureModifier =
        if (!isMinimized && !localIsInPipMode && !screenState.isTouchLocked) {
            modifier
                .videoPlayerControls(
                    isSpeedBoostActive = screenState.isSpeedBoostActive,
                    onSpeedBoostChange = { screenState.isSpeedBoostActive = it },
                    showControls = screenState.showControls,
                    onShowControlsChange = { screenState.showControls = it },
                    onShowSeekBackChange = { screenState.showSeekBackAnimation = it },
                    onShowSeekForwardChange = { screenState.showSeekForwardAnimation = it },
                    onSeekAccumulate = { screenState.seekAccumulation = kotlin.math.abs(it) },
                    currentPosition = { screenState.currentPosition },
                    duration = screenState.duration,
                    scope = scope,
                    isFullscreen = screenState.isFullscreen,
                    onBrightnessChange = updateBrightnessLevel,
                    onShowBrightnessChange = { screenState.showBrightnessOverlay = it },
                    onVolumeChange = { level ->
                        screenState.volumeLevel = level
                        EnhancedPlayerManager
                            .getInstance()
                            .setVolumeBoost(if (level > 1f) level else 1f)
                    },
                    onShowVolumeChange = { screenState.showVolumeOverlay = it },
                    onSeekDragChange = { dragging ->
                        screenState.isSeekDragging = dragging
                        screenState.onInteraction()
                    },
                    onSeekDragUpdate = { targetMs, deltaMs ->
                        screenState.seekDragTargetMs = targetMs
                        screenState.seekDragDeltaMs = deltaMs
                    },
                    brightnessLevel = { screenState.brightnessLevel },
                    volumeLevel = { screenState.volumeLevel },
                    maxVolume = audioSystemInfo.maxVolume,
                    audioManager = audioSystemInfo.audioManager,
                    activity = activity,
                    brightnessSwipeGesturesEnabled = prefs.brightnessSwipeGesturesEnabled,
                    volumeSwipeGesturesEnabled = prefs.volumeSwipeGesturesEnabled,
                    seekSwipeGesturesEnabled = prefs.seekSwipeGesturesEnabled,
                    allowVolumeBoost = prefs.allowVolumeBoost,
                    doubleTapSeekMs = prefs.doubleTapSeekSeconds * 1000L,
                    longPressPlaybackSpeed = prefs.longPressPlaybackSpeed,
                    onExitFullscreen = {
                        screenState.isFullscreen = false
                        screenState.isFullscreenPortrait = false
                    },
                    onExitFullscreenDrag = { offsetPx, progress ->
                        screenState.exitDragOffsetY = offsetPx
                        screenState.exitDragProgress = progress
                    },
                    isSeekForwardActive = screenState.showSeekForwardAnimation,
                    isSeekBackActive = screenState.showSeekBackAnimation,
                ).videoPlayerZoom(
                    scope = scope,
                    scale = { screenState.zoomScale },
                    offsetX = { screenState.zoomOffsetX },
                    offsetY = { screenState.zoomOffsetY },
                    onTransform = { newScale, newOffsetX, newOffsetY ->
                        screenState.zoomScale = newScale
                        screenState.zoomOffsetX = newOffsetX
                        screenState.zoomOffsetY = newOffsetY
                        screenState.showZoomIndicator = true
                        screenState.zoomIndicatorSequence += 1
                    },
                ).graphicsLayer {
                    if (screenState.isFullscreen) {
                        translationY = screenState.exitDragOffsetY
                        val shrink = lerp(1f, EXIT_DRAG_MIN_SCALE, screenState.exitDragProgress)
                        scaleX = shrink
                        scaleY = shrink
                    }
                }
        } else {
            modifier
        }

    Box(modifier = gestureModifier) {
        // Zoomable layer: video + subtitles scale together with the pinch transform
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        if (!isMinimized) {
                            scaleX = screenState.zoomScale
                            scaleY = screenState.zoomScale
                            translationX = screenState.zoomOffsetX
                            translationY = screenState.zoomOffsetY
                        }
                    },
        ) {
            VideoPlayerSurface(
                video = video,
                resizeMode = screenState.resizeMode,
                modifier = Modifier.fillMaxSize(),
                onVideoAspectRatioChanged = onDecodedVideoAspectRatioChanged,
                cornerRadiusDp =
                    if (isMinimized && !localIsInPipMode) {
                        MINI_PLAYER_CORNER_RADIUS_DP / playerSheetState.miniVisualScale
                    } else {
                        0f
                    },
                ambientMode = prefs.ambientModeEnabled && !isMinimized && !localIsInPipMode,
            )
            if (expandedSurfacesMounted && !localIsInPipMode) {
                Media3SubtitleOverlay(
                    enabled = screenState.subtitlesEnabled,
                    isAutoGenerated =
                        playerState.availableSubtitles
                            .firstOrNull { it.url == screenState.selectedSubtitleUrl }
                            ?.isAutoGenerated == true,
                    style = screenState.subtitleStyle,
                    modifier = Modifier.fillMaxSize().placedWhen(expandedSurfacesPlaced),
                )
            }
            if (playerUiState.isRestoredSession) {
                val thumbUrl =
                    video.thumbnailUrl.takeIf { it.isNotEmpty() }
                        ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(video.id)
                coil3.compose.AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(
                                RoundedCornerShape(
                                    if (isMinimized && !localIsInPipMode) {
                                        (MINI_PLAYER_CORNER_RADIUS_DP / playerSheetState.miniVisualScale).dp
                                    } else {
                                        0.dp
                                    },
                                ),
                            ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        } // end zoomable layer

        // Non-zoomable UI overlays (always at full-screen position)
        if (expandedSurfacesMounted && !localIsInPipMode) {
            Box(modifier = Modifier.matchParentSize().placedWhen(expandedSurfacesPlaced)) {
                VideoStageOverlays(session = session)
            }
        }

        // Controls overlay - fully expanded only
        var showRemainingTime by rememberSaveable { mutableStateOf(false) }
        if (!playerUiState.isUpcoming && expandedSurfacesMounted && !localIsInPipMode) {
            VideoStageControls(
                session = session,
                expandedSurfacesPlaced = expandedSurfacesPlaced,
                videoAspectRatio = videoAspectRatio,
                canGoPrevious = canGoPrevious,
                isCommentsAvailable = isCommentsAvailable,
                canUseFullscreenSidePanel = canUseFullscreenSidePanel,
                showRemainingTime = showRemainingTime,
                onToggleRemainingTime = { showRemainingTime = !showRemainingTime },
                rememberSubtitleLanguage = rememberSubtitleLanguage,
                onSbSubmitClick = onSbSubmitClick,
                onCastClick = onCastClick,
            )
        }
    }
}
