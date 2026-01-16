package com.flow.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
import com.flow.youtube.ui.screens.music.EnhancedMusicScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicPlayerScreen
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicPlayerViewModel
import com.flow.youtube.ui.screens.music.ArtistPage
import com.flow.youtube.ui.screens.music.MusicViewModel
import com.flow.youtube.ui.screens.player.EnhancedVideoPlayerScreen
import com.flow.youtube.ui.screens.search.SearchScreen
import com.flow.youtube.ui.screens.settings.SettingsScreen
import com.flow.youtube.ui.screens.personality.FlowPersonalityScreen
import com.flow.youtube.ui.screens.shorts.ShortsScreen
import com.flow.youtube.ui.screens.subscriptions.SubscriptionsScreen
import com.flow.youtube.ui.screens.channel.ChannelScreen
import com.flow.youtube.ui.theme.ThemeMode
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    
     // Offline Monitoring
    val currentRoute = remember { mutableStateOf("home") }
    LaunchedEffect(Unit) {
        while (true) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            if (!hasInternet) {
                // If offline and not in downloads or player (which supports offline), redirect to downloads
                val route = currentRoute.value
                val isSafeRoute = route == "downloads" || 
                                  route?.startsWith("player") == true || 
                                  route?.startsWith("musicPlayer") == true ||
                                  route == "settings"
                
                if (!isSafeRoute) {
                    navController.navigate("downloads") {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            }
            delay(5000) // Check every 5 seconds
        }
    }
    
    var selectedBottomNavIndex by remember { mutableIntStateOf(0) }
    var showBottomNav by remember { mutableStateOf(true) }
    
    // Observer global player state
    val isMiniPlayerVisible by GlobalPlayerState.isMiniPlayerVisible.collectAsState()
    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    
    // Observe music player state
    val currentMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()

    // Handle deep links and notifications
    val activity = context as? com.flow.youtube.MainActivity
    val deeplinkVideoId by activity?.deeplinkVideoId ?: remember { mutableStateOf(null) }
    
    LaunchedEffect(deeplinkVideoId) {
        if (deeplinkVideoId != null) {
            navController.navigate("player/$deeplinkVideoId")
            activity?.consumeDeeplink()
        }
    }

    // Navigate to player if entering PiP from another screen
    LaunchedEffect(isInPipMode) {
        if (isInPipMode && !currentRoute.value.startsWith("player") && currentVideo != null) {
            navController.navigate("player/${currentVideo!!.id}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isInPipMode) androidx.compose.ui.graphics.Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isInPipMode) {
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
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(if (isInPipMode) PaddingValues(0.dp) else paddingValues)) {
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                    currentRoute.value = "home"
                    showBottomNav = true
                    selectedBottomNavIndex = 0
                    HomeScreen(
                        onVideoClick = { video ->
                            if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
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
                            if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
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
                    showBottomNav = false
                    selectedBottomNavIndex = 1
                    val startVideoId = backStackEntry.arguments?.getString("startVideoId")
                    ShortsScreen(
                        startVideoId = startVideoId,
                        onBack = {
                            navController.popBackStack()
                        },
                        onChannelClick = { channelId ->
                            navController.navigate("channel?url=$channelId")
                        }
                    )
                }

                composable("subscriptions") {
                    currentRoute.value = "subscriptions"
                    showBottomNav = true
                    selectedBottomNavIndex = 3
                    SubscriptionsScreen(
                        onVideoClick = { video ->
                            if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
                        },
                        onShortClick = { videoId ->
                            navController.navigate("shorts?startVideoId=$videoId")
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
                        onNavigateToMusicPlaylists = {
                            navController.navigate("musicPlaylists")
                        },
                        onNavigateToLikedVideos = { 
                            navController.navigate("likedVideos")
                        },
                        onNavigateToWatchLater = {
                            navController.navigate("watchLater")
                        },
                        onNavigateToSavedShorts = {
                            navController.navigate("savedShorts")
                        },
                        onNavigateToDownloads = {
                            navController.navigate("downloads")
                        },
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
                            if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
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
                        onNavigateToAppearance = { navController.navigate("settings/appearance") },
                        onNavigateToDonations = { navController.navigate("donations") },
                        onNavigateToPersonality = { navController.navigate("personality") }
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

                composable("donations") {
                    currentRoute.value = "donations"
                    showBottomNav = false
                    com.flow.youtube.ui.screens.settings.DonationsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("personality") {
                    currentRoute.value = "personality"
                    showBottomNav = false
                    FlowPersonalityScreen(
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
                        onVideoClick = { video ->
                            if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
                        },
                        onShortClick = { videoId ->
                            navController.navigate("shorts?startVideoId=$videoId")
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate("playlist/$playlistId")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // History Screen
                composable("history") {
                    currentRoute.value = "history"
                    showBottomNav = false
                    HistoryScreen(
                        onVideoClick = { track ->
                            if (track.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${track.videoId}")
                            } else {
                                navController.navigate("player/${track.videoId}")
                            }
                        },
                        onBackClick = { navController.popBackStack() },
                        onArtistClick = { channelId ->
                            navController.navigate("channel?url=$channelId")
                        }
                    )
                }

                // Liked Videos Screen
                composable("likedVideos") {
                    currentRoute.value = "likedVideos"
                    showBottomNav = false
                    LikedVideosScreen(
                        onVideoClick = { track ->
                            if (track.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${track.videoId}")
                            } else {
                                navController.navigate("player/${track.videoId}")
                            }
                        },
                        onBackClick = { navController.popBackStack() },
                        onArtistClick = { channelId ->
                            navController.navigate("channel?url=$channelId")
                        }
                    )
                }

                // Watch Later Screen
                composable("watchLater") {
                    currentRoute.value = "watchLater"
                    showBottomNav = false
                    WatchLaterScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { video ->
                            if (video.isMusic) {
                                navController.navigate("musicPlayer/${video.id}")
                            } else if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
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

                // Music Playlists Screen
                composable("musicPlaylists") {
                    currentRoute.value = "musicPlaylists"
                    showBottomNav = false
                    com.flow.youtube.ui.screens.music.MusicPlaylistsScreen(
                        onBackClick = { navController.popBackStack() },
                        onPlaylistClick = { playlist ->
                            navController.navigate("musicPlaylist/${playlist.id}")
                        },
                        onNavigateToLikedMusic = { navController.navigate("likedMusic") },
                        onNavigateToMusicHistory = { navController.navigate("musicHistory") }
                    )
                }

                composable("likedMusic") {
                    currentRoute.value = "likedMusic"
                    showBottomNav = false
                    LikedVideosScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { track ->
                            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(track.title)
                            val encodedArtist = android.net.Uri.encode(track.artist)
                            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        },
                        isMusic = true,
                        onArtistClick = { channelId ->
                            navController.navigate("artist/$channelId")
                        }
                    )
                }

                composable("musicHistory") {
                    currentRoute.value = "musicHistory"
                    showBottomNav = false
                    HistoryScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { track ->
                            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(track.title)
                            val encodedArtist = android.net.Uri.encode(track.artist)
                            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        },
                        isMusic = true,
                        onArtistClick = { channelId ->
                            navController.navigate("artist/$channelId")
                        }
                    )
                }

                // Playlist Detail Screen
                composable("playlist/{playlistId}") { _ ->
                    currentRoute.value = "playlist"
                    showBottomNav = false
                    PlaylistDetailScreen(
                        // playlistId is handled by ViewModel via SavedStateHandle
                        // playlistRepository is injected by Hilt
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            if (video.isMusic) {
                                navController.navigate("musicPlayer/${video.id}")
                            } else if (video.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}")
                            }
                        }
                    )
                }

                // Saved Shorts Grid
                composable("savedShorts") {
                    currentRoute.value = "savedShorts"
                    showBottomNav = false
                    com.flow.youtube.ui.screens.library.SavedShortsGridScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { videoId ->
                            navController.navigate("savedShortsPlayer/$videoId")
                        }
                    )
                }

                // Saved Shorts Player
                composable(
                    route = "savedShortsPlayer/{startVideoId}",
                    arguments = listOf(navArgument("startVideoId") { type = NavType.StringType })
                ) { backStackEntry ->
                    currentRoute.value = "savedShortsPlayer"
                    showBottomNav = false
                    val startVideoId = backStackEntry.arguments?.getString("startVideoId")
                    ShortsScreen(
                        startVideoId = startVideoId,
                        isSavedMode = true,
                        onBack = {
                            navController.popBackStack()
                        },
                        onChannelClick = { channelId ->
                            navController.navigate("channel?url=$channelId")
                        }
                    )
                }
                composable("downloads") {
                    currentRoute.value = "downloads"
                    showBottomNav = false
                    
                    // Inject MusicPlayerViewModel here to handle queue playback
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    com.flow.youtube.ui.screens.library.DownloadsScreen(
                        onBackClick = { navController.popBackStack() },
                        onVideoClick = { videoId ->
                            navController.navigate("player/$videoId")
                        },
                        onMusicClick = { tracks, index ->
                            // Convert DownloadedTrack to MusicTrack
                            val musicTracks = tracks.map { it.track }
                            val selectedTrack = musicTracks[index]
                            
                            // Load and play queue
                            musicPlayerViewModel.loadAndPlayTrack(selectedTrack, musicTracks, "Downloads")
                            
                            val encodedUrl = android.net.Uri.encode(selectedTrack.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(selectedTrack.title)
                            val encodedArtist = android.net.Uri.encode(selectedTrack.artist)
                            navController.navigate("musicPlayer/${selectedTrack.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        }
                    )
                }
                // Music Screen - Enhanced with SoundCloud
                composable("music") {
                    currentRoute.value = "music"
                    showBottomNav = true
                    selectedBottomNavIndex = 2
                    
                    // Get the MusicPlayerViewModel from this composable context
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    EnhancedMusicScreen(
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { track, queue, source ->
                            // Load and play the track with the provided queue
                            musicPlayerViewModel.loadAndPlayTrack(track, queue, source)
                            
                            // Navigate to player
                            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(track.title)
                            val encodedArtist = android.net.Uri.encode(track.artist)
                            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        },
                        onVideoClick = { track ->
                            if (track.duration in 1..80) {
                                navController.navigate("shorts?startVideoId=${track.videoId}")
                            } else {
                                navController.navigate("player/${track.videoId}")
                            }
                        },
                        onArtistClick = { channelId ->
                            navController.navigate("artist/$channelId")
                        },
                        onSearchClick = {
                            navController.navigate("musicSearch")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        },
                        onAlbumClick = { albumId ->
                            navController.navigate("musicPlaylist/$albumId")
                        }
                    )
                }

                // Music Search Screen
                composable("musicSearch") {
                    currentRoute.value = "musicSearch"
                    showBottomNav = false
                    
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    com.flow.youtube.ui.screens.music.MusicSearchScreen(
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = { track, queue, source ->
                            musicPlayerViewModel.loadAndPlayTrack(track, queue, source)
                            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(track.title)
                            val encodedArtist = android.net.Uri.encode(track.artist)
                            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        },
                        onAlbumClick = { albumId ->
                            navController.navigate("musicPlaylist/$albumId")
                        },
                        onArtistClick = { channelId ->
                            navController.navigate("artist/$channelId")
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate("musicPlaylist/$playlistId")
                        }
                    )
                }
                
                // Library Screen - Playlists, Favorites, Downloads
                composable("musicLibrary") {
                    currentRoute.value = "musicLibrary"
                    showBottomNav = false
                    
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    
                    com.flow.youtube.ui.screens.music.LibraryScreen(
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = { track, queue ->
                            musicPlayerViewModel.loadAndPlayTrack(track, queue)
                            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                            val encodedTitle = android.net.Uri.encode(track.title)
                            val encodedArtist = android.net.Uri.encode(track.artist)
                            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                        }
                    )
                }

                // Artist Page
                composable("artist/{channelId}") { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
                    val musicViewModel: MusicViewModel = hiltViewModel()
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    val uiState by musicViewModel.uiState.collectAsState()
                    
                    LaunchedEffect(channelId) {
                        musicViewModel.fetchArtistDetails(channelId)
                    }
                    
                    if (uiState.isArtistLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        uiState.artistDetails?.let { details ->
                            ArtistPage(
                                artistDetails = details,
                                onBackClick = { navController.popBackStack() },
                                onTrackClick = { track, queue ->
                                    musicPlayerViewModel.loadAndPlayTrack(track, queue)
                                    val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                                    val encodedTitle = android.net.Uri.encode(track.title)
                                    val encodedArtist = android.net.Uri.encode(track.artist)
                                    navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                                },
                                onAlbumClick = { album ->
                                    navController.navigate("musicPlaylist/${album.id}")
                                },
                                onArtistClick = { id ->
                                    navController.navigate("artist/$id")
                                },
                                onFollowClick = {
                                    musicViewModel.toggleFollowArtist(details)
                                }
                            )
                        }
                    }
                }

                // Music Playlist Page
                composable("musicPlaylist/{playlistId}") { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                    val musicViewModel: MusicViewModel = hiltViewModel()
                    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
                    val uiState by musicViewModel.uiState.collectAsState()
                    
                    LaunchedEffect(playlistId) {
                        // Handle community playlists - map genre to tracks
                        if (playlistId.startsWith("community_")) {
                            val genre = playlistId.substringAfter("community_")
                            musicViewModel.loadCommunityPlaylist(genre)
                        } else {
                            musicViewModel.fetchPlaylistDetails(playlistId)
                        }
                    }
                    
                    if (uiState.isPlaylistLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        uiState.playlistDetails?.let { details ->
                            com.flow.youtube.ui.screens.music.PlaylistPage(
                                playlistDetails = details,
                                onBackClick = { navController.popBackStack() },
                                onTrackClick = { track, queue ->
                                    musicPlayerViewModel.loadAndPlayTrack(track, queue)
                                    val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                                    val encodedTitle = android.net.Uri.encode(track.title)
                                    val encodedArtist = android.net.Uri.encode(track.artist)
                                    navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
                                },
                                onArtistClick = { channelId ->
                                    navController.navigate("artist/$channelId")
                                }
                            )
                        }
                    }
                }

                // Music Player Screen - Enhanced
                composable(
                    route = "musicPlayer/{trackId}?title={title}&artist={artist}&thumbnailUrl={thumbnailUrl}",
                    arguments = listOf(
                        navArgument("trackId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("artist") { type = NavType.StringType; defaultValue = "" },
                        navArgument("thumbnailUrl") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    currentRoute.value = "musicPlayer"
                    showBottomNav = false
                    
                    val trackId = backStackEntry.arguments?.getString("trackId") ?: ""
                    val title = backStackEntry.arguments?.getString("title") ?: ""
                    val artist = backStackEntry.arguments?.getString("artist") ?: ""
                    val thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl") ?: ""
                    
                    val managerTrack = EnhancedMusicPlayerManager.currentTrack.value
                    
                    val track = if (managerTrack?.videoId == trackId) {
                        managerTrack
                    } else {
                        MusicTrack(
                            videoId = trackId,
                            title = title,
                            artist = artist,
                            thumbnailUrl = thumbnailUrl,
                            duration = 0,
                            sourceUrl = ""
                        )
                    }
                    
                    EnhancedMusicPlayerScreen(
                        track = track,
                        onBackClick = { navController.popBackStack() },
                        onArtistClick = { channelId ->
                            navController.navigate("artist/$channelId")
                        }
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
                        },
                        navDeepLink {
                            uriPattern = "https://www.youtube.com/shorts/{videoId}"
                            action = android.content.Intent.ACTION_VIEW
                        },
                        navDeepLink {
                            uriPattern = "https://youtube.com/shorts/{videoId}"
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
                            if (video.duration <= 80) {
                                navController.navigate("shorts?startVideoId=${video.id}")
                            } else {
                                navController.navigate("player/${video.id}") {
                                    popUpTo("player/{videoId}") { inclusive = true }
                                }
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
}