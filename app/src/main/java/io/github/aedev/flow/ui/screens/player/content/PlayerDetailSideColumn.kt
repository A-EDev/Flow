package io.github.aedev.flow.ui.screens.player.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerState
import io.github.aedev.flow.ui.components.videoplayer.sheet.LiveChatPreview
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerChaptersSheetHost
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerCommentsPanelHost
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerDescriptionSheetHost
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerLiveChatColumn
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerSettingsSheetHost
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerSleepTimerSheetHost
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerPreferencesState
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.launch

/**
 * Supporting pane of the wide player layout. Comments, the description, the chapters, the settings
 * sheet with every page it owns and the sleep timer take the pane over when opened, so the video
 * stays visible instead of being covered by a bottom sheet (#918); otherwise it shows live chat,
 * falling back to the related-videos list.
 */
@Composable
internal fun PlayerDetailSideColumn(
    video: Video,
    uiState: VideoPlayerUiState,
    playerState: EnhancedPlayerState,
    viewModel: VideoPlayerViewModel,
    screenState: PlayerScreenState,
    prefs: VideoPlayerPreferencesState,
    comments: List<Comment>,
    commentsEnabled: Boolean,
    showRelatedVideos: Boolean,
    relatedCardStyle: PlayerRelatedCardStyle,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val hasMoreComments by viewModel.hasMoreComments.collectAsStateWithLifecycle()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val playerPreferences = prefs.preferences
    val closeSheet = { screenState.closeSheet() }

    BoxWithConstraints(modifier) {
        val paneHeight = maxHeight
        val paneModifier = Modifier.fillMaxSize()
        when {
            screenState.activeSheet == PlayerSheet.Comments() && commentsEnabled -> {
                BackHandler(onBack = closeSheet)
                PlayerCommentsPanelHost(
                    videoId = video.id,
                    screenState = screenState,
                    viewModel = viewModel,
                    comments = comments,
                    isLoading = isLoadingComments,
                    isLoadingMore = isLoadingMoreComments,
                    hasMore = hasMoreComments,
                    onNavigateToChannel = onChannelClick,
                    onClose = closeSheet,
                    modifier = paneModifier,
                )
            }

            screenState.isSettingsOpen -> {
                BackHandler(onBack = closeSheet)
                PlayerSettingsSheetHost(
                    screenState = screenState,
                    playerState = playerState,
                    uiState = uiState,
                    viewModel = viewModel,
                    playerPreferences = playerPreferences,
                    scope = scope,
                    rememberPlaybackSpeed = prefs.rememberPlaybackSpeed,
                    ambientModeEnabled = prefs.ambientModeEnabled,
                    groupedQualitySelectorEnabled = prefs.groupedQualitySelectorEnabled,
                    rememberSubtitleLanguage = { language ->
                        scope.launch { playerPreferences.setPreferredSubtitleLanguage(language) }
                    },
                    asSidePanel = true,
                    expandedHeight = paneHeight,
                    onDismiss = closeSheet,
                )
            }

            screenState.activeSheet == PlayerSheet.Description -> {
                BackHandler(onBack = closeSheet)
                PlayerDescriptionSheetHost(
                    video = video,
                    uiState = uiState,
                    asSidePanel = true,
                    expandedHeight = paneHeight,
                    onDismiss = closeSheet,
                )
            }

            screenState.activeSheet == PlayerSheet.Chapters -> {
                BackHandler(onBack = closeSheet)
                PlayerChaptersSheetHost(
                    screenState = screenState,
                    chapters = uiState.chapters,
                    thumbnailUrl = video.thumbnailUrl,
                    asSidePanel = true,
                    expandedHeight = paneHeight,
                    onDismiss = closeSheet,
                )
            }

            screenState.activeSheet == PlayerSheet.SleepTimer -> {
                BackHandler(onBack = closeSheet)
                PlayerSleepTimerSheetHost(
                    asSidePanel = true,
                    expandedHeight = paneHeight,
                    onDismiss = closeSheet,
                )
            }

            uiState.isLiveChatAvailable && screenState.showLiveChatPanel -> {
                PlayerLiveChatColumn(
                    messages = uiState.liveChatMessages,
                    isLoading = uiState.isLiveChatLoading,
                    onClose = { screenState.showLiveChatPanel = false },
                    modifier = paneModifier,
                )
            }

            else -> {
                LazyColumn(
                    modifier = paneModifier,
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (uiState.isLiveChatAvailable) {
                        item {
                            LiveChatPreview(onClick = { screenState.showLiveChatPanel = true })
                        }
                    }
                    if (showRelatedVideos) {
                        relatedVideosContent(
                            relatedVideos = uiState.relatedVideos,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                            cardStyle = relatedCardStyle,
                        )
                    }
                }
            }
        }
    }
}
