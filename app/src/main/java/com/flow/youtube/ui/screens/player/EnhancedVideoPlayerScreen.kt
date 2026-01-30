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
            val isWideLayout = maxWidth > 600.dp && maxHeight < maxWidth && 
                              !screenState.isFullscreen && !screenState.isInPipMode

            if (isWideLayout) {
                // Tablet/Foldable Layout
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
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
                        Modifier.weight(0.35f), 
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        relatedVideosContent(
                            relatedVideos = uiState.relatedVideos,
                            onVideoClick = onVideoClick
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
            
            // Snackbar host
            SnackbarHost(
                hostState = snackbarHostState, 
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}