package com.flow.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.components.FloatingBottomNavBar
import com.flow.youtube.ui.components.PersistentMiniMusicPlayer
import com.flow.youtube.ui.components.PlayerSheetValue
import com.flow.youtube.ui.components.rememberPlayerDraggableState
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.theme.ThemeMode

@UnstableApi
@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    onDeeplinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val navController = rememberNavController()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity!!)
    val playerUiStateResult = playerViewModel.uiState.collectAsStateWithLifecycle()
    val playerUiState by playerUiStateResult
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    
    val preferences = remember { com.flow.youtube.data.local.PlayerPreferences(context) }
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    
    // Offline Monitoring
    val currentRoute = remember { mutableStateOf("home") }
    
    // Onboarding check
    var needsOnboarding by remember { mutableStateOf<Boolean?>(null) }
    
    LaunchedEffect(Unit) {
        FlowNeuroEngine.initialize(context)
        needsOnboarding = FlowNeuroEngine.needsOnboarding()
    }

    HandleDeepLinks(deeplinkVideoId, isShort, navController, onDeeplinkConsumed)
    OfflineMonitor(context, navController, snackbarHostState, currentRoute)
    
    val selectedBottomNavIndex = remember { mutableIntStateOf(0) }
    val showBottomNav = remember { mutableStateOf(true) }
    
    // Observer global player state
    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()
        
        val navBarBottomInset = WindowInsets.navigationBars.getBottom(density)
        
        val bottomNavContentHeightDp = 48.dp
        val bottomNavContentHeightPx = with(density) { bottomNavContentHeightDp.toPx() }
        
        val bottomNavHeightPx = bottomNavContentHeightPx + navBarBottomInset
        
        // Mini Player Floating Dimensions (Capped at 320dp, otherwise 55% width)
        val screenWidthPx = constraints.maxWidth.toFloat()
        val maxMiniWidthPx = with(density) { 320.dp.toPx() }
        val miniPlayerWidthPx = (screenWidthPx * 0.55f).coerceAtMost(maxMiniWidthPx)
        
        // Used float division for aspect ratio calculation
        val miniPlayerHeightPx = miniPlayerWidthPx * (9f / 16f)
        val marginPx = with(density) { 12.dp.toPx() }
        
        // Calculate maxOffset: Position of Mini Player Top
        // We want it to sit at bottom-right, respecting bottom nav
        // Y = FullHeight - (Content + Inset) - MiniPlayer - Margin
        val maxOffset = screenHeightPx - bottomNavHeightPx - miniPlayerHeightPx - marginPx
        
        // Draggable player state
        val playerSheetState = rememberPlayerDraggableState(maxOffset = maxOffset)
        val playerVisibleState = remember { mutableStateOf(false) }
        var playerVisible by playerVisibleState
    
    val activeVideo = playerUiState.cachedVideo ?: playerUiState.streamInfo?.let { streamInfo ->
        Video(
            id = streamInfo.id,
            title = streamInfo.name ?: "",
            channelName = streamInfo.uploaderName ?: "",
            channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: "",
            thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: "",
            duration = streamInfo.duration.toInt(),
            viewCount = streamInfo.viewCount,
            uploadDate = ""
        )
    }
    
    LaunchedEffect(playerSheetState.currentValue) {
        // Show bottom nav when player is collapsed OR no video is playing
        showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
        // Sync with GlobalPlayerState
        when (playerSheetState.currentValue) {
            PlayerSheetValue.Expanded -> GlobalPlayerState.expandMiniPlayer()
            PlayerSheetValue.Collapsed -> GlobalPlayerState.collapseMiniPlayer()
        }
    }
    
    LaunchedEffect(playerUiState.cachedVideo) {
        if (playerUiState.cachedVideo != null) {
            playerVisible = true
            playerSheetState.expand()
        }
    }
    
    // Observe music player state
    val currentMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val musicPlayerState by EnhancedMusicPlayerManager.playerState.collectAsStateWithLifecycle()

    // When music starts, clear video state to avoid conflicts
    LaunchedEffect(currentMusicTrack, musicPlayerState.isPlaying, musicPlayerState.isBuffering, musicPlayerState.isPreparing) {
        if (currentMusicTrack != null && (musicPlayerState.isPlaying || musicPlayerState.isBuffering || musicPlayerState.isPreparing)) {
            playerViewModel.clearVideo()
            playerVisible = false
        }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode && !currentRoute.value.startsWith("player") && currentVideo != null) {
            navController.navigate("player/${currentVideo!!.id}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
            containerColor = if (isInPipMode) androidx.compose.ui.graphics.Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top), 
            bottomBar = {
                if (!isInPipMode && showBottomNav.value) {
                    Column {
                        // Music mini player above bottom nav
                        // Only show music player if NOT on music screen AND no video is currently active/cached
                        if (currentMusicTrack != null && !currentRoute.value.startsWith("musicPlayer") && playerUiState.cachedVideo == null && playerUiState.streamInfo == null) {
                            PersistentMiniMusicPlayer(
                                onExpandClick = {
                                    currentMusicTrack?.let { track ->
                                        navController.navigate("musicPlayer/${track.videoId}")
                                    }
                                },
                                onDismiss = {
                                    EnhancedMusicPlayerManager.stop()
                                    EnhancedMusicPlayerManager.clearCurrentTrack()
                                }
                            )
                        }
                        
                        // Bottom nav bar
                        FloatingBottomNavBar(
                            selectedIndex = selectedBottomNavIndex.intValue,
                            isShortsEnabled = isShortsNavigationEnabled,
                            onItemSelected = { index ->
                            when (index) {
                                0 -> {
                                    if (currentRoute.value == "home") {
                                        TabScrollEventBus.emitScrollToTop("home")
                                    } else {
                                        selectedBottomNavIndex.intValue = index
                                        currentRoute.value = "home"
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                }
                                1 -> {
                                    if (currentRoute.value.startsWith("shorts")) {
                                        TabScrollEventBus.emitScrollToTop("shorts")
                                    } else {
                                        selectedBottomNavIndex.intValue = index
                                        currentRoute.value = "shorts"
                                        navController.navigate("shorts") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                2 -> {
                                    if (currentRoute.value == "music") {
                                        TabScrollEventBus.emitScrollToTop("music")
                                    } else {
                                        selectedBottomNavIndex.intValue = index
                                        currentRoute.value = "music"
                                        navController.navigate("music") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                3 -> {
                                    if (currentRoute.value == "subscriptions") {
                                        TabScrollEventBus.emitScrollToTop("subscriptions")
                                    } else {
                                        selectedBottomNavIndex.intValue = index
                                        currentRoute.value = "subscriptions"
                                        navController.navigate("subscriptions") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                4 -> {
                                    if (currentRoute.value == "library") {
                                        TabScrollEventBus.emitScrollToTop("library")
                                    } else {
                                        selectedBottomNavIndex.intValue = index
                                        currentRoute.value = "library"
                                        navController.navigate("library") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
                    )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(if (isInPipMode) PaddingValues(0.dp) else paddingValues)) {
                if (needsOnboarding != null) {
                    NavHost(
                        navController = navController,
                        startDestination = if (needsOnboarding == true) "onboarding" else "home",
                        enterTransition = {
                            fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                                initialOffsetX = { 30 },
                                animationSpec = tween(200)
                            )
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(150))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(200))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                                targetOffsetX = { 30 },
                                animationSpec = tween(150)
                            )
                        }
                    ) {
                        flowAppGraph(
                            navController = navController,
                            currentRoute = currentRoute,
                            showBottomNav = showBottomNav,
                            selectedBottomNavIndex = selectedBottomNavIndex,
                            playerSheetState = playerSheetState,
                            playerViewModel = playerViewModel,
                            playerUiStateResult = playerUiStateResult,
                            playerVisibleState = playerVisibleState,
                            currentTheme = currentTheme,
                            onThemeChange = onThemeChange
                        )
                    }
                }
            }
        }
    }
    
    // ===== GLOBAL PLAYER OVERLAY =====
    GlobalPlayerOverlay(
        video = activeVideo,
        isVisible = playerVisible,
        playerSheetState = playerSheetState,
        onClose = { 
            playerVisible = false 
            playerViewModel.clearVideo()
        },
        onNavigateToChannel = { channelId ->
            val channelUrl = "https://www.youtube.com/channel/$channelId"
            val encodedUrl = java.net.URLEncoder.encode(channelUrl, "UTF-8")
            playerSheetState.collapse()
            navController.navigate("channel?url=$encodedUrl")
        },
        onNavigateToShorts = { videoId ->
            playerSheetState.collapse()
            navController.navigate("shorts?startVideoId=$videoId")
        }
    )
  } 
}