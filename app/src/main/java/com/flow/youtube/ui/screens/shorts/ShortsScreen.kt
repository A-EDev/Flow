package com.flow.youtube.ui.screens.shorts

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Initialize repositories
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadShorts()
    }
    
    // Hide system bars for immersive experience
    DisposableEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            // Hide status and navigation bars
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        onDispose {
            // Restore system bars
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
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
                    scope = scope,
                    viewModel = viewModel
                )
            }
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
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: ShortsViewModel
) {
    val listState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Track visible item
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex != currentIndex) {
            onIndexChanged(listState.firstVisibleItemIndex)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        flingBehavior = snapBehavior,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            count = shorts.size,
            key = { index -> shorts[index].id }
        ) { index ->
            val short = shorts[index]
            val isVisible = index == listState.firstVisibleItemIndex
            
            ShortVideoPlayer(
                video = short,
                isVisible = isVisible,
                onBack = onBack,
                onChannelClick = onChannelClick,
                onLikeClick = { onLikeClick(short) },
                onSubscribeClick = { onSubscribeClick(short) },
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
            )
        }
    }
}
