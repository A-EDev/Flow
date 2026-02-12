package com.flow.youtube.ui.screens.shorts

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.R
import com.flow.youtube.data.model.ShortVideo
import com.flow.youtube.data.model.toVideo
import com.flow.youtube.player.shorts.ShortsPlayerPool
import com.flow.youtube.ui.components.FlowCommentsBottomSheet
import com.flow.youtube.ui.components.FlowDescriptionBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    startVideoId: String? = null,
    isSavedMode: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Bottom sheet states
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var isTopComments by remember { mutableStateOf(true) }
    val comments by viewModel.commentsState.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()

    val sortedComments = remember(comments, isTopComments) {
        if (isTopComments) comments.sortedByDescending { it.likeCount }
        else comments
    }

    // Load shorts — no initialize() needed, Hilt injects all deps
    LaunchedEffect(Unit) {
        if (isSavedMode) {
            viewModel.loadSavedShorts(startVideoId)
        } else {
            viewModel.loadShorts(startVideoId = startVideoId)
        }
    }

    // Release player pool when leaving Shorts
    DisposableEffect(Unit) {
        onDispose {
            ShortsPlayerPool.getInstance().release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isLoading && uiState.shorts.isEmpty() -> {
                // Initial loading
                ShortsLoadingState(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.shorts.isEmpty() -> {
                // Error state 
                ShortsErrorState(
                    error = uiState.error,
                    onRetry = { viewModel.loadShorts() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.shorts.isNotEmpty() -> {
                val pagerState = rememberPagerState(
                    initialPage = uiState.currentIndex,
                    pageCount = { uiState.shorts.size }
                )

                // Track page changes
                LaunchedEffect(pagerState.currentPage) {
                    viewModel.updateCurrentIndex(pagerState.currentPage)
                }

                // Load more when near end
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage >= uiState.shorts.size - 3) {
                        viewModel.loadMoreShorts()
                    }
                }

                // Pre-resolve streams for adjacent pages
                LaunchedEffect(pagerState.currentPage) {
                    val currentIdx = pagerState.currentPage
                    val idsToPreload = listOfNotNull(
                        uiState.shorts.getOrNull(currentIdx + 1)?.id,
                        uiState.shorts.getOrNull(currentIdx + 2)?.id
                    )
                    if (idsToPreload.isNotEmpty()) {
                        viewModel.preResolveStreams(idsToPreload)
                    }
                }

                // Track settled page for player pool management
                LaunchedEffect(pagerState.settledPage) {
                    val settled = pagerState.settledPage
                    val playerPool = ShortsPlayerPool.getInstance()
                    
                    // Helper to get stream URL
                    suspend fun getStreams(id: String): Pair<String?, String?>? {
                         val streamInfo = viewModel.getVideoStreamInfo(id) ?: return null
                         val videoStream = streamInfo.videoStreams?.firstOrNull { it.height >= 720 }
                                            ?: streamInfo.videoStreams?.firstOrNull()
                                            ?: streamInfo.videoOnlyStreams?.firstOrNull()
                         val audioStream = streamInfo.audioStreams?.maxByOrNull { it.averageBitrate }
                         return (videoStream?.content ?: videoStream?.url) to (audioStream?.content ?: audioStream?.url)
                    }

                    // 1. Activate Current logic
                    // Ensure the player for this index is the active one (audio focus etc)
                    playerPool.activatePlayer(settled)

                    // Load current if needed
                    val currentShort = uiState.shorts.getOrNull(settled)
                    if (currentShort != null) {
                         launch {
                             val streams = getStreams(currentShort.id)
                             val vUrl = streams?.first
                             if (vUrl != null) {
                                  playerPool.prepare(settled, currentShort.id, vUrl, streams?.second, true)
                             }
                         }
                    }

                    // 2. Preload Next
                    val nextShort = uiState.shorts.getOrNull(settled + 1)
                    if (nextShort != null) {
                         launch {
                             val streams = getStreams(nextShort.id)
                             val vUrl = streams?.first
                             if (vUrl != null) {
                                  playerPool.prepare(settled + 1, nextShort.id, vUrl, streams?.second, false)
                             }
                         }
                    }

                    // 3. Preload Previous
                    val prevShort = uiState.shorts.getOrNull(settled - 1)
                     if (prevShort != null) {
                         launch {
                             val streams = getStreams(prevShort.id)
                             val vUrl = streams?.first
                             if (vUrl != null) {
                                  playerPool.prepare(settled - 1, prevShort.id, vUrl, streams?.second, false)
                             }
                         }
                    }
                    
                    // 4. Cleanup distant players
                    playerPool.releaseUnusedPlayers(settled)
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1, 
                    key = { uiState.shorts[it].id }
                ) { page ->
                    val short = uiState.shorts[page]
                    val isActive = page == pagerState.currentPage

                    ShortVideoPage(
                        video = short.toVideo(),
                        isActive = isActive,
                        pageIndex = page,
                        viewModel = viewModel,
                        onBack = onBack,
                        onChannelClick = { onChannelClick(short.channelId) },
                        onCommentsClick = {
                            viewModel.loadComments(short.id)
                            showCommentsSheet = true
                        },
                        onDescriptionClick = { showDescriptionSheet = true },
                        onShareClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    context.getString(R.string.share_short_template, short.id)
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    )
                }

                // Loading more indicator at bottom
                if (uiState.isLoadingMore) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Comments Sheet
        if (showCommentsSheet) {
            FlowCommentsBottomSheet(
                comments = sortedComments,
                commentCount = if (comments.isNotEmpty()) "${comments.size}+" else "0",
                isLoading = isLoadingComments,
                isTopSelected = isTopComments,
                onFilterChanged = { isTopComments = it },
                onLoadReplies = { viewModel.loadCommentReplies(it) },
                onDismiss = { showCommentsSheet = false }
            )
        }

        // Description Sheet
        if (showDescriptionSheet && uiState.shorts.isNotEmpty()) {
            val safeIndex = uiState.currentIndex.coerceIn(0, uiState.shorts.size - 1)
            FlowDescriptionBottomSheet(
                video = uiState.shorts[safeIndex].toVideo(),
                onDismiss = { showDescriptionSheet = false }
            )
        }

        // Top Bar Overlay
        ShortsTopBar(
            visible = uiState.shorts.isNotEmpty(),
            showBackButton = startVideoId != null || isSavedMode,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ShortsTopBar(
    visible: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    tint = Color.White
                )
            }
        } else {
             Text(
                text = "Shorts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Loading & Error States
@Composable
private fun ShortsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
        Text(
            stringResource(R.string.loading_shorts),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ShortsErrorState(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = error ?: stringResource(R.string.error_short_load),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
