package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.dlna.DlnaDevice
import io.github.aedev.flow.ui.components.videoplayer.DlnaDevicePickerDialog
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerBottomSheetsContainer
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerDialogsContainer
import io.github.aedev.flow.ui.screens.player.dialogs.SbSubmitSegmentDialog
import io.github.aedev.flow.ui.screens.player.state.MediaSheetHeights
import io.github.aedev.flow.ui.screens.player.state.PlayerLayoutMode

/** Every dialog and bottom sheet the player overlay raises above its own stage. */
@UnstableApi
@Composable
internal fun VideoPlayerDialogs(
    session: VideoPlayerStageSession,
    completeVideo: Video,
    mediaSheetHeights: MediaSheetHeights,
    onMediaSheetProgressChange: (Float) -> Unit,
    canUseFullscreenSidePanel: Boolean,
    playerLayoutMode: PlayerLayoutMode,
    comments: List<Comment>,
    isLoadingComments: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    showSbSubmitDialog: Boolean,
    onSbSubmitDialogDismiss: () -> Unit,
    showDlnaDialog: Boolean,
    onDlnaDialogDismiss: () -> Unit,
    dlnaDevices: State<List<DlnaDevice>>,
    isDlnaDiscovering: State<Boolean>,
    onNavigateToChannel: (String) -> Unit,
    onNavigateToShorts: (String) -> Unit,
    onClose: () -> Unit,
) {
    val video = session.video
    val context = session.context
    val screenState = session.screenState
    val playerState = session.playerState
    val playerUiState = session.uiState
    val playerViewModel = session.viewModel
    val prefs = session.prefs

    // Dialogs
    PlayerDialogsContainer(
        screenState = screenState,
        playerState = playerState,
        uiState = playerUiState,
        video = completeVideo,
        viewModel = playerViewModel,
        renderSettingsMenu = !canUseFullscreenSidePanel,
        mediaSheetExpandedHeight = mediaSheetHeights.expanded,
        mediaSheetCollapsedHeight = mediaSheetHeights.collapsed,
        onMediaSheetProgressChange = onMediaSheetProgressChange,
    )

    // SB Submit dialog
    if (showSbSubmitDialog) {
        val initialPosition = remember { screenState.currentPosition }
        SbSubmitSegmentDialog(
            videoId = video.id,
            currentPositionMs = initialPosition,
            onDismiss = onSbSubmitDialogDismiss,
        )
    }

    // DLNA device picker dialog
    if (showDlnaDialog) {
        DlnaDevicePickerDialog(
            devices = dlnaDevices.value,
            isDiscovering = isDlnaDiscovering.value,
            isCasting = DlnaCastManager.isCasting,
            videoTitle = video.title,
            onDeviceSelected = { device ->
                val currentPlayerUrl =
                    EnhancedPlayerManager
                        .getInstance()
                        .getPlayer()
                        ?.currentMediaItem
                        ?.localConfiguration
                        ?.uri
                        ?.toString()
                DlnaCastManager.castStreamInfo(
                    device = device,
                    title = video.title,
                    streamInfo = playerUiState.streamInfo,
                    currentPlayerUrl = currentPlayerUrl,
                )
                onDlnaDialogDismiss()
            },
            onStopCasting = {
                DlnaCastManager.disconnect()
                onDlnaDialogDismiss()
            },
            onDismiss = {
                DlnaCastManager.stopDiscovery()
                onDlnaDialogDismiss()
            },
        )
    }

    // Bottom Sheets
    PlayerBottomSheetsContainer(
        screenState = screenState,
        uiState = playerUiState,
        video = video,
        completeVideo = completeVideo,
        disableShortsPlayer = prefs.disableShortsPlayer,
        showShortsPlayerPrompt = prefs.showShortsPlayerPrompt,
        comments = comments,
        commentsEnabled = prefs.commentsEnabled,
        isLoadingComments = isLoadingComments,
        isLoadingMoreComments = isLoadingMoreComments,
        hasMoreComments = hasMoreComments,
        onLoadMoreComments = { videoId -> playerViewModel.loadMoreComments(videoId) },
        mediaSheetExpandedHeight = mediaSheetHeights.expanded,
        mediaSheetCollapsedHeight = mediaSheetHeights.collapsed,
        context = context,
        onPlayAsShort = { videoId ->
            onClose()
            onNavigateToShorts(videoId)
        },
        onPlayAsMusic = { _ ->
            // Handle play as music - still placeholder for now
        },
        onLoadReplies = { comment ->
            playerViewModel.loadCommentReplies(comment)
        },
        onLoadMoreReplies = { comment ->
            playerViewModel.loadMoreCommentReplies(comment)
        },
        onNavigateToChannel = { channelId ->
            onNavigateToChannel(channelId)
        },
        renderCommentsSheet = playerLayoutMode != PlayerLayoutMode.WIDE,
        renderChaptersSheet = !canUseFullscreenSidePanel,
        renderSleepTimerSheet = !canUseFullscreenSidePanel,
        onMediaSheetProgressChange = onMediaSheetProgressChange,
    )
}
