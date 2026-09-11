package io.github.aedev.flow.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.videoplayer.sheet.PlaylistQueueDock
import io.github.aedev.flow.ui.screens.player.content.PlayerDetailSideColumn
import io.github.aedev.flow.ui.screens.player.content.VideoInfoContent
import io.github.aedev.flow.ui.screens.player.content.relatedVideosContent
import io.github.aedev.flow.ui.screens.player.content.relatedVideosGridContent
import io.github.aedev.flow.ui.screens.player.state.PlayerLayoutMode
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerPreferencesState
import io.github.aedev.flow.ui.screens.player.state.playerLayoutModeFor
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import kotlin.math.roundToInt

/** A readable cap for the dock, which would otherwise stretch across a tablet's whole width. */
private val QueueDockMaxWidth = 600.dp

/**
 * EnhancedVideoPlayerScreen - Simplified version for DraggablePlayerLayout
 *
 * This composable only renders the VIDEO DETAILS (description, comments, related videos).
 * The video player surface and all effects are handled by FlowApp.kt
 */
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun EnhancedVideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    video: Video,
    alpha: () -> Float,
    videoPlayerHeightPx: () -> Float = { 0f },
    screenState: PlayerScreenState, // Shared screenState from FlowApp
    prefs: VideoPlayerPreferencesState,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val windowSizeClass = LocalWindowSizeClass.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()

    val isLocalMedia = video.id.startsWith("local_")
    val showRelatedVideos = prefs.showRelatedVideos && !isLocalMedia
    val commentsEnabled = prefs.commentsEnabled && !isLocalMedia
    val showCommentsPreview = prefs.commentsPreviewEnabled
    val relatedCardStyle = prefs.relatedCardStyle
    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsStateWithLifecycle()
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha() }
                .background(MaterialTheme.colorScheme.background),
    ) {
        val layoutMode = playerLayoutModeFor(windowSizeClass, screenState.isFullscreen, isInPipMode)
        val isWideLayout = layoutMode == PlayerLayoutMode.WIDE
        val isMediumLayout = layoutMode == PlayerLayoutMode.MEDIUM

        if (isWideLayout) {
            // The host sizes the video from the same directive (PlayerDetailPanes.supportingPaneReserve),
            // so the video row above the main pane and the scaffold's main pane keep one width.
            val navigator =
                rememberSupportingPaneScaffoldNavigator(
                    scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()),
                )
            SupportingPaneScaffold(
                directive = navigator.scaffoldDirective,
                scaffoldState = navigator.scaffoldState,
                mainPane = {
                    AnimatedPane {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .layout { measurable, constraints ->
                                        val height = videoPlayerHeightPx().roundToInt().coerceAtLeast(0)
                                        val placeable =
                                            measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                                        layout(placeable.width, height) { placeable.place(0, 0) }
                                    },
                            )

                            VideoInfoContent(
                                video = video,
                                uiState = uiState,
                                viewModel = viewModel,
                                screenState = screenState,
                                comments = comments,
                                commentsEnabled = commentsEnabled,
                                showCommentsPreview = showCommentsPreview,
                                deArrowEnabled = prefs.deArrowEnabled,
                                context = context,
                                scope = scope,
                                snackbarHostState = snackbarHostState,
                                onChannelClick = onChannelClick,
                            )
                        }
                    }
                },
                supportingPane = {
                    AnimatedPane {
                        PlayerDetailSideColumn(
                            video = video,
                            uiState = uiState,
                            playerState = playerState,
                            viewModel = viewModel,
                            screenState = screenState,
                            prefs = prefs,
                            comments = comments,
                            commentsEnabled = commentsEnabled,
                            showRelatedVideos = showRelatedVideos,
                            relatedCardStyle = relatedCardStyle,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                if (!screenState.isFullscreen && !isInPipMode) {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item {
                            VideoInfoContent(
                                video = video,
                                uiState = uiState,
                                viewModel = viewModel,
                                screenState = screenState,
                                comments = comments,
                                commentsEnabled = commentsEnabled,
                                showCommentsPreview = showCommentsPreview,
                                deArrowEnabled = prefs.deArrowEnabled,
                                context = context,
                                scope = scope,
                                snackbarHostState = snackbarHostState,
                                onChannelClick = onChannelClick,
                            )
                        }
                        if (showRelatedVideos) {
                            if (isMediumLayout) {
                                relatedVideosGridContent(
                                    relatedVideos = uiState.relatedVideos,
                                    columns = 2,
                                    onVideoClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                    cardStyle = relatedCardStyle,
                                )
                            } else {
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

        val queueVideos by EnhancedPlayerManager.getInstance().queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
        val currentQueueIndex by EnhancedPlayerManager.getInstance().currentQueueIndexState.collectAsStateWithLifecycle(
            initialValue = -1,
        )

        if ((playerState.queueTitle != null && queueVideos.isNotEmpty()) || (playerState.queueTitle == null && queueVideos.size > 1)) {
            val nextVideoTitle =
                when {
                    currentQueueIndex < queueVideos.lastIndex -> queueVideos[currentQueueIndex + 1].title
                    playerState.isQueueLooping -> queueVideos.firstOrNull()?.title
                    else -> null
                }

            PlaylistQueueDock(
                nextVideoTitle = nextVideoTitle,
                playlistName = playerState.queueTitle ?: "",
                currentIndex = currentQueueIndex,
                queueSize = queueVideos.size,
                onClick = { screenState.open(PlayerSheet.Queue) },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isWideLayout) 24.dp else 16.dp)
                        .widthIn(max = QueueDockMaxWidth),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    // Move snackbar up if dock is visible
                    .padding(bottom = if (playerState.queueTitle != null && queueVideos.isNotEmpty()) 80.dp else 0.dp),
        )
    }
}
