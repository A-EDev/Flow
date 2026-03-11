package com.flow.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.flow.youtube.ui.components.MusicPlayerBottomSheet
import com.flow.youtube.ui.components.MusicPlayerSheetState
import com.flow.youtube.ui.components.PersistentMiniMusicPlayer
import com.flow.youtube.ui.components.rememberMusicPlayerSheetState
import com.flow.youtube.ui.components.PlayerSheetValue
import com.flow.youtube.ui.components.rememberPlayerDraggableState
import com.flow.youtube.ui.screens.music.EnhancedMusicPlayerScreen
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
    val isMusicNavigationEnabled by preferences.musicNavigationEnabled.collectAsState(initial = true)
    
    // Mini Player Customizations
    val miniPlayerScale by preferences.miniPlayerScale.collectAsState(initial = 0.45f)
    val miniPlayerShowSkipControls by preferences.miniPlayerShowSkipControls.collectAsState(initial = false)
    val miniPlayerShowNextPrevControls by preferences.miniPlayerShowNextPrevControls.collectAsState(initial = false)
    
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

    // Scroll-based bottom nav hide/show
    var isNavScrolledVisible by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute.value) {
        isNavScrolledVisible = true
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -10f -> isNavScrolledVisible = false 
                    available.y > 10f  -> isNavScrolledVisible = true  
                }
                return Offset.Zero
            }
        }
    }
    
    // Observer global player state
    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()
        
        val navBarBottomInset = WindowInsets.navigationBars.getBottom(density)
        
        val bottomNavContentHeightDp = 48.dp
        val bottomNavContentHeightPx = with(density) { bottomNavContentHeightDp.toPx() }
        
        // Draggable player state
        val playerSheetState = rememberPlayerDraggableState()
        val playerVisibleState = remember { mutableStateOf(false) }
        var playerVisible by playerVisibleState

        // ── Music player sheet state ─────────────────────────────────────────
        val miniPlayerHeightDp = 80.dp
        val musicPlayerSheetState = rememberMusicPlayerSheetState(
            expandedBound = with(density) { screenHeightPx.toDp() },
            collapsedBound = miniPlayerHeightDp,
        )
    
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
    
    LaunchedEffect(playerSheetState.currentValue, playerSheetState.isDragging) {
        if (!playerSheetState.isDragging) {
            // Show bottom nav when player is collapsed OR no video is playing
            showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
            // Sync with GlobalPlayerState
            when (playerSheetState.currentValue) {
                PlayerSheetValue.Expanded -> GlobalPlayerState.expandMiniPlayer()
                PlayerSheetValue.Collapsed -> GlobalPlayerState.collapseMiniPlayer()
            }
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

    LaunchedEffect(currentMusicTrack) {
        if (currentMusicTrack != null && musicPlayerSheetState.isDismissed) {
            musicPlayerSheetState.collapse()
        } else if (currentMusicTrack == null) {
            musicPlayerSheetState.dismiss()
        }
    }

    LaunchedEffect(musicPlayerSheetState.isExpanded) {
        if (musicPlayerSheetState.isExpanded) {
            showBottomNav.value = false
        } else if (!musicPlayerSheetState.isDismissed && playerSheetState.currentValue != PlayerSheetValue.Expanded) {
            showBottomNav.value = true
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
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = {} 
        ) { paddingValues ->
            val navBarExtraBottomPadding by animateDpAsState(
                targetValue = if (!isInPipMode && showBottomNav.value && isNavScrolledVisible) bottomNavContentHeightDp else 0.dp,
                animationSpec = tween(durationMillis = 220),
                label = "contentNavPadding"
            )
            Box(
                modifier = Modifier
                    .padding(if (isInPipMode) PaddingValues(0.dp) else paddingValues)
                    .padding(bottom = navBarExtraBottomPadding.coerceAtLeast(0.dp))
                    .nestedScroll(nestedScrollConnection)
            ) {
                if (needsOnboarding != null) {
                    NavHost(
                        navController = navController,
                        startDestination = if (needsOnboarding == true) "onboarding" else "home",
                        enterTransition = {
                            fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                initialOffsetX = { (it * 0.06f).toInt() },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(
                                targetOffsetX = { (it * 0.06f).toInt() },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    ) {
                        flowAppGraph(
                            navController = navController,
                            currentRoute = currentRoute,
                            showBottomNav = showBottomNav,
                            selectedBottomNavIndex = selectedBottomNavIndex,
                            playerSheetState = playerSheetState,
                            musicPlayerSheetState = musicPlayerSheetState,
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

        // ── Floating bottom nav bar overlay ──────────────────────────────────
        AnimatedVisibility(
            visible = !isInPipMode && showBottomNav.value && isNavScrolledVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + fadeIn(animationSpec = tween(160, delayMillis = 40)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f)
            ) + fadeOut(animationSpec = tween(120))
        ) {
            FloatingBottomNavBar(
                selectedIndex = selectedBottomNavIndex.intValue,
                isShortsEnabled = isShortsNavigationEnabled,
                isMusicEnabled = isMusicNavigationEnabled,
                onItemSelected = { index ->
                    val route = when (index) {
                        0 -> "home"
                        1 -> "shorts"
                        2 -> "music"
                        3 -> "subscriptions"
                        4 -> "library"
                        else -> "home"
                    }

                    val activeRoute = navController.currentBackStackEntry?.destination?.route
                    if (activeRoute == route) {
                        TabScrollEventBus.emitScrollToTop(route)
                    } else if (route == "home") {
                        selectedBottomNavIndex.intValue = index
                        currentRoute.value = route
                        navController.popBackStack("home", inclusive = false)
                    } else {
                        selectedBottomNavIndex.intValue = index
                        currentRoute.value = route
                        navController.navigate(route) {
                            popUpTo("home") {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
    
    val animatedBottomPaddingRaw by animateDpAsState(
        targetValue = if (!isInPipMode && showBottomNav.value && isNavScrolledVisible) {
            bottomNavContentHeightDp + with(density) { navBarBottomInset.toDp() }
        } else {
            with(density) { navBarBottomInset.toDp() }
        },
        animationSpec = tween(220),
        label = "globalBottomPadding"
    )
    val animatedBottomPadding = animatedBottomPaddingRaw.coerceAtLeast(0.dp)

    // ===== GLOBAL PLAYER OVERLAY =====
    GlobalPlayerOverlay(
        video = activeVideo,
        isVisible = playerVisible,
        playerSheetState = playerSheetState,
        bottomPadding = animatedBottomPadding,
        miniPlayerScale = miniPlayerScale,
        miniPlayerShowSkipControls = miniPlayerShowSkipControls,
        miniPlayerShowNextPrevControls = miniPlayerShowNextPrevControls,
        onClose = { 
            playerVisible = false 
            playerViewModel.clearVideo()
        },
        onMinimize = {
            playerVisible = false
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
    
    // ===== GLOBAL MUSIC PLAYER OVERLAY =====
    if (currentMusicTrack != null &&
        playerUiState.cachedVideo == null &&
        playerUiState.streamInfo == null
    ) {
        MusicPlayerBottomSheet(
            state = musicPlayerSheetState,
            bottomPadding = animatedBottomPadding,
            onDismiss = {
                EnhancedMusicPlayerManager.stop()
                EnhancedMusicPlayerManager.clearCurrentTrack()
            },
            collapsedContent = {
                PersistentMiniMusicPlayer(
                    onExpandClick = { musicPlayerSheetState.expand() },
                    onDismiss = {
                        EnhancedMusicPlayerManager.stop()
                        EnhancedMusicPlayerManager.clearCurrentTrack()
                        musicPlayerSheetState.dismiss()
                    }
                )
            },
            expandedContent = {
                EnhancedMusicPlayerScreen(
                    track = currentMusicTrack!!,
                    onBackClick = { musicPlayerSheetState.collapse() },
                    onArtistClick = { channelId ->
                        musicPlayerSheetState.collapse()
                        navController.navigate("artist/$channelId")
                    },
                    onAlbumClick = { albumId ->
                        musicPlayerSheetState.collapse()
                        navController.navigate("album/$albumId")
                    },
                )
            }
        )
    }
  } 
}