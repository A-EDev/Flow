package io.github.aedev.flow.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import androidx.room.withTransaction
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import io.github.aedev.flow.data.innertube.RssSubscriptionService
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.DonationPromptHost
import io.github.aedev.flow.ui.components.FloatingBottomNavBar
import io.github.aedev.flow.ui.components.MusicPlayerBottomSheet
import io.github.aedev.flow.ui.components.MusicPlayerSheetState
import io.github.aedev.flow.ui.components.PersistentMiniMusicPlayer
import io.github.aedev.flow.ui.components.rememberMusicPlayerSheetState
import io.github.aedev.flow.ui.components.PlayerSheetValue
import io.github.aedev.flow.ui.components.rememberPlayerDraggableState
import io.github.aedev.flow.ui.screens.music.EnhancedMusicPlayerScreen
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.isEffectivelyDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    customThemeColors: CustomThemeColors,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onCustomThemeColorsChange: (CustomThemeColors) -> Unit,
    onSystemLightThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeChange: (ThemeMode) -> Unit,
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    openMusicPlayerRequest: Int = 0,
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

    val preferences = remember { PlayerPreferences(context) }
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val isMusicNavigationEnabled by preferences.musicNavigationEnabled.collectAsState(initial = true)
    val isSearchNavigationEnabled by preferences.searchNavigationEnabled.collectAsState(initial = false)
    val isCategoriesNavigationEnabled by preferences.categoriesNavigationEnabled.collectAsState(initial = false)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val subscriptionRefreshOnStartup by preferences.subscriptionRefreshOnStartup.collectAsState(initial = false)
    val defaultStartRoute = navRouteForIndex(defaultNavTabIndex)
    
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
        DeepFlowManager.initialize(context)
        needsOnboarding = FlowNeuroEngine.needsOnboarding()
    }

    LaunchedEffect(subscriptionRefreshOnStartup) {
        if (subscriptionRefreshOnStartup) {
            refreshSubscriptionsAtStartup(context.applicationContext, preferences)
        }
    }

    LaunchedEffect(snackbarHostState) {
        DeepFlowManager.messages.collectLatest { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(snackbarHostState) {
        EnhancedMusicPlayerManager.playbackWarnings.collectLatest { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
        }
    }

    HandleDeepLinks(deeplinkVideoId, isShort, navController, onDeeplinkConsumed)
    OfflineMonitor(context, navController, snackbarHostState, currentRoute)
    
    val selectedBottomNavIndex = remember { mutableIntStateOf(0) }
    val showBottomNav = remember { mutableStateOf(true) }
    val navScrollThresholdPx = with(LocalDensity.current) { 32.dp.toPx() }

    LaunchedEffect(defaultNavTabIndex) {
        selectedBottomNavIndex.intValue = defaultNavTabIndex
        currentRoute.value = navRouteForIndex(defaultNavTabIndex)
    }

    var isNavScrolledVisible by remember { mutableStateOf(true) }
    var accumulatedNavScroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentRoute.value) {
        isNavScrolledVisible = true
        accumulatedNavScroll = 0f
    }
    val nestedScrollConnection = remember(navScrollThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val route = currentRoute.value
                if (source != NestedScrollSource.UserInput ||
                    route == "shorts" ||
                    route == "savedShortsPlayer"
                ) {
                    return Offset.Zero
                }

                val delta = available.y
                if (delta == 0f) return Offset.Zero
                if (accumulatedNavScroll != 0f && (accumulatedNavScroll > 0f) != (delta > 0f)) {
                    accumulatedNavScroll = 0f
                }
                accumulatedNavScroll += delta

                when {
                    accumulatedNavScroll <= -navScrollThresholdPx && isNavScrolledVisible -> {
                        isNavScrolledVisible = false
                        accumulatedNavScroll = 0f
                    }
                    accumulatedNavScroll >= navScrollThresholdPx && !isNavScrolledVisible -> {
                        isNavScrolledVisible = true
                        accumulatedNavScroll = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    
    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()
        
        val navBarBottomInset = WindowInsets.navigationBars.getBottom(density)
        
        val bottomNavContentHeightDp = 48.dp
        
        val playerSheetState = rememberPlayerDraggableState()
        val playerVisibleState = remember { mutableStateOf(false) }
        var playerVisible by playerVisibleState
        var keepMiniOnQueueAutoAdvance by remember { mutableStateOf(false) }

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
            showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
            when (playerSheetState.currentValue) {
                PlayerSheetValue.Expanded -> GlobalPlayerState.expandMiniPlayer()
                PlayerSheetValue.Collapsed -> GlobalPlayerState.collapseMiniPlayer()
            }
        }
    }

    LaunchedEffect(Unit) {
        EnhancedPlayerManager.getInstance().queueAutoAdvanceEvent.collect {
            keepMiniOnQueueAutoAdvance = playerSheetState.currentValue == PlayerSheetValue.Collapsed
        }
    }
    
    LaunchedEffect(playerUiState.cachedVideo) {
        if (playerUiState.cachedVideo != null) {
            playerVisible = true
            val isQueueAutoAdvanceInMiniPlayer =
                keepMiniOnQueueAutoAdvance &&
                playerState.queueTitle != null &&
                playerSheetState.currentValue == PlayerSheetValue.Collapsed

            if (
                playerUiState.isRestoredSession ||
                playerUiState.resumedInMiniPlayer ||
                isQueueAutoAdvanceInMiniPlayer
            ) {
                playerSheetState.collapse()
            } else {
                playerSheetState.expand()
            }

            keepMiniOnQueueAutoAdvance = false
        }
    }
    
    val currentMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsStateWithLifecycle()
    var suppressMusicMiniAfterVideo by remember { mutableStateOf(false) }
    var handledMusicPlayerRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeVideo?.id) {
        if (activeVideo != null) {
            suppressMusicMiniAfterVideo = true
        }
    }

    LaunchedEffect(currentMusicTrack?.videoId) {
        if (currentMusicTrack == null) {
            suppressMusicMiniAfterVideo = false
        }
    }

    LaunchedEffect(currentRoute.value) {
        if (currentRoute.value == "musicPlayer") {
            suppressMusicMiniAfterVideo = false
        }
    }

    LaunchedEffect(currentMusicTrack) {
        if (currentMusicTrack != null && musicPlayerSheetState.isDismissed) {
            musicPlayerSheetState.collapse()
        } else if (currentMusicTrack == null) {
            musicPlayerSheetState.dismiss()
        }
    }

    LaunchedEffect(openMusicPlayerRequest, currentMusicTrack?.videoId) {
        if (openMusicPlayerRequest > handledMusicPlayerRequest && currentMusicTrack != null) {
            handledMusicPlayerRequest = openMusicPlayerRequest
            suppressMusicMiniAfterVideo = false
            if (playerVisible) {
                playerSheetState.collapse()
            }
            musicPlayerSheetState.expand()
        }
    }

    LaunchedEffect(musicPlayerSheetState.isExpanded) {
        if (musicPlayerSheetState.isExpanded) {
            showBottomNav.value = false
        } else if (!musicPlayerSheetState.isDismissed && playerSheetState.currentValue != PlayerSheetValue.Expanded) {
            showBottomNav.value = true
        }
    }

    ApplyStatusBarStyle(
        themeMode = currentTheme,
        systemLightThemeMode = systemLightThemeMode,
        systemDarkThemeMode = systemDarkThemeMode,
        isFullscreen = playerUiState.isFullscreen,
        isMusicPlayerImmersive = currentMusicTrack != null && musicPlayerSheetState.progress > 0.5f
    )

    LaunchedEffect(isInPipMode) {
        if (isInPipMode && !currentRoute.value.startsWith("player") && currentVideo != null) {
            navController.navigate("player/${currentVideo!!.id}")
        }
    }

    val dismissRequested by GlobalPlayerState.dismissRequested.collectAsState()
    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            GlobalPlayerState.resetDismiss()
            GlobalPlayerState.hideMiniPlayer()
            playerVisible = false
            if (playerUiState.isRestoredSession) {
                playerViewModel.dismissContinueWatching()
            }
            playerViewModel.clearVideo()
            if (isInPipMode) {
                activity?.moveTaskToBack(false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val shouldReserveMusicMiniPlayerSpace =
            currentRoute.value.isLibraryOrSettingsRouteForMusicMiniPlayer()
        val isMusicMiniPlayerObscuringContent =
            currentMusicTrack != null &&
                !suppressMusicMiniAfterVideo &&
                playerUiState.cachedVideo == null &&
                playerUiState.streamInfo == null &&
                !musicPlayerSheetState.isDismissed &&
                !musicPlayerSheetState.isExpanded
        val musicMiniPlayerContentPadding by animateDpAsState(
            targetValue = if (shouldReserveMusicMiniPlayerSpace && isMusicMiniPlayerObscuringContent) {
                miniPlayerHeightDp
            } else {
                0.dp
            },
            animationSpec = tween(durationMillis = 220),
            label = "musicMiniPlayerContentPadding"
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isInPipMode) androidx.compose.ui.graphics.Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = {} 
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(if (isInPipMode) PaddingValues(0.dp) else paddingValues)
                    .padding(bottom = musicMiniPlayerContentPadding.coerceAtLeast(0.dp))
                    .nestedScroll(nestedScrollConnection)
            ) {
                if (needsOnboarding != null) {
                    NavHost(
                        navController = navController,
                        startDestination = if (needsOnboarding == true) "onboarding" else defaultStartRoute,
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
                            customThemeColors = customThemeColors,
                            systemLightThemeMode = systemLightThemeMode,
                            systemDarkThemeMode = systemDarkThemeMode,
                            onThemeChange = onThemeChange,
                            onCustomThemeColorsChange = onCustomThemeColorsChange,
                            onSystemLightThemeChange = onSystemLightThemeChange,
                            onSystemDarkThemeChange = onSystemDarkThemeChange,
                            disableShortsPlayer = disableShortsPlayer,
                            defaultStartRoute = defaultStartRoute
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
                isSearchEnabled = isSearchNavigationEnabled,
                isCategoriesEnabled = isCategoriesNavigationEnabled,
                navOrder = navTabOrder,
                onItemSelected = { index ->
                    val route = navRouteForIndex(index)

                    val activeRoute = navController.currentBackStackEntry?.destination?.route
                    if (activeRoute == route) {
                        TabScrollEventBus.emitScrollToTop(route)
                    } else if (route == defaultStartRoute) {
                        selectedBottomNavIndex.intValue = index
                        currentRoute.value = route
                        navController.popBackStack(defaultStartRoute, inclusive = false)
                    } else {
                        selectedBottomNavIndex.intValue = index
                        currentRoute.value = route
                        navController.navigate(route) {
                            popUpTo(defaultStartRoute) {
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
    val snackbarBottomPadding = (animatedBottomPadding + 12.dp).coerceAtLeast(12.dp)

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
            if (playerUiState.isRestoredSession) {
                playerViewModel.dismissContinueWatching()
            }
            playerViewModel.clearVideo()
        },
        onMinimize = {
            playerVisible = false
        },
        onNavigateToChannel = { channelArg ->
            val channelUrl = when {
                channelArg.startsWith("http://") || channelArg.startsWith("https://") -> channelArg
                channelArg.startsWith("@") -> "https://www.youtube.com/$channelArg"
                channelArg.startsWith("UC") && channelArg.length >= 24 -> "https://www.youtube.com/channel/$channelArg"
                else -> "https://www.youtube.com/channel/$channelArg"
            }
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
        !suppressMusicMiniAfterVideo &&
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
                    isPlayerSheetExpanded = musicPlayerSheetState.isExpanded,
                    onBackClick = { musicPlayerSheetState.collapse() },
                    onArtistClick = { channelId ->
                        musicPlayerSheetState.collapse()
                        navController.navigate("artist/${android.net.Uri.encode(channelId)}")
                    },
                    onAlbumClick = { albumId ->
                        musicPlayerSheetState.collapse()
                        navController.navigate("musicPlaylist/${android.net.Uri.encode(albumId)}")
                    },
                )
            }
        )
    }

    androidx.compose.material3.SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = snackbarBottomPadding
            )
    )

    DonationPromptHost(
        enabled = needsOnboarding == false && !isInPipMode && !playerVisible,
        onNavigateToDonations = { navController.navigate("donations") }
    )
  }
}

private fun navRouteForIndex(index: Int): String = when (index) {
    0 -> "home"
    1 -> "shorts"
    2 -> "music"
    3 -> "subscriptions"
    4 -> "library"
    5 -> "search"
    6 -> "categories"
    else -> "home"
}

private fun String.isLibraryOrSettingsRouteForMusicMiniPlayer(): Boolean {
    return this == "library" ||
        this == "history" ||
        this == "playlists" ||
        this == "playlist" ||
        this == "likes" ||
        this == "downloads" ||
        this == "musicLibrary" ||
        this == "musicPlaylists" ||
        this == "savedShorts" ||
        startsWith("settings")
}

private suspend fun refreshSubscriptionsAtStartup(
    context: android.content.Context,
    preferences: PlayerPreferences
) = withContext(Dispatchers.IO) {
    runCatching {
        val subscriptions = SubscriptionRepository.getInstance(context).getAllSubscriptions().first()
        if (subscriptions.isEmpty()) return@runCatching

        val database = AppDatabase.getDatabase(context)
        val cacheDao = database.cacheDao()
        val cachedEntities = cacheDao.getSubscriptionFeed().first()
        var finalVideos: List<Video> = emptyList()
        RssSubscriptionService.fetchSubscriptionVideos(
            channelIds = subscriptions.map { it.channelId },
            maxTotal = 600,
            knownVideoIds = cachedEntities.map { it.videoId }.toHashSet()
        ).collect { videos ->
            if (videos.isEmpty()) return@collect
            finalVideos = videos
        }
        if (finalVideos.isNotEmpty()) {
            val refreshTime = System.currentTimeMillis()
            val cutoff = refreshTime - 60L * 24L * 60L * 60L * 1000L
            val entities = finalVideos.map { video ->
                SubscriptionFeedEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    thumbnailUrl = video.thumbnailUrl,
                    duration = video.duration,
                    viewCount = video.viewCount,
                    uploadDate = video.uploadDate,
                    timestamp = video.timestamp,
                    channelThumbnailUrl = video.channelThumbnailUrl,
                    isShort = video.isShort,
                    isLive = video.isLive,
                    isUpcoming = video.isUpcoming,
                    cachedAt = refreshTime
                )
            }
            val mergedEntities = (entities + cachedEntities)
                .asSequence()
                .filter { entity -> entity.timestamp >= cutoff || entity.isUpcoming }
                .distinctBy { it.videoId }
                .sortedByDescending { it.timestamp }
                .take(600)
                .toList()
            database.withTransaction {
                cacheDao.clearSubscriptionFeed()
                cacheDao.insertSubscriptionFeed(mergedEntities)
            }
            preferences.setSubscriptionLastRefresh(refreshTime, mergedEntities.size)
        } else if (cachedEntities.isNotEmpty()) {
            preferences.setSubscriptionLastRefresh(System.currentTimeMillis(), cachedEntities.size)
        }
    }.onFailure {
        android.util.Log.w("FlowApp", "Startup subscription refresh failed: ${it.message}")
    }
}

@Composable
private fun ApplyStatusBarStyle(
    themeMode: ThemeMode,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    isFullscreen: Boolean,
    isMusicPlayerImmersive: Boolean = false
) {
    val activity = LocalContext.current as? Activity ?: return
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = themeMode.isEffectivelyDark(
        isSystemDark = isSystemDark,
        systemLightThemeMode = systemLightThemeMode,
        systemDarkThemeMode = systemDarkThemeMode
    )

    SideEffect {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val shouldDrawBehindStatusBar = isFullscreen || isMusicPlayerImmersive

        window.statusBarColor = if (shouldDrawBehindStatusBar) {
            android.graphics.Color.TRANSPARENT
        } else {
            colorScheme.background.toArgb()
        }

        insetsController.isAppearanceLightStatusBars = !isDarkTheme && !shouldDrawBehindStatusBar
    }
}
