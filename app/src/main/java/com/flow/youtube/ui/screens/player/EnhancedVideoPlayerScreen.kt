package com.flow.youtube.ui.screens.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video

// Modular components
import com.flow.youtube.ui.screens.player.content.VideoInfoContent
import com.flow.youtube.ui.screens.player.content.relatedVideosContent
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import com.flow.youtube.ui.screens.player.state.rememberPlayerScreenState
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.ui.components.PlaylistQueueDock

/**
 * EnhancedVideoPlayerScreen - Simplified version for DraggablePlayerLayout
 * 
 * This composable only renders the VIDEO DETAILS (description, comments, related videos).
 * The video player surface and all effects are handled by FlowApp.kt
 */
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedVideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    video: Video,
    alpha: Float,
    videoPlayerHeight: androidx.compose.ui.unit.Dp = 0.dp,
    screenState: PlayerScreenState, // Shared screenState from FlowApp
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()

    // Error Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            snackbarHostState.showSnackbar(message = errorMsg, withDismissAction = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth > 600.dp && 
                              !screenState.isFullscreen && !screenState.isInPipMode

            if (isWideLayout) {
                val descriptionWeight = if (maxWidth < 840.dp) 0.55f else 0.65f
                val relatedWeight = 1f - descriptionWeight

                // Tablet/Foldable Layout
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(descriptionWeight)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (videoPlayerHeight > 0.dp) {
                            Spacer(Modifier.height(videoPlayerHeight))
                        }
                        
                        VideoInfoContent(
                            video = video,
                            uiState = uiState,
                            viewModel = viewModel,
                            screenState = screenState,
                            comments = comments,
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            onChannelClick = onChannelClick
                        )
                    }
                    LazyColumn(
                        Modifier.weight(relatedWeight), 
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        relatedVideosContent(
                            relatedVideos = uiState.relatedVideos,
                            onVideoClick = onVideoClick,
                            useCompactView = true
                        )
                    }
                }
            } else {
                // Phone Portrait Layout
                Column(Modifier.fillMaxSize()) {
                    if (!screenState.isFullscreen && !screenState.isInPipMode) {
                        LazyColumn(
                            Modifier.weight(1f), 
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                VideoInfoContent(
                                    video = video,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    screenState = screenState,
                                    comments = comments,
                                    context = context,
                                    scope = scope,
                                    snackbarHostState = snackbarHostState,
                                    onChannelClick = onChannelClick
                                )
                            }
                            relatedVideosContent(
                                relatedVideos = uiState.relatedVideos,
                                onVideoClick = onVideoClick
                            )
                        }
                    }
                }
            }
            
            // Playlist Queue Dock
            val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
            val queueVideos by EnhancedPlayerManager.getInstance().queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
            val currentQueueIndex by EnhancedPlayerManager.getInstance().currentQueueIndexState.collectAsStateWithLifecycle(initialValue = -1)

            if (playerState.queueTitle != null && queueVideos.isNotEmpty()) {
                val nextVideoTitle = if (currentQueueIndex < queueVideos.size - 1) {
                    queueVideos[currentQueueIndex + 1].title
                } else {
                    null
                }

                PlaylistQueueDock(
                    nextVideoTitle = nextVideoTitle,
                    playlistName = playerState.queueTitle ?: "",
                    currentIndex = currentQueueIndex,
                    queueSize = queueVideos.size,
                    onClick = { screenState.showPlaylistQueueSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isWideLayout) 24.dp else 16.dp)
                        .widthIn(max = 600.dp)
                )
            }

            // Snackbar host
            SnackbarHost(
                hostState = snackbarHostState, 
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Move snackbar up if dock is visible
                    .padding(bottom = if (playerState.queueTitle != null && queueVideos.isNotEmpty()) 80.dp else 0.dp)
            )
        }
    }
}