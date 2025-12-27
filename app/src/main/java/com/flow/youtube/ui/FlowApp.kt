package com.flow.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.components.FloatingBottomNavBar
import com.flow.youtube.ui.components.PersistentVideoMiniPlayer
import com.flow.youtube.ui.components.PersistentMiniMusicPlayer
import com.flow.youtube.ui.screens.explore.ExploreScreen
import com.flow.youtube.ui.screens.home.HomeScreen
import com.flow.youtube.ui.screens.history.HistoryScreen
import com.flow.youtube.ui.screens.library.LibraryScreen
import com.flow.youtube.ui.screens.library.WatchLaterScreen
import com.flow.youtube.ui.screens.likedvideos.LikedVideosScreen
import com.flow.youtube.ui.screens.playlists.PlaylistsScreen
import com.flow.youtube.ui.screens.playlists.PlaylistDetailScreen
import com.flow.youtube.ui.screens.music.PremiumMusicScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicPlayerScreen
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicPlayerViewModel
import com.flow.youtube.ui.screens.player.EnhancedVideoPlayerScreen
import com.flow.youtube.ui.screens.search.SearchScreen
import com.flow.youtube.ui.screens.settings.SettingsScreen
import com.flow.youtube.ui.screens.shorts.ShortsScreen
import com.flow.youtube.ui.screens.subscriptions.SubscriptionsScreen
import com.flow.youtube.ui.screens.channel.ChannelScreen
import com.flow.youtube.ui.theme.ThemeMode
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    var selectedBottomNavIndex by remember { mutableStateOf(0) }
    var showBottomNav by remember { mutableStateOf(true) }
    
    // Observer global player state
    val isMiniPlayerVisible by GlobalPlayerState.isMiniPlayerVisible.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    val currentRoute = remember { mutableStateOf("home") }
    
    // Observe music player state
    val currentMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val musicPlayerState by EnhancedMusicPlayerManager.playerState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Stack the mini players above the bottom nav
                Column {
                    // Show persistent video mini player when video is playing and not on video player screen
                    if (showBottomNav && isMiniPlayerVisible && currentVideo != null && !currentRoute.value.startsWith("player")) {
                        PersistentVideoMiniPlayer(
                            video = currentVideo!!,
                            onExpandClick = {
                                GlobalPlayerState.hideMiniPlayer()
                                navController.navigate("player/${currentVideo!!.id}")
                            },
                            onDismiss = {
                                GlobalPlayerState.stop()
                            }
                        )
                    }
                    
                    // Show persistent music mini player when music is playing and not on music player screen
                    if (showBottomNav && currentMusicTrack != null && !currentRoute.value.startsWith("musicPlayer")) {
                        PersistentMiniMusicPlayer(
                            onExpandClick = {
                                currentMusicTrack?.let { track ->
                                    navController.navigate("musicPlayer/${track.videoId}")
                                }
                            },
                            onDismiss = {
                                // Stop music and clear current track
                                EnhancedMusicPlayerManager.stop()
                                EnhancedMusicPlayerManager.clearCurrentTrack()
                            }
                        )
                    }
                    
                    if (showBottomNav) {
                        FloatingBottomNavBar(
                            selectedIndex = selectedBottomNavIndex,
                            onItemSelected = { index ->
                                selectedBottomNavIndex = index
                                when (index) {
                                    0 -> {
                                        currentRoute.value = "home"
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                    1 -> {
                                        currentRoute.value = "shorts"
                                        navController.navigate("shorts")
                                    }
                                    2 -> {
                                        currentRoute.value = "music"
                                        navController.navigate("music")
                                    }
                                    3 -> {
                                        currentRoute.value = "subscriptions"
                                        navController.navigate("subscriptions")
                                    }
                                    4 -> {
                                        currentRoute.value = "library"
                                        navController.navigate("library")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") {
                    currentRoute.value = "home"
                    showBottomNav = true
                    selectedBottomNavIndex = 0
                    HomeScreen(
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}")
                        },
                        onShortClick = { video ->
                            // Navigate to shorts screen with the clicked short's ID
                            navController.navigate("shorts?startVideoId=${video.id}")
                        },
                        onSearchClick = {
                            navController.navigate("search")
                        },
                        onNotificationClick = {
                            // TODO: Navigate to notifications
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        }
                    )
                }

                composable("explore") {
                    currentRoute.value = "explore"
                    showBottomNav = true
                    selectedBottomNavIndex = -1
                    ExploreScreen(
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        },
                        onShortsClick = {
                            navController.navigate("shorts")
                        }
                    )
                }

                composable(
                    route = "shorts?startVideoId={startVideoId}",
                    arguments = listOf(
                        navArgument("startVideoId") { 
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    currentRoute.value = "shorts"
                    showBottomNav = true
                    selectedBottomNavIndex = 1
                    val startVideoId = backStackEntry.arguments?.getString("startVideoId")
                    ShortsScreen(
                        startVideoId = startVideoId,
                        onBack = {
                            navController.popBackStack()
                        },
                        onChannelClick = { channelId ->
                            navController.navigate("channel?url=%2Fchannel%2F$channelId")
                        }
                    )
                }

                composable("subscriptions") {
                    currentRoute.value = "subscriptions"
                    showBottomNav = true
                    selectedBottomNavIndex = 3
                    SubscriptionsScreen(
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}")
                        },
                        onChannelClick = { channelUrl ->
                            val encodedUrl = channelUrl.replace("/", "%2F").replace(":", "%3A")
                            navController.navigate("channel?url=$encodedUrl")
                        }
                    )
                }

                composable("library") {
                    currentRoute.value = "library"
                    showBottomNav = true
                    selectedBottomNavIndex = 4
                    LibraryScreen(
                        onNavigateToHistory = { 
                            navController.navigate("history")
                        },
                        onNavigateToPlaylists = { 
                            navController.navigate("playlists")
                        },
                        onNavigateToLikedVideos = { 
                            navController.navigate("likedVideos")
                        },
                        onNavigateToWatchLater = {
                            navController.navigate("watchLater")
                        },
                        onNavigateToDownloads = { /* Navigate to downloads */ },
                        onManageData = {
                            navController.navigate("settings")
                        }
                    )
                }

                composable("search") {
                    currentRoute.value = "search"
                    showBottomNav = true
                    selectedBottomNavIndex = -1
                    SearchScreen(
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}")
                        },
                        onChannelClick = { channel ->
                            // Use the full URL if available, otherwise construct from ID
                            val channelUrl = if (channel.url.isNotBlank()) {
                                channel.url
                            } else {
                                "https://www.youtube.com/channel/${channel.id}"
                            }
                            val encodedUrl = java.net.URLEncoder.encode(channelUrl, "UTF-8")
                            navController.navigate("channel?url=$encodedUrl")
                        }
                    )
                }

                composable("settings") {
                    currentRoute.value = "settings"
                    showBottomNav = false
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAppearance = { navController.navigate("settings/appearance") }
                    )
                }

                composable("settings/appearance") {
                    currentRoute.value = "settings/appearance"
                    showBottomNav = false
                    com.flow.youtube.ui.screens.settings.AppearanceScreen(
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = "channel?url={channelUrl}",
                    arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
                ) { backStackEntry ->
                    currentRoute.value = "channel"
                    showBottomNav = false
                    val channelUrl = backStackEntry.arguments?.getString("channelUrl")?.let {
                        java.net.URLDecoder.decode(it, "UTF-8")
                    } ?: ""
                    
                    ChannelScreen(
                        channelUrl = channelUrl,
                        onVideoClick = { videoUrl ->
                            // Extract video ID from URL and navigate
                            val videoId = when {
                                videoUrl.contains("v=") -> videoUrl.substringAfter("v=").substringBefore("&")
                                videoUrl.contains("/watch/") -> videoUrl.substringAfter("/watch/").substringBefore("?")
                                else -> videoUrl.substringAfterLast("/").substringBefore("?")
                            }
                            navController.navigate("player/$videoId")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // History Screen
                composable("history") {
                    currentRoute.value = "history"
                    showBottomNav = false
                    HistoryScreen(
                        onVideoClick = { videoId ->
                            navController.navigate("player/$videoId")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Liked Videos Screen
                composable("likedVideos") {
                    currentRoute.value = "likedVideos"
                    showBottomNav = false
                    LikedVideosScreen(
                        onVideoClick = { videoId ->
                            navController.navigate("player/$videoId")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Watch Later Screen
                composable("watchLater") {
                    currentRoute.value = "watchLater"
                    showBottomNav = false
                    WatchLaterScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { videoId ->
                            navController.navigate("player/$videoId")
                        }
                    )
                }

                // Playlists Screen
                composable("playlists") {
                    currentRoute.value = "playlists"
                    showBottomNav = false
                    PlaylistsScreen(
                        onBackClick = { navController.popBackStack() },
                        onPlaylistClick = { playlist ->
                            navController.navigate("playlist/${playlist.id}")
                        },
                        onNavigateToWatchLater = { navController.navigate("watchLater") },
                        onNavigateToLikedVideos = { navController.navigate("likedVideos") }
                    )
                }

                // Playlist Detail Screen
                composable("playlist/{playlistId}") { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                    currentRoute.value = "playlist"
                    showBottomNav = false
                    PlaylistDetailScreen(
                        // playlistId is handled by ViewModel via SavedStateHandle
                        // playlistRepository is injected by Hilt
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}")
                        }
                    )
                }

                // Music Screen - Enhanced with SoundCloud
                composable("music") {
                    currentRoute.value = "music"
                    showBottomNav = true
                    selectedBottomNavIndex = 2
                    
                    // Get the MusicPlayerViewModel from this composable context
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    LaunchedEffect(Unit) {
                        musicPlayerViewModel.initialize(context)
                    }
                    
                    PremiumMusicScreen(
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { track ->
                            // Load and play the track immediately
                            musicPlayerViewModel.loadAndPlayTrack(track)
                            
                            // Navigate to player
                            navController.navigate("musicPlayer/${track.videoId}")
                        },
                        onLibraryClick = {
                            navController.navigate("musicLibrary")
                        },
                        onSearchClick = {
                            navController.navigate("search")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        }
                    )
                }
                
                // Library Screen - Playlists, Favorites, Downloads
                composable("musicLibrary") {
                    currentRoute.value = "musicLibrary"
                    showBottomNav = false
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    LaunchedEffect(Unit) {
                        musicPlayerViewModel.initialize(context)
                    }
                    
                    com.flow.youtube.ui.screens.music.LibraryScreen(
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = { track ->
                            musicPlayerViewModel.loadAndPlayTrack(track)
                            navController.navigate("musicPlayer/${track.videoId}")
                        }
                    )
                }

                // Music Player Screen - Enhanced
                composable(
                    route = "musicPlayer/{trackId}",
                    arguments = listOf(navArgument("trackId") { type = NavType.StringType })
                ) { backStackEntry ->
                    currentRoute.value = "musicPlayer"
                    showBottomNav = false
                    
                    // Always use the current track from the manager
                    // The track is already loaded when clicking from music screen
                    val track = currentMusicTrack ?: MusicTrack(
                        videoId = backStackEntry.arguments?.getString("trackId") ?: "",
                        title = "",
                        artist = "",
                        thumbnailUrl = "",
                        duration = 0,
                        sourceUrl = ""
                    )
                    
                    EnhancedMusicPlayerScreen(
                        track = track,
                        onBackClick = { navController.popBackStack() }
                    )
                }



                composable(
                    route = "player/{videoId}",
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                    deepLinks = listOf(
                        navDeepLink {
                            uriPattern = "http://www.youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "https://www.youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "http://youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "https://youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "http://youtu.be/{videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "https://youtu.be/{videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "http://m.youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "https://m.youtube.com/watch?v={videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        }
                    )
                ) { backStackEntry ->
                    currentRoute.value = "player"
                    showBottomNav = false
                    val videoId = backStackEntry.arguments?.getString("videoId")

                    // Use a real YouTube video ID for testing if available
                    // Otherwise use the provided videoId or a known test video
                    val effectiveVideoId = when {
                        !videoId.isNullOrEmpty() && videoId != "sample" -> videoId
                        else -> "jNQXAC9IVRw"  // Default to popular "Me at the zoo" video for testing
                    }

                    // Pass a minimal placeholder Video (id only). Real metadata will be loaded by the player via extractor.
                    val placeholder = Video(
                        id = effectiveVideoId,
                        title = "",
                        channelName = "",
                        channelId = "",
                        thumbnailUrl = "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = "",
                        description = "",
                        channelThumbnailUrl = ""
                    )

                    EnhancedVideoPlayerScreen(
                        video = placeholder,
                        onBack = { 
                            // Show mini player when exiting
                            GlobalPlayerState.showMiniPlayer()
                            navController.popBackStack() 
                        },
                        onVideoClick = { video ->
                            navController.navigate("player/${video.id}") {
                                popUpTo("player/{videoId}") { inclusive = true }
                            }
                        },
                        onChannelClick = { channelId ->
                            // Construct a URL for the channel since the route expects one
                            val channelUrl = "https://www.youtube.com/channel/$channelId"
                            val encodedUrl = java.net.URLEncoder.encode(channelUrl, "UTF-8")
                            navController.navigate("channel?url=$encodedUrl")
                        }
                    )
                }
            }
        }
    }
}
