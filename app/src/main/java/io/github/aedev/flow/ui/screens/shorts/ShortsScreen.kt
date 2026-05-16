package io.github.aedev.flow.ui.screens.shorts

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
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.FlowCommentsBottomSheet
import io.github.aedev.flow.ui.components.CommentSortFilter
import io.github.aedev.flow.ui.components.FlowDescriptionBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
    val audioLangPref = remember(context) { PlayerPreferences(context) }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbar()
        }
    }

    var isWifi by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        fun update() {
            isWifi = cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        update()
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) = update()
            override fun onLost(network: android.net.Network) { update() }
            override fun onAvailable(network: android.net.Network) { update() }
        }
        cm.registerDefaultNetworkCallback(networkCallback)
        onDispose { cm.unregisterNetworkCallback(networkCallback) }
    }
    val shortsQualityWifi by audioLangPref.shortsQualityWifi.collectAsState(initial = io.github.aedev.flow.data.local.VideoQuality.Q_720p)
    val shortsQualityCellular by audioLangPref.shortsQualityCellular.collectAsState(initial = io.github.aedev.flow.data.local.VideoQuality.Q_480p)
    val shortsTargetHeight by remember(isWifi, shortsQualityWifi, shortsQualityCellular) {
        derivedStateOf { if (isWifi) shortsQualityWifi.height else shortsQualityCellular.height }
    }
    val prevShortsTargetHeight = remember { mutableStateOf(shortsTargetHeight) }

    // Bottom sheet states
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var commentSortFilter by remember { mutableStateOf(CommentSortFilter.TOP) }
    val comments by viewModel.commentsState.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()

    fun relativeTimeToSeconds(timeStr: String): Long {
        val lower = timeStr.lowercase().trim()
        val number = Regex("\\d+").find(lower)?.value?.toLongOrNull() ?: 0L
        return when {
            "second" in lower -> number
            "minute" in lower -> number * 60L
            "hour" in lower -> number * 3_600L
            "day" in lower -> number * 86_400L
            "week" in lower -> number * 604_800L
            "month" in lower -> number * 2_592_000L
            "year" in lower -> number * 31_536_000L
            else -> Long.MAX_VALUE
        }
    }

    val sortedComments = remember(comments, commentSortFilter) {
        val pinned = comments.filter { it.isPinned }
        val unpinned = comments.filterNot { it.isPinned }
        val sortedUnpinned = when (commentSortFilter) {
            CommentSortFilter.TOP -> unpinned.sortedByDescending { it.likeCount }
            CommentSortFilter.NEWEST -> unpinned.sortedBy { relativeTimeToSeconds(it.publishedTime) }
            CommentSortFilter.OLDEST -> unpinned.sortedByDescending { relativeTimeToSeconds(it.publishedTime) }
        }
        pinned + sortedUnpinned
    }

    // Load shorts
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
                ShortsLoadingState(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.shorts.isEmpty() -> {
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

                // Load likes and metadata for the current short
                LaunchedEffect(pagerState.currentPage) {
                    delay(750)
                    uiState.shorts.getOrNull(pagerState.currentPage)?.let {
                        viewModel.loadShortDetails(it.id)
                    }
                }

                // Load more when near end
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage >= uiState.shorts.size - 3) {
                        viewModel.loadMoreShorts()
                    }
                }

                // Pre-resolve lightweight playback streams for the visible page and near neighbors.
                LaunchedEffect(pagerState.currentPage) {
                    val currentIdx = pagerState.currentPage
                    val idsToPreload = listOfNotNull(
                        uiState.shorts.getOrNull(currentIdx)?.id,
                        uiState.shorts.getOrNull(currentIdx + 1)?.id,
                        uiState.shorts.getOrNull(currentIdx + 2)?.id
                    )
                    if (idsToPreload.isNotEmpty()) {
                        val preferredLang = audioLangPref.preferredAudioLanguage.first()
                        idsToPreload.forEach { id ->
                            launch {
                                viewModel.getPlaybackStreams(id, shortsTargetHeight, preferredLang)
                            }
                        }
                    }
                }

                // Track settled page for player pool management
                LaunchedEffect(pagerState.settledPage) {
                    val settled = pagerState.settledPage
                    val playerPool = ShortsPlayerPool.getInstance()
                    playerPool.initialize(context)

                    suspend fun prepareShort(index: Int, short: ShortVideo, shouldPlay: Boolean) {
                        try {
                            val preferredLang = audioLangPref.preferredAudioLanguage.first()
                            val streams = viewModel.getPlaybackStreams(short.id, shortsTargetHeight, preferredLang)
                            if (streams != null) {
                                playerPool.prepare(index, short.id, streams.videoUrl, streams.audioUrl, shouldPlay)
                            } else {
                                Log.w("ShortsScreen", "No stream URL resolved for ${short.id}")
                            }
                        } catch (e: Exception) {
                            Log.e("ShortsScreen", "Failed to prepare player for ${short.id}", e)
                        }
                    }

                    playerPool.activatePlayer(settled)

                    uiState.shorts.getOrNull(settled)?.let { currentShort ->
                        launch { prepareShort(settled, currentShort, shouldPlay = true) }
                    }
                    uiState.shorts.getOrNull(settled + 1)?.let { nextShort ->
                        launch { prepareShort(settled + 1, nextShort, shouldPlay = false) }
                    }
                    uiState.shorts.getOrNull(settled - 1)?.let { prevShort ->
                        launch { prepareShort(settled - 1, prevShort, shouldPlay = false) }
                    }

                    playerPool.releaseUnusedPlayers(settled)
                }

                LaunchedEffect(shortsTargetHeight) {
                    val newHeight = shortsTargetHeight
                    if (newHeight == prevShortsTargetHeight.value) return@LaunchedEffect
                    prevShortsTargetHeight.value = newHeight

                    val settled = pagerState.settledPage
                    val playerPool = ShortsPlayerPool.getInstance()

                    val currentShort = uiState.shorts.getOrNull(settled) ?: return@LaunchedEffect
                    try {
                        val streamInfo = viewModel.getVideoStreamInfo(currentShort.id)
                            ?: return@LaunchedEffect
                        val allVideoStreams = (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
                        val videoStream = if (newHeight == 0) {
                            allVideoStreams.maxByOrNull { it.height }
                        } else {
                            allVideoStreams.filter { it.height <= newHeight }.maxByOrNull { it.height }
                                ?: allVideoStreams.minByOrNull { it.height }
                        }
                        val vUrl = videoStream?.content ?: videoStream?.url
                        if (vUrl != null) {
                            playerPool.reloadWithVideoUrl(settled, currentShort.id, vUrl)
                        }
                    } catch (e: Exception) {
                        Log.e("ShortsScreen", "Quality change: failed to reload ${currentShort.id}", e)
                    }

                    uiState.shorts.getOrNull(settled + 1)?.let { nextShort ->
                        launch {
                            runCatching {
                                val streamInfo = viewModel.getVideoStreamInfo(nextShort.id)
                                    ?: return@runCatching
                                val allVideoStreams = (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
                                val videoStream = if (newHeight == 0) {
                                    allVideoStreams.maxByOrNull { it.height }
                                } else {
                                    allVideoStreams.filter { it.height <= newHeight }.maxByOrNull { it.height }
                                        ?: allVideoStreams.minByOrNull { it.height }
                                }
                                val vUrl = videoStream?.content ?: videoStream?.url
                                if (vUrl != null) playerPool.reloadWithVideoUrl(settled + 1, nextShort.id, vUrl)
                            }
                        }
                    }
                    uiState.shorts.getOrNull(settled - 1)?.let { prevShort ->
                        launch {
                            runCatching {
                                val streamInfo = viewModel.getVideoStreamInfo(prevShort.id)
                                    ?: return@runCatching
                                val allVideoStreams = (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
                                val videoStream = if (newHeight == 0) {
                                    allVideoStreams.maxByOrNull { it.height }
                                } else {
                                    allVideoStreams.filter { it.height <= newHeight }.maxByOrNull { it.height }
                                        ?: allVideoStreams.minByOrNull { it.height }
                                }
                                val vUrl = videoStream?.content ?: videoStream?.url
                                if (vUrl != null) playerPool.reloadWithVideoUrl(settled - 1, prevShort.id, vUrl)
                            }
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
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
                        onDescriptionClick = {
                            scope.launch { viewModel.loadShortDetails(short.id) }
                            showDescriptionSheet = true
                        },
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
                        },
                        onWantMore = { viewModel.wantMoreLikeThis(short) },
                        onNotInterested = { viewModel.notInterested(short) },
                        onVideoEnded = {
                            scope.launch {
                                if (page < uiState.shorts.size - 1) {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
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
                isLoading = isLoadingComments,
                selectedFilter = commentSortFilter,
                onFilterChanged = { commentSortFilter = it },
                onLoadReplies = { viewModel.loadCommentReplies(it) },
                onAuthorClick = { authorHandle ->
                    showCommentsSheet = false
                    onChannelClick("@$authorHandle")
                },
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color.DarkGray,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
        }
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