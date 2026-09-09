package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.ui.components.SleepTimerSheet
import io.github.aedev.flow.ui.components.commentTimestampToMs
import io.github.aedev.flow.ui.components.videoplayer.settings.PlayerSettingsPage
import io.github.aedev.flow.ui.components.videoplayer.settings.SettingsMenuDialog
import io.github.aedev.flow.ui.components.videoplayer.sheet.FlowChaptersBottomSheet
import io.github.aedev.flow.ui.components.videoplayer.sheet.PlayerCommentsPanel
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The drawer a landscape fullscreen player slides in from the trailing edge instead of raising a
 * bottom sheet, plus the width it leaves for the video beside it.
 */
@Stable
internal class FullscreenSidePanelState(
    val visible: Boolean,
    val settingsInitialPage: PlayerSettingsPage,
    val showSettings: Boolean,
    val showLiveChat: Boolean,
    val showComments: Boolean,
    val showSleepTimer: Boolean,
    val drawerWidth: Dp,
    val drawerOffset: Dp,
    val panelHeight: Dp,
    val playerWidth: Dp,
    val dragModifier: Modifier,
    val close: () -> Unit,
)

@Composable
internal fun rememberFullscreenSidePanelState(
    screenState: PlayerScreenState,
    playerUiState: VideoPlayerUiState,
    commentsEnabled: Boolean,
    canUseFullscreenSidePanel: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    scope: CoroutineScope,
): FullscreenSidePanelState {
    val density = LocalDensity.current
    val settingsInitialPage =
        when {
            screenState.showQualitySelector -> PlayerSettingsPage.Quality
            screenState.showAudioTrackSelector -> PlayerSettingsPage.Audio
            screenState.showPlaybackSpeedSelector -> PlayerSettingsPage.Speed
            screenState.showSubtitleSelector -> PlayerSettingsPage.Subtitles
            else -> PlayerSettingsPage.Main
        }
    val showSettingsSurface =
        screenState.showSettingsMenu ||
            screenState.showQualitySelector ||
            screenState.showAudioTrackSelector ||
            screenState.showPlaybackSpeedSelector ||
            screenState.showSubtitleSelector
    val showLiveChatSidePanel = screenState.showLiveChatFullscreen && playerUiState.isLiveChatAvailable
    val showCommentsSidePanel = screenState.showCommentsFullscreen && commentsEnabled
    val showSleepTimerSidePanel = screenState.showSleepTimerSheet
    val fullscreenSidePanelVisible =
        canUseFullscreenSidePanel &&
            (
                showSettingsSurface || screenState.showChaptersSheet || showLiveChatSidePanel || showCommentsSidePanel ||
                    showSleepTimerSidePanel
            )
    val fullscreenDrawerWidth = minOf(maxWidth * 0.42f, 420.dp)
    val fullscreenDrawerWidthPx = with(density) { fullscreenDrawerWidth.toPx() }
    val fullscreenDrawerOffsetPx = remember { Animatable(0f) }

    LaunchedEffect(fullscreenSidePanelVisible, fullscreenDrawerWidthPx) {
        fullscreenDrawerOffsetPx.updateBounds(
            lowerBound = 0f,
            upperBound = fullscreenDrawerWidthPx,
        )
        if (fullscreenSidePanelVisible) {
            if (fullscreenDrawerOffsetPx.value == 0f) {
                fullscreenDrawerOffsetPx.snapTo(fullscreenDrawerWidthPx)
            }
            fullscreenDrawerOffsetPx.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260),
            )
        }
    }

    fun closeFullscreenSidePanel() {
        scope.launch {
            fullscreenDrawerOffsetPx.animateTo(
                targetValue = fullscreenDrawerWidthPx,
                animationSpec = tween(durationMillis = 220),
            )
            screenState.dismissMediaSheets()
            screenState.showSleepTimerSheet = false
        }
    }
    val fullscreenSidePanelDragModifier =
        Modifier.pointerInput(fullscreenDrawerWidthPx, fullscreenSidePanelVisible) {
            if (!fullscreenSidePanelVisible || fullscreenDrawerWidthPx <= 0f) return@pointerInput
            val velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    velocityTracker.addPointerInputChange(change)
                    change.consume()
                    scope.launch {
                        fullscreenDrawerOffsetPx.snapTo(
                            (fullscreenDrawerOffsetPx.value + dragAmount)
                                .coerceIn(0f, fullscreenDrawerWidthPx),
                        )
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    scope.launch {
                        fullscreenDrawerOffsetPx.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220),
                        )
                    }
                },
                onDragEnd = {
                    val velocityX = velocityTracker.calculateVelocity().x
                    velocityTracker.resetTracking()
                    val shouldDismiss =
                        velocityX > 900f ||
                            fullscreenDrawerOffsetPx.value > fullscreenDrawerWidthPx * 0.38f
                    if (shouldDismiss) {
                        closeFullscreenSidePanel()
                    } else {
                        scope.launch {
                            fullscreenDrawerOffsetPx.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 220),
                            )
                        }
                    }
                },
            )
        }
    val fullscreenDrawerOffset = with(density) { fullscreenDrawerOffsetPx.value.toDp() }
    val fullscreenReservedWidth =
        if (fullscreenSidePanelVisible) {
            (fullscreenDrawerWidth - fullscreenDrawerOffset).coerceIn(0.dp, fullscreenDrawerWidth)
        } else {
            0.dp
        }
    val fullscreenSidePanelHeight = maxHeight
    val fullscreenPlayerWidth =
        if (fullscreenSidePanelVisible) {
            (maxWidth - fullscreenReservedWidth).coerceAtLeast(maxWidth * 0.58f)
        } else {
            maxWidth
        }

    return FullscreenSidePanelState(
        visible = fullscreenSidePanelVisible,
        settingsInitialPage = settingsInitialPage,
        showSettings = showSettingsSurface,
        showLiveChat = showLiveChatSidePanel,
        showComments = showCommentsSidePanel,
        showSleepTimer = showSleepTimerSidePanel,
        drawerWidth = fullscreenDrawerWidth,
        drawerOffset = fullscreenDrawerOffset,
        panelHeight = fullscreenSidePanelHeight,
        playerWidth = fullscreenPlayerWidth,
        dragModifier = fullscreenSidePanelDragModifier,
        close = ::closeFullscreenSidePanel,
    )
}

@UnstableApi
@Composable
internal fun BoxScope.FullscreenSidePanel(
    session: VideoPlayerStageSession,
    panelState: FullscreenSidePanelState,
    comments: List<Comment>,
    isLoadingComments: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    videoAspectRatio: Float,
    rememberSubtitleLanguage: (String) -> Unit,
    onNavigateToChannel: (String) -> Unit,
) {
    val video = session.video
    val context = session.context
    val activity = session.activity
    val scope = session.scope
    val screenState = session.screenState
    val playerState = session.playerState
    val playerUiState = session.uiState
    val playerViewModel = session.viewModel
    val prefs = session.prefs
    val playerPreferences = prefs.preferences
    val closeFullscreenSidePanel = panelState.close

    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = panelState.drawerOffset)
                .width(panelState.drawerWidth)
                .fillMaxHeight()
                .then(panelState.dragModifier)
                .background(MaterialTheme.colorScheme.surface)
                .zIndex(8f),
    ) {
        if (panelState.showSettings) {
            SettingsMenuDialog(
                playerState = playerState,
                autoplayEnabled = playerUiState.autoplayEnabled,
                subtitlesEnabled = screenState.subtitlesEnabled,
                initialPage = panelState.settingsInitialPage,
                onDismiss = { closeFullscreenSidePanel() },
                onQualitySelected = { option ->
                    EnhancedPlayerManager.getInstance().switchQuality(option)
                },
                onAudioTrackSelected = { index ->
                    EnhancedPlayerManager.getInstance().switchAudioTrack(index)
                },
                onSpeedSelected = { speed ->
                    EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
                    screenState.normalSpeed = speed
                    if (prefs.rememberPlaybackSpeed) {
                        scope.launch { playerPreferences.setPlaybackSpeed(speed) }
                    }
                },
                selectedSubtitleUrl = screenState.selectedSubtitleUrl,
                onSubtitleSelected = { index ->
                    SubtitleSelection.applyAt(
                        screenState = screenState,
                        subtitles = playerState.availableSubtitles,
                        index = index,
                        rememberLanguage = rememberSubtitleLanguage,
                    )
                },
                onDisableSubtitles = { SubtitleSelection.disable(screenState) },
                onAutoplayToggle = { playerViewModel.toggleAutoplay(it) },
                onSkipSilenceToggle = { playerViewModel.toggleSkipSilence(it) },
                onStableVolumeToggle = { playerViewModel.toggleStableVolume(it) },
                onShowSubtitleStyle = {
                    screenState.showSettingsMenu = false
                    screenState.showSubtitleStyleCustomizer = true
                },
                onLoopToggle = { playerViewModel.toggleLoop(it) },
                ambientModeEnabled = prefs.ambientModeEnabled,
                onAmbientModeToggle = { scope.launch { playerPreferences.setVideoAmbientModeEnabled(it) } },
                onCastClick = {
                    DlnaCastManager.startDiscovery(context)
                    screenState.showDlnaDialog = true
                },
                onPipClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        PictureInPictureHelper.isPlayerPopupSupported(context)
                    ) {
                        PictureInPictureHelper.requestPlayerPipMode(
                            activity = activity,
                            aspectRatio = videoAspectRatio,
                            isPlaying = playerState.isPlaying,
                        )
                    }
                },
                onSleepTimerClick = {
                    screenState.showSettingsMenu = false
                    screenState.showQualitySelector = false
                    screenState.showAudioTrackSelector = false
                    screenState.showPlaybackSpeedSelector = false
                    screenState.showSubtitleSelector = false
                    screenState.showSleepTimerSheet = true
                },
                expandedHeight = panelState.panelHeight,
                enableVerticalDismiss = false,
                useGroupedQualitySelector = prefs.groupedQualitySelectorEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (screenState.showChaptersSheet) {
            val chaptersPositionMs by remember {
                derivedStateOf { (screenState.currentPosition / 1_000L) * 1_000L }
            }
            FlowChaptersBottomSheet(
                chapters = playerUiState.chapters,
                currentPosition = chaptersPositionMs,
                durationMs = screenState.duration,
                onChapterClick = { newPosition ->
                    EnhancedPlayerManager.getInstance().seekTo(newPosition)
                },
                thumbnailUrl = video.thumbnailUrl,
                expandedHeight = panelState.panelHeight,
                enableVerticalDismiss = false,
                modifier = Modifier.fillMaxSize(),
                onDismiss = { closeFullscreenSidePanel() },
            )
        } else if (panelState.showSleepTimer) {
            SleepTimerSheet(
                onDismiss = { closeFullscreenSidePanel() },
                expandedHeight = panelState.panelHeight,
                enableVerticalDismiss = false,
                asBottomSheet = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (panelState.showLiveChat) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.live_chat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { closeFullscreenSidePanel() }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                io.github.aedev.flow.ui.components.videoplayer.sheet.LiveChatList(
                    messages = playerUiState.liveChatMessages,
                    isLoading = playerUiState.isLiveChatLoading,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        } else if (panelState.showComments) {
            PlayerCommentsPanel(
                comments = comments,
                isLoading = isLoadingComments,
                isLoadingMore = isLoadingMoreComments,
                hasMore = hasMoreComments,
                selectedFilter = screenState.commentSortFilter,
                onFilterChanged = { screenState.commentSortFilter = it },
                onTimestampClick = { EnhancedPlayerManager.getInstance().seekTo(commentTimestampToMs(it)) },
                onLoadReplies = { playerViewModel.loadCommentReplies(it) },
                onLoadMoreReplies = { playerViewModel.loadMoreCommentReplies(it) },
                onAuthorClick = { authorChannelRef ->
                    closeFullscreenSidePanel()
                    onNavigateToChannel(authorChannelRef)
                },
                onLoadMore = { playerViewModel.loadMoreComments(video.id) },
                onClose = { closeFullscreenSidePanel() },
            )
        }
    }
}
