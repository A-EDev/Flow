package com.flow.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.components.PlayerSheetValue
import com.flow.youtube.ui.components.PlayerDraggableState
import com.flow.youtube.ui.screens.home.HomeScreen
import com.flow.youtube.ui.screens.history.HistoryScreen
import com.flow.youtube.ui.screens.library.LibraryScreen
import com.flow.youtube.ui.screens.library.WatchLaterScreen
import com.flow.youtube.ui.screens.likedvideos.LikedVideosScreen
import com.flow.youtube.ui.screens.playlists.PlaylistsScreen
import com.flow.youtube.ui.screens.playlists.PlaylistDetailScreen
import com.flow.youtube.ui.screens.notifications.NotificationScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicScreen
import com.flow.youtube.ui.screens.music.EnhancedMusicPlayerScreen
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicPlayerViewModel
import com.flow.youtube.ui.screens.music.ArtistPage
import com.flow.youtube.ui.screens.music.MusicViewModel
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.search.SearchScreen
import com.flow.youtube.ui.screens.settings.SettingsScreen
import com.flow.youtube.ui.screens.settings.ImportDataScreen
import com.flow.youtube.ui.screens.personality.FlowPersonalityScreen
import com.flow.youtube.ui.screens.shorts.ShortsScreen
import com.flow.youtube.ui.screens.subscriptions.SubscriptionsScreen
import com.flow.youtube.ui.screens.channel.ChannelScreen
import com.flow.youtube.ui.screens.onboarding.OnboardingScreen
import com.flow.youtube.ui.theme.ThemeMode
import androidx.media3.common.util.UnstableApi
import java.net.URLEncoder

@UnstableApi
fun NavGraphBuilder.flowAppGraph(
    navController: NavHostController,
    currentRoute: MutableState<String>,
    showBottomNav: MutableState<Boolean>,
    selectedBottomNavIndex: MutableIntState,
    playerSheetState: PlayerDraggableState,
    playerViewModel: VideoPlayerViewModel,
    playerUiStateResult: State<VideoPlayerUiState>, 
    playerVisibleState: MutableState<Boolean>, 
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    // =============================================
    // ONBOARDING (First-time user experience)
    // =============================================
    composable("onboarding") {
        currentRoute.value = "onboarding"
        showBottomNav.value = false
        OnboardingScreen(
            onComplete = {
                // Navigate to home and clear the backstack so user can't go back to onboarding
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        )
    }
    
    composable("home") {
        currentRoute.value = "home"
        showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
        selectedBottomNavIndex.intValue = 0
        HomeScreen(
            onVideoClick = { video ->
                if (video.duration in 1..80) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    playerViewModel.playVideo(video)
                    GlobalPlayerState.setCurrentVideo(video)
                }
            },
            onShortClick = { video ->
                navController.navigate("shorts?startVideoId=${video.id}")
            },
            onSearchClick = {
                navController.navigate("search")
            },
            onNotificationClick = {
                navController.navigate("notifications")
            },
            onSettingsClick = {
                navController.navigate("settings")
            },
            onChannelClick = { channelId ->
                val encodedUrl = java.net.URLEncoder.encode("https://www.youtube.com/channel/$channelId", "UTF-8")
                navController.navigate("channel?url=$encodedUrl")
            }
        )
    }

    // Notifications Screen
    composable("notifications") {
        currentRoute.value = "notifications"
        showBottomNav.value = false
        NotificationScreen(
            onBackClick = { navController.popBackStack() },
            onNotificationClick = { videoId ->
                navController.navigate("player/$videoId")
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
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 1
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
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 3
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
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 4
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
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = -1
        SearchScreen(
            onVideoClick = { video ->
                if (video.duration in 1..80) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onChannelClick = { channel ->
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
        showBottomNav.value = false
        SettingsScreen(
            currentTheme = currentTheme,

            onNavigateBack = { navController.popBackStack() },
            onNavigateToAppearance = { navController.navigate("settings/appearance") },
            onNavigateToPlayerAppearance = { navController.navigate("settings/player_appearance") },
            onNavigateToDonations = { navController.navigate("donations") },
            onNavigateToPersonality = { navController.navigate("personality") },
            onNavigateToDownloads = { navController.navigate("settings/downloads") },
            onNavigateToTimeManagement = { navController.navigate("settings/time_management") },
            onNavigateToImport = { navController.navigate("settings/import") },
            onNavigateToPlayerSettings = { navController.navigate("settings/player") },
            onNavigateToVideoQuality = { navController.navigate("settings/video_quality") },
            onNavigateToShortsQuality = { navController.navigate("settings/shorts_quality") },
            onNavigateToContentSettings = { navController.navigate("settings/content") },
            onNavigateToBufferSettings = { navController.navigate("settings/buffer") },
            onNavigateToSearchHistory = { navController.navigate("settings/search_history") },
            onNavigateToAbout = { navController.navigate("settings/about") },
            onNavigateToUserPreferences = { navController.navigate("settings/user_preferences") }
        )
    }

    composable("settings/user_preferences") {
        currentRoute.value = "settings/user_preferences"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.UserPreferencesScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/player") {
        currentRoute.value = "settings/player"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.PlayerSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/buffer") {
        currentRoute.value = "settings/buffer"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.BufferSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/search_history") {
        currentRoute.value = "settings/search_history"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.SearchHistorySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/video_quality") {
        currentRoute.value = "settings/video_quality"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.VideoQualitySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/shorts_quality") {
        currentRoute.value = "settings/shorts_quality"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.ShortsVideoQualitySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/content") {
        currentRoute.value = "settings/content"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.ContentSettingsScreen(
            onBackClick = { navController.popBackStack() }
        )
    }
    
    composable("settings/import") {
        currentRoute.value = "settings/import"
        ImportDataScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/time_management") {
        currentRoute.value = "settings/time_management"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.TimeManagementScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/about") {
        currentRoute.value = "settings/about"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.AboutScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDonations = { navController.navigate("donations") }
        )
    }

    composable("settings/appearance") {
        currentRoute.value = "settings/appearance"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.AppearanceScreen(
            currentTheme = currentTheme,
            onThemeChange = onThemeChange,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/player_appearance") {
        currentRoute.value = "settings/player_appearance"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.PlayerAppearanceScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/downloads") {
        currentRoute.value = "settings/downloads"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.DownloadSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("donations") {
        currentRoute.value = "donations"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.settings.DonationsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("personality") {
        currentRoute.value = "personality"
        showBottomNav.value = false
        FlowPersonalityScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable(
        route = "channel?url={channelUrl}",
        arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
    ) { backStackEntry ->
        currentRoute.value = "channel"
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
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
            },
            onPlayPlaylist = { videos, index ->
                playerViewModel.playPlaylist(videos, index, "Watch Later")
            }
        )
    }

    // Playlists Screen
    composable("playlists") {
        currentRoute.value = "playlists"
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
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
            },
            onPlayPlaylist = { videos, index ->
                playerViewModel.playPlaylist(videos, index, "Playlist")
            }
        )
    }

    // Saved Shorts Grid
    composable("savedShorts") {
        currentRoute.value = "savedShorts"
        showBottomNav.value = false
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
        showBottomNav.value = false
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
        showBottomNav.value = false
        
        val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
        
        com.flow.youtube.ui.screens.library.DownloadsScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { videoId ->
                navController.navigate("player/$videoId")
            },
            onMusicClick = { tracks, index ->
                val musicTracks = tracks.map { it.track }
                val selectedTrack = musicTracks[index]
                
                musicPlayerViewModel.loadAndPlayTrack(selectedTrack, musicTracks, "Downloads")
                
                val encodedUrl = android.net.Uri.encode(selectedTrack.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(selectedTrack.title)
                val encodedArtist = android.net.Uri.encode(selectedTrack.artist)
                navController.navigate("musicPlayer/${selectedTrack.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onHomeClick = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            }
        )
    }
    composable("music") {
        currentRoute.value = "music"
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 2
        
        val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
        
        EnhancedMusicScreen(
            onBackClick = { navController.popBackStack() },
            onSongClick = { track, queue, source ->
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
            },
            onMoodsClick = { item ->
                if (item != null) {
                    // Navigate to browse screen with browseId and params for proper content fetching
                    val encodedParams = android.net.Uri.encode(item.endpoint.params ?: "")
                    navController.navigate("youtube_browse/${item.endpoint.browseId}?params=$encodedParams")
                } else {
                    navController.navigate("moodsAndGenres")
                }
            }
        )
    }

    composable("moodsAndGenres") {
        currentRoute.value = "moodsAndGenres"
        showBottomNav.value = false
        com.flow.youtube.ui.screens.music.MoodsAndGenresScreen(
            onBackClick = { navController.popBackStack() },
            onGenreClick = { item ->
                val encodedParams = android.net.Uri.encode(item.endpoint.params ?: "")
                navController.navigate("youtube_browse/${item.endpoint.browseId}?params=$encodedParams")
            }
        )
    }

    // Music Search Screen
    composable("musicSearch") {
        currentRoute.value = "musicSearch"
        showBottomNav.value = false
        
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
    
    // YouTube Browse Screen (for mood/genre content)
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType },
            navArgument("params") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            }
        )
    ) {
        currentRoute.value = "youtube_browse"
        showBottomNav.value = false
        
        val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
        
        com.flow.youtube.ui.screens.music.YouTubeBrowseScreen(
            onBackClick = { navController.popBackStack() },
            onSongClick = { song ->
                val track = com.flow.youtube.ui.screens.music.MusicTrack(
                    videoId = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.thumbnail,
                    duration = song.duration ?: 0,
                    album = song.album?.name ?: "",
                    channelId = song.artists.firstOrNull()?.id ?: ""
                )
                musicPlayerViewModel.loadAndPlayTrack(track, emptyList())
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
    
    composable("musicLibrary") {
        currentRoute.value = "musicLibrary"
        showBottomNav.value = false
        
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
                    },
                    onSeeAllClick = { browseId, params ->
                         val encodedParams = if (params != null) android.net.Uri.encode(params) else null
                         navController.navigate("artistItems/$channelId/$browseId?params=$encodedParams")
                    }
                )
            }
        }
    }

    // Artist Items Page (View All)
    composable(
        "artistItems/{channelId}/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType },
            navArgument("params") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
             navArgument("channelId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
        val params = backStackEntry.arguments?.getString("params")
        // channelId is available if needed contextually
        
        val musicViewModel: MusicViewModel = hiltViewModel()
        val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()

        com.flow.youtube.ui.screens.music.ArtistItemsScreen(
            browseId = browseId,
            params = params,
            onBackClick = { navController.popBackStack() },
            viewModel = musicViewModel,
            onTrackClick = { songItem ->
                val track = com.flow.youtube.ui.screens.music.MusicTrack(
                    videoId = songItem.id,
                    title = songItem.title,
                    artist = songItem.artists.joinToString(", ") { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    duration = songItem.duration ?: 0
                )
                musicPlayerViewModel.loadAndPlayTrack(track, listOf(track))
                val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(track.title)
                val encodedArtist = android.net.Uri.encode(track.artist)
                navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onAlbumClick = { albumId ->
                 navController.navigate("musicPlaylist/$albumId")
            },
            onArtistClick = { id ->
                 navController.navigate("artist/$id")
            },
             onPlaylistClick = { playlistId ->
                navController.navigate("musicPlaylist/$playlistId")
            }
        )
    }

    // Music Playlist Page
    composable("musicPlaylist/{playlistId}") { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
        val musicViewModel: MusicViewModel = hiltViewModel()
        val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel()
        val uiState by musicViewModel.uiState.collectAsState()
        
        LaunchedEffect(playlistId) {
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
        showBottomNav.value = false
        
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
        val videoId = backStackEntry.arguments?.getString("videoId")
        val effectiveVideoId = when {
            !videoId.isNullOrEmpty() && videoId != "sample" -> videoId
            else -> "jNQXAC9IVRw"
        }

        // Use passed state
        val playerUiState = playerUiStateResult.value
        val playerVisible = playerVisibleState.value

        LaunchedEffect(effectiveVideoId) {
            if (playerUiState.cachedVideo?.id != effectiveVideoId || !playerVisible) {
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
                playerViewModel.playVideo(placeholder)
                GlobalPlayerState.setCurrentVideo(placeholder)
            } else {
                playerSheetState.expand()
            }
            navController.popBackStack()
        }
        
        Box(modifier = Modifier.fillMaxSize())
    }
}