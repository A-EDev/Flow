package com.flow.youtube.ui.screens.shorts

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.launch
import com.flow.youtube.ui.components.FlowCommentsBottomSheet
import com.flow.youtube.ui.components.FlowDescriptionBottomSheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    startVideoId: String? = null,  // Optional: scroll to this video when coming from home
    isSavedMode: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var isTopComments by remember { mutableStateOf(true) }
    val comments by viewModel.commentsState.collectAsState()
    
    val sortedComments = remember(comments, isTopComments) {
        if (isTopComments) {
            comments.sortedByDescending { it.likeCount }
        } else {
            comments
        }
    }
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    
    // Initialize repositories and load shorts
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        if (isSavedMode) {
            viewModel.loadSavedShorts(startVideoId)
        } else {
            viewModel.loadShorts(startVideoId = startVideoId)
        }
    }
    
    // System bars stay visible - no forced fullscreen for a better experience
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isLoading && uiState.shorts.isEmpty() -> {
                // Initial loading
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            
            uiState.error != null && uiState.shorts.isEmpty() -> {
                // Error state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.error ?: "An error occurred",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadShorts() }) {
                        Text("Retry")
                    }
                }
            }
            
            uiState.shorts.isNotEmpty() -> {
                // Shorts list with vertical pager
                VerticalShortsPager(
                    shorts = uiState.shorts,
                    currentIndex = uiState.currentIndex,
                    onIndexChanged = { viewModel.updateCurrentIndex(it) },
                    onBack = onBack,
                    onChannelClick = onChannelClick,
                    onLikeClick = { video ->
                        scope.launch {
                            viewModel.toggleLike(video)
                        }
                    },
                    onSubscribeClick = { video ->
                        scope.launch {
                            viewModel.toggleSubscription(
                                video.channelId,
                                video.channelName,
                                video.channelThumbnailUrl
                            )
                        }
                    },
                    onCommentsClick = { video ->
                        viewModel.loadComments(video.id)
                        showCommentsSheet = true
                    },
                    onDescriptionClick = { _ ->
                        showDescriptionSheet = true
                    },
                    onShareClick = { video ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this short: https://youtu.be/${video.id}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    viewModel = viewModel
                )
            }
        }
        
        if (showCommentsSheet) {
            FlowCommentsBottomSheet(
                comments = sortedComments,
                commentCount = if (comments.isNotEmpty()) "${comments.size}+" else "0",
                isLoading = isLoadingComments,
                isTopSelected = isTopComments,
                onFilterChanged = { isTop ->
                    isTopComments = isTop
                },
                onLoadReplies = { comment ->
                    viewModel.loadCommentReplies(comment)
                },
                onDismiss = { showCommentsSheet = false }
            )
        }

        if (showDescriptionSheet && uiState.shorts.isNotEmpty()) {
            FlowDescriptionBottomSheet(
                video = uiState.shorts[uiState.currentIndex],
                onDismiss = { showDescriptionSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalShortsPager(
    shorts: List<Video>,
    currentIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onLikeClick: (Video) -> Unit,
    onSubscribeClick: (Video) -> Unit,
    onCommentsClick: (Video) -> Unit,
    onDescriptionClick: (Video) -> Unit,
    onShareClick: (Video) -> Unit,
    viewModel: ShortsViewModel
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentIndex,
        pageCount = { shorts.size }
    )
    
    // Track visible item
    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
    }
    
    androidx.compose.foundation.pager.VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val short = shorts[page]
        val isVisible = page == pagerState.currentPage
        
        ShortVideoPlayer(
            video = short,
            isVisible = isVisible,
            onBack = onBack,
            onChannelClick = onChannelClick,
            onLikeClick = { onLikeClick(short) },
            onSubscribeClick = { onSubscribeClick(short) },
            onCommentsClick = { onCommentsClick(short) },
            onDescriptionClick = { onDescriptionClick(short) },
            onShareClick = { onShareClick(short) },
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
