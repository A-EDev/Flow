package io.github.aedev.flow.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.donation.DonationPromptHost
import io.github.aedev.flow.ui.components.equalizer.LocalEqualizerState
import io.github.aedev.flow.ui.components.layout.navigation.FlowNavigationChrome
import io.github.aedev.flow.ui.components.layout.navigation.FlowNavigationDefaults
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.layout.navigation.NavigationVisibility
import io.github.aedev.flow.ui.components.layout.navigation.flowUsesNavigationRail
import io.github.aedev.flow.ui.components.layout.navigation.rememberFlowNavigationScrollState
import io.github.aedev.flow.ui.components.layout.navigation.resolveDefaultFlowTab
import io.github.aedev.flow.ui.components.layout.navigation.visibleFlowTabs
import io.github.aedev.flow.ui.components.layout.topbar.ProvideFlowGlobalActions
import io.github.aedev.flow.ui.components.music.common.ProvideMusicPlaybackState
import io.github.aedev.flow.ui.components.music.sheet.LocalMusicMenus
import io.github.aedev.flow.ui.components.music.sheet.MusicMenuSheets
import io.github.aedev.flow.ui.components.music.sheet.rememberMusicMenus
import io.github.aedev.flow.ui.components.musicplayer.MusicMiniPlayerBottomSpacer
import io.github.aedev.flow.ui.components.musicplayer.MusicMiniPlayerHeight
import io.github.aedev.flow.ui.components.musicplayer.UnifiedMusicPlayerSheet
import io.github.aedev.flow.ui.components.musicplayer.rememberMusicPlayerSheetState
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsHost
import io.github.aedev.flow.ui.components.videoplayer.PlayerSheetValue
import io.github.aedev.flow.ui.components.videoplayer.rememberPlayerDraggableState
import io.github.aedev.flow.ui.screens.equalizer.EqualizerViewModel
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.notifications.NotificationViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerHost
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.update.UPDATE_ROUTE
import io.github.aedev.flow.ui.screens.update.UpdateLaunchEffect
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant

@UnstableApi
@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    themeVariant: ThemeVariant,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    openMusicPlayerRequest: Int = 0,
    onDeeplinkConsumed: () -> Unit = {},
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    onStartDestinationKnown: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val navController = rememberNavController()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity!!)
    // Activity-scoped so the unread badge has exactly one collector for the whole shell.
    val notificationViewModel: NotificationViewModel = hiltViewModel(activity)
    val equalizerViewModel: EqualizerViewModel = hiltViewModel(activity)
    val playerUiStateResult = playerViewModel.uiState.collectAsStateWithLifecycle()
    val playerUiState by playerUiStateResult
    val enhancedPlayerManager = remember { EnhancedPlayerManager.getInstance() }
    val hasVideoQueue by enhancedPlayerManager.hasQueue.collectAsStateWithLifecycle(
        initialValue = enhancedPlayerManager.playerState.value.queueTitle != null,
    )

    val preferences = remember { PlayerPreferences(context) }
    val isHomeNavigationEnabled by preferences.homeNavigationEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.effectiveShortsNavigationEnabled.collectAsState(initial = true)
    val isMusicNavigationEnabled by preferences.musicNavigationEnabled.collectAsState(initial = true)
    val isSearchNavigationEnabled by preferences.searchNavigationEnabled.collectAsState(initial = false)
    val isCategoriesNavigationEnabled by preferences.categoriesNavigationEnabled.collectAsState(initial = false)
    val disableShortsPlayer by preferences.effectiveDisableShortsPlayer.collectAsState(initial = false)
    val musicPlayerBackgroundStyle by preferences.musicPlayerBackgroundStyle.collectAsState(
        initial = io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle.BLUR_GRADIENT,
    )
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val subscriptionRefreshOnStartup by preferences.subscriptionRefreshOnStartup.collectAsState(initial = false)
    val bottomNavHideOnScroll by preferences.bottomNavHideOnScroll.collectAsState(initial = true)
    val sleepTimerCloseAppOnExpiry by preferences.sleepTimerCloseAppOnExpiry.collectAsState(
        initial = SleepTimerManager.preferredCloseAppOnExpiry,
    )
    val navigationVisibility =
        NavigationVisibility(
            home = isHomeNavigationEnabled,
            shorts = isShortsNavigationEnabled,
            music = isMusicNavigationEnabled,
            search = isSearchNavigationEnabled,
            categories = isCategoriesNavigationEnabled,
        )
    val navigationTabs = remember(navTabOrder, navigationVisibility) { visibleFlowTabs(navTabOrder, navigationVisibility) }
    val defaultTab = resolveDefaultFlowTab(defaultNavTabIndex, navTabOrder, navigationVisibility)
    val defaultStartRoute = defaultTab.route

    // Mini Player Customizations
    val miniPlayerScale by preferences.miniPlayerScale.collectAsState(initial = 0.45f)
    val miniPlayerShowSkipControls by preferences.miniPlayerShowSkipControls.collectAsState(initial = false)
    val miniPlayerShowNextPrevControls by preferences.miniPlayerShowNextPrevControls.collectAsState(initial = false)
    val showRestoredMusicMiniPlayer by produceState<Boolean?>(initialValue = null, preferences) {
        preferences.showRestoredMusicMiniPlayer.collect { value = it }
    }

    // Offline Monitoring
    val currentRoute = remember { mutableStateOf(defaultStartRoute) }

    // Onboarding check
    var needsOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        FlowNeuroEngine.initialize(context)
        DeepFlowManager.initialize(context)
        val bypass = activity?.intent?.getBooleanExtra(MainActivity.EXTRA_BENCHMARK_BYPASS_ONBOARDING, false) == true
        needsOnboarding = if (bypass) false else FlowNeuroEngine.needsOnboarding()
        onStartDestinationKnown()
    }

    FlowAppSideEffects(
        snackbarHostState = snackbarHostState,
        sleepTimerCloseAppOnExpiry = sleepTimerCloseAppOnExpiry,
        subscriptionRefreshOnStartup = subscriptionRefreshOnStartup,
    )

    HandleDeepLinks(deeplinkVideoId, isShort, navController, onDeeplinkConsumed)
    HandlePendingRoute(pendingRoute, navController, onPendingRouteConsumed)
    OfflineMonitor(context, navController, snackbarHostState, currentRoute)

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentTab = currentEntry?.flowTab()
    val selectedTab by rememberSelectedFlowTab(navController, defaultTab)
    val usesNavigationRail = flowUsesNavigationRail()
    var navigationRailWidth by remember { mutableStateOf(0.dp) }
    var navigationBarHeight by remember { mutableStateOf(FlowNavigationDefaults.BarHeight) }

    FlowStartTabEffects(
        navController = navController,
        currentRoute = currentRoute,
        defaultTab = defaultTab,
        isHomeNavigationEnabled = isHomeNavigationEnabled,
        needsOnboarding = needsOnboarding,
    )

    val navScrollState =
        rememberFlowNavigationScrollState(
            hideOnScroll = bottomNavHideOnScroll,
            routeKey = currentRoute.value,
            locked = currentRoute.value == SHORTS_ROUTE_KEY,
        )

    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    val isShortsPlayerRoute = currentRoute.value == SHORTS_ROUTE_KEY

    LaunchedEffect(isShortsPlayerRoute) {
        if (isShortsPlayerRoute) {
            EnhancedPlayerManager.getInstance().pause()
            GlobalPlayerState.hideMiniPlayer()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()

        val navBarBottomInset = WindowInsets.navigationBars.getBottom(density)

        val playerSheetState = rememberPlayerDraggableState()
        val playerVisibleState = remember { mutableStateOf(false) }
        var playerVisible by playerVisibleState
        var keepMiniOnQueueAutoAdvance by remember { mutableStateOf(false) }

        val musicPlayerSheetState = rememberMusicPlayerSheetState()
        val musicMenus = rememberMusicMenus()
        val mediaNavigator =
            remember(navController, playerSheetState, musicPlayerSheetState) {
                FlowMediaNavigator(navController) {
                    if (playerSheetState.currentValue == PlayerSheetValue.Expanded) playerSheetState.collapse()
                    if (musicPlayerSheetState.isExpanded) musicPlayerSheetState.collapse()
                }
            }

        val activeVideo = playerUiState.cachedVideo

        LaunchedEffect(playerSheetState.currentValue, playerSheetState.isDragging) {
            if (!playerSheetState.isDragging) {
                when (playerSheetState.currentValue) {
                    PlayerSheetValue.Expanded -> {
                        GlobalPlayerState.expandMiniPlayer()
                    }

                    PlayerSheetValue.Collapsed -> {
                        if (playerUiState.isBackgroundPlaybackMode) {
                            GlobalPlayerState.hideMiniPlayer()
                        } else {
                            GlobalPlayerState.collapseMiniPlayer()
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            enhancedPlayerManager.queueAutoAdvanceEvent.collect {
                keepMiniOnQueueAutoAdvance = playerSheetState.currentValue == PlayerSheetValue.Collapsed
            }
        }

        LaunchedEffect(playerViewModel) {
            playerViewModel.expandPlayerRequest.collect {
                playerVisible = true
                playerSheetState.expand()
            }
        }

        LaunchedEffect(playerUiState.cachedVideo?.id, playerUiState.isBackgroundPlaybackMode) {
            if (playerUiState.cachedVideo != null) {
                if (playerUiState.isBackgroundPlaybackMode) {
                    playerSheetState.snapTo(PlayerSheetValue.Collapsed)
                    GlobalPlayerState.hideMiniPlayer()
                    playerVisible = false
                    return@LaunchedEffect
                }
                GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
                playerVisible = true
                val isQueueAutoAdvanceInMiniPlayer =
                    keepMiniOnQueueAutoAdvance &&
                        hasVideoQueue &&
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

        LaunchedEffect(currentMusicTrack, showRestoredMusicMiniPlayer) {
            if (currentMusicTrack != null &&
                showRestoredMusicMiniPlayer == true &&
                musicPlayerSheetState.isDismissed
            ) {
                musicPlayerSheetState.collapse()
            } else if (currentMusicTrack == null) {
                musicPlayerSheetState.dismiss()
            }
        }

        LaunchedEffect(showRestoredMusicMiniPlayer) {
            if (showRestoredMusicMiniPlayer == false && !musicPlayerSheetState.isExpanded) {
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

        ApplyStatusBarStyle(
            themeMode = currentTheme,
            themeVariant = themeVariant,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            isFullscreen = false,
            isMusicPlayerImmersive = currentMusicTrack != null && musicPlayerSheetState.isImmersive,
            musicPlayerFollowsTheme =
                musicPlayerBackgroundStyle == io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle.DEFAULT,
            isShortsPlayer = isShortsPlayerRoute,
        )

        LaunchedEffect(isInPipMode) {
            if (
                isInPipMode &&
                !isShortsPlayerRoute &&
                !currentRoute.value.startsWith("player") &&
                currentVideo != null
            ) {
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

        val isMusicSheetShown =
            currentMusicTrack != null && !suppressMusicMiniAfterVideo && playerUiState.cachedVideo == null
        val isPlayerCoveringContent =
            (playerVisible && playerSheetState.currentValue == PlayerSheetValue.Expanded) ||
                (isMusicSheetShown && musicPlayerSheetState.isExpanded)
        val showBottomNav = !isInPipMode && currentTab.showsNavigationBar() && !isPlayerCoveringContent
        val isBottomNavShown = !usesNavigationRail && showBottomNav && navScrollState.isBarVisible
        val bottomNavOverlayHeight = rememberUpdatedState(if (isBottomNavShown) navigationBarHeight else 0.dp)
        // The rail is hidden only where content goes truly full screen; the expanded players cover
        // it instead, so opening them never re-lays out the page beneath.
        val currentDestinationRoute = currentEntry?.destination?.route
        val isNavigationRailVisible =
            !isInPipMode &&
                needsOnboarding != null &&
                currentDestinationRoute != "onboarding" &&
                !(currentDestinationRoute == SHORTS_ROUTE_PATTERN && currentTab == null)
        FlowNavigationChrome(
            tabs = navigationTabs,
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                if (currentTab == tab) {
                    TabScrollEventBus.emitScrollToTop(tab.route)
                } else {
                    currentRoute.value = tab.route
                    navController.navigate(tab.route) {
                        popUpTo(defaultStartRoute) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            barVisible = showBottomNav && navScrollState.isBarVisible,
            railVisible = isNavigationRailVisible,
            onBarHeightChanged = { navigationBarHeight = it },
            onRailWidthChanged = { navigationRailWidth = it },
        ) {
            val shouldReserveMusicMiniPlayerSpace =
                currentRoute.value.isLibraryOrSettingsRouteForMusicMiniPlayer()
            val isMusicMiniPlayerObscuringContent =
                currentMusicTrack != null &&
                    !suppressMusicMiniAfterVideo &&
                    playerUiState.cachedVideo == null &&
                    !musicPlayerSheetState.isDismissed &&
                    !musicPlayerSheetState.isExpanded
            val musicMiniPlayerInset =
                if (isMusicMiniPlayerObscuringContent) MusicMiniPlayerHeight + MusicMiniPlayerBottomSpacer else 0.dp
            val musicMiniPlayerContentPadding by animateDpAsState(
                targetValue =
                    if (shouldReserveMusicMiniPlayerSpace && isMusicMiniPlayerObscuringContent) {
                        MusicMiniPlayerHeight + MusicMiniPlayerBottomSpacer
                    } else {
                        0.dp
                    },
                animationSpec = tween(durationMillis = 220),
                label = "musicMiniPlayerContentPadding",
            )
            val bottomNavContentPadding by animateDpAsState(
                targetValue =
                    if (!bottomNavHideOnScroll && isBottomNavShown && !isShortsPlayerRoute) {
                        navigationBarHeight
                    } else {
                        0.dp
                    },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "bottomNavContentPadding",
            )

            ProvideMusicPlaybackState(
                miniPlayerInset = musicMiniPlayerInset,
                surfacesVisible = !musicPlayerSheetState.isExpanded && playerSheetState.currentValue != PlayerSheetValue.Expanded,
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor =
                        if (isInPipMode) {
                            androidx.compose.ui.graphics.Color.Black
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.background
                        },
                    contentWindowInsets = WindowInsets.systemBars,
                ) { paddingValues ->
                    val layoutDirection = LocalLayoutDirection.current
                    val contentPadding =
                        if (
                            isShortsPlayerRoute
                        ) {
                            PaddingValues(
                                start = paddingValues.calculateStartPadding(layoutDirection),
                                top = 0.dp,
                                end = paddingValues.calculateEndPadding(layoutDirection),
                                bottom = paddingValues.calculateBottomPadding(),
                            )
                        } else {
                            paddingValues
                        }
                    Box(
                        modifier =
                            Modifier
                                .padding(if (isInPipMode) PaddingValues(0.dp) else contentPadding)
                                .padding(bottom = bottomNavContentPadding.coerceAtLeast(0.dp))
                                .padding(bottom = musicMiniPlayerContentPadding.coerceAtLeast(0.dp))
                                .nestedScroll(navScrollState.nestedScrollConnection),
                    ) {
                        if (needsOnboarding != null) {
                            val homeViewModel: HomeViewModel = hiltViewModel(activity!!)
                            LaunchedEffect(homeViewModel) {
                                homeViewModel.initialize(context.applicationContext)
                            }

                            ProvideFlowGlobalActions(
                                unreadNotifications = notificationViewModel.unreadCount,
                                onOpenNotifications = { navController.navigate("notifications") },
                                onOpenSettings = { navController.navigate("settings") },
                            ) {
                                CompositionLocalProvider(
                                    LocalMediaNavigator provides mediaNavigator,
                                    LocalMusicMenus provides musicMenus,
                                    LocalEqualizerState provides equalizerViewModel.state,
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = if (needsOnboarding == true) "onboarding" else defaultStartRoute,
                                        enterTransition = FlowNavTransitions.enter,
                                        exitTransition = FlowNavTransitions.exit,
                                        popEnterTransition = FlowNavTransitions.popEnter,
                                        popExitTransition = FlowNavTransitions.popExit,
                                    ) {
                                        flowAppGraph(
                                            navController = navController,
                                            mediaNavigator = mediaNavigator,
                                            currentRoute = currentRoute,
                                            playerSheetState = playerSheetState,
                                            musicPlayerSheetState = musicPlayerSheetState,
                                            homeViewModel = homeViewModel,
                                            playerViewModel = playerViewModel,
                                            playerUiStateResult = playerUiStateResult,
                                            playerVisibleState = playerVisibleState,
                                            disableShortsPlayer = disableShortsPlayer,
                                            defaultStartRoute = defaultStartRoute,
                                            bottomNavOverlayPadding = { bottomNavOverlayHeight.value },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val bottomPaddingTarget =
            if (isBottomNavShown) {
                navigationBarHeight + with(density) { navBarBottomInset.toDp() }
            } else {
                with(density) { navBarBottomInset.toDp() }
            }
        val animatedBottomPaddingRaw by animateDpAsState(
            targetValue = bottomPaddingTarget,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "globalBottomPadding",
        )
        val animatedBottomPadding = animatedBottomPaddingRaw.coerceAtLeast(0.dp)
        val snackbarBottomPadding = (animatedBottomPadding + 12.dp).coerceAtLeast(12.dp)

        // ===== GLOBAL PLAYER OVERLAY =====
        // The video overlay takes the settled target, not the animated value: it only uses the
        // padding to pick the mini player's resting bounds, and an animated Dp parameter
        // recomposed the whole overlay on every frame of the nav bar animation.
        CompositionLocalProvider(
            LocalMediaNavigator provides mediaNavigator,
            LocalMusicMenus provides musicMenus,
            LocalEqualizerState provides equalizerViewModel.state,
        ) {
            VideoPlayerHost(
                video = activeVideo,
                isVisible = playerVisible && !isShortsPlayerRoute,
                playerSheetState = playerSheetState,
                bottomPadding = bottomPaddingTarget.coerceAtLeast(0.dp),
                startInset = if (usesNavigationRail && isNavigationRailVisible) navigationRailWidth else 0.dp,
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
                    playerSheetState.snapTo(PlayerSheetValue.Collapsed)
                    GlobalPlayerState.hideMiniPlayer()
                    playerVisible = false
                },
                onNavigateToChannel = mediaNavigator::openChannel,
                onNavigateToShorts = { videoId ->
                    playerSheetState.collapse()
                    navController.openShorts(ShortsQueueSource.SeededFeed(videoId))
                },
            )

            // ===== GLOBAL MUSIC PLAYER OVERLAY =====
            if (currentMusicTrack != null &&
                !suppressMusicMiniAfterVideo &&
                playerUiState.cachedVideo == null
            ) {
                UnifiedMusicPlayerSheet(
                    state = musicPlayerSheetState,
                    containerHeight = with(density) { screenHeightPx.toDp() },
                    bottomPadding = animatedBottomPadding,
                    track = currentMusicTrack!!,
                    onDismiss = {
                        EnhancedMusicPlayerManager.stop()
                        EnhancedMusicPlayerManager.clearCurrentTrack()
                    },
                )
            }

            MusicMenuSheets(musicMenus)
            QuickActionsHost(snackbarHostState)
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = snackbarBottomPadding,
                    ),
        )

        UpdateLaunchEffect(needsOnboarding = needsOnboarding, onOpenUpdate = { navController.navigate(UPDATE_ROUTE) })

        DonationPromptHost(
            enabled = needsOnboarding == false && !isInPipMode && !playerVisible,
            onNavigateToDonations = { navController.navigate("donations") },
        )
    }
}
