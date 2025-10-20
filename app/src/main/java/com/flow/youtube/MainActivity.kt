package com.flow.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flow.youtube.data.local.LocalDataManager
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.components.FloatingBottomNavBar
import com.flow.youtube.ui.components.EnhancedMiniPlayer
import com.flow.youtube.ui.components.PersistentMiniMusicPlayer
import com.flow.youtube.ui.screens.explore.ExploreScreen
import com.flow.youtube.ui.screens.home.HomeScreen
import com.flow.youtube.ui.screens.history.HistoryScreen
import com.flow.youtube.ui.screens.library.LibraryScreen
import com.flow.youtube.ui.screens.likedvideos.LikedVideosScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicPlayerScreen
import com.flow.youtube.ui.screens.music.MusicScreen
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicPlayerViewModel
import com.flow.youtube.ui.screens.player.EnhancedVideoPlayerScreen
import com.flow.youtube.ui.screens.search.SearchScreen
import com.flow.youtube.ui.screens.settings.SettingsScreen
import com.flow.youtube.ui.screens.subscriptions.SubscriptionsScreen
import com.flow.youtube.ui.screens.channel.ChannelScreen
import com.flow.youtube.ui.theme.FlowTheme
import com.flow.youtube.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize global player state
        GlobalPlayerState.initialize(applicationContext)

        val dataManager = LocalDataManager(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(ThemeMode.LIGHT) }

            // Load theme preference
            LaunchedEffect(Unit) {
                themeMode = dataManager.themeMode.first()
            }

            FlowTheme(themeMode = themeMode) {
                FlowApp(
                    currentTheme = themeMode,
                    onThemeChange = { newTheme ->
                        themeMode = newTheme
                        scope.launch {
                            dataManager.setThemeMode(newTheme)
                        }
                    }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release player when app is destroyed
        GlobalPlayerState.release()
    }
}

@Composable
fun FlowApp(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    var selectedBottomNavIndex by remember { mutableStateOf(0) }
    var showBottomNav by remember { mutableStateOf(true) }
    
    // Observe mini player state
    val isMiniPlayerVisible by GlobalPlayerState.isMiniPlayerVisible.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    val currentRoute = remember { mutableStateOf("home") }
    
    // Observe music player state
    val currentMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val musicPlayerState by EnhancedMusicPlayerManager.playerState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
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
                                currentRoute.value = "explore"
                                navController.navigate("explore")
                            }
                            2 -> {
                                currentRoute.value = "subscriptions"
                                navController.navigate("subscriptions")
                            }
                            3 -> {
                                currentRoute.value = "library"
                                navController.navigate("library")
                            }
                            4 -> {
                                currentRoute.value = "search"
                                navController.navigate("search")
                            }
                        }
                    }
                )
            }
            
            // Show enhanced mini player if visible and not on video player screen
            if (isMiniPlayerVisible && currentVideo != null && !currentRoute.value.startsWith("player") && !currentRoute.value.startsWith("musicPlayer")) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                ) {
                    EnhancedMiniPlayer(
                        video = currentVideo!!,
                        onExpandClick = {
                            GlobalPlayerState.hideMiniPlayer()
                            navController.navigate("player/${currentVideo!!.id}")
                        },
                        onCloseClick = {
                            GlobalPlayerState.stop()
                            GlobalPlayerState.hideMiniPlayer()
                        }
                    )
                }
            }
            
            // Show persistent music mini player when music is playing and not on music player screen
            if (currentMusicTrack != null && !currentRoute.value.startsWith("musicPlayer")) {
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
                    onMusicClick = {
                        navController.navigate("music")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("explore") {
                currentRoute.value = "explore"
                showBottomNav = true
                selectedBottomNavIndex = 1
                ExploreScreen(
                    onVideoClick = { video ->
                        navController.navigate("player/${video.id}")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("subscriptions") {
                currentRoute.value = "subscriptions"
                showBottomNav = true
                selectedBottomNavIndex = 2
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
                selectedBottomNavIndex = 3
                LibraryScreen(
                    onNavigateToHistory = { 
                        navController.navigate("history")
                    },
                    onNavigateToPlaylists = { /* Navigate to playlists */ },
                    onNavigateToLikedVideos = { 
                        navController.navigate("likedVideos")
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
                selectedBottomNavIndex = 4
                SearchScreen(
                    onVideoClick = { video ->
                        navController.navigate("player/${video.id}")
                    },
                    onChannelClick = { channel ->
                        val channelUrl = "https://youtube.com/channel/${channel.id}"
                        val encodedUrl = channelUrl.replace("/", "%2F").replace(":", "%3A")
                        navController.navigate("channel?url=$encodedUrl")
                    }
                )
            }

            composable("settings") {
                currentRoute.value = "settings"
                showBottomNav = false
                SettingsScreen(
                    currentTheme = currentTheme,
                    onThemeChange = onThemeChange
                )
            }
            
            composable(
                route = "channel?url={channelUrl}",
                arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                currentRoute.value = "channel"
                showBottomNav = false
                val channelUrl = backStackEntry.arguments?.getString("channelUrl")?.let {
                    it.replace("%2F", "/").replace("%3A", ":")
                } ?: ""
                
                ChannelScreen(
                    channelUrl = channelUrl,
                    onVideoClick = { videoUrl ->
                        // Extract video ID from URL and navigate
                        val videoId = videoUrl.substringAfterLast("/").substringBefore("?")
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

            // Music Screen - Enhanced with SoundCloud
            composable("music") {
                currentRoute.value = "music"
                showBottomNav = false
                
                // Get the MusicPlayerViewModel from this composable context
                val context = androidx.compose.ui.platform.LocalContext.current
                val musicPlayerViewModel: MusicPlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
                LaunchedEffect(Unit) {
                    musicPlayerViewModel.initialize(context)
                }
                
                EnhancedMusicScreen(
                    onBackClick = { navController.popBackStack() },
                    onSongClick = { track ->
                        // Load and play the track immediately
                        musicPlayerViewModel.loadAndPlayTrack(track)
                        
                        // Navigate to player
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
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStackEntry ->
                currentRoute.value = "player"
                showBottomNav = false
                val videoId = backStackEntry.arguments?.getString("videoId")

                // Create sample video for demonstration
                val sampleVideo = Video(
                    id = videoId ?: "sample",
                    title = "Amazing Video Title - Full Tutorial",
                    channelName = "Tech Channel",
                    channelId = "tech_channel",
                    thumbnailUrl = "https://picsum.photos/1280/720",
                    duration = 600,
                    viewCount = 5000000L,
                    uploadDate = "2 days ago",
                    description = "This is a comprehensive tutorial on building modern Android apps with Jetpack Compose. We'll cover everything from basics to advanced topics including state management, navigation, and theming.",
                    channelThumbnailUrl = "https://picsum.photos/200/200"
                )

                EnhancedVideoPlayerScreen(
                    video = sampleVideo,
                    onBack = { 
                        // Show mini player when exiting
                        GlobalPlayerState.showMiniPlayer()
                        navController.popBackStack() 
                    },
                    onVideoClick = { video ->
                        navController.navigate("player/${video.id}") {
                            popUpTo("player/{videoId}") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

