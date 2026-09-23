package io.github.aedev.flow.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.data.shorts.queue.openAtVideoId
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.musicplayer.MusicPlayerSheetState
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import io.github.aedev.flow.ui.screens.channel.ChannelScreen
import io.github.aedev.flow.ui.screens.history.HistoryScreen
import io.github.aedev.flow.ui.screens.home.HomeScreen
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.library.LibraryScreen
import io.github.aedev.flow.ui.screens.likedvideos.LikesScreen
import io.github.aedev.flow.ui.screens.music.ArtistPage
import io.github.aedev.flow.ui.screens.music.EnhancedMusicScreen
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.ui.screens.notifications.NotificationScreen
import io.github.aedev.flow.ui.screens.onboarding.OnboardingScreen
import io.github.aedev.flow.ui.screens.personality.FlowPersonalityScreen
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.playlists.PlaylistDetailScreen
import io.github.aedev.flow.ui.screens.playlists.PlaylistsScreen
import io.github.aedev.flow.ui.screens.search.SearchScreen
import io.github.aedev.flow.ui.screens.settings.SettingsHost
import io.github.aedev.flow.ui.screens.shorts.ShortsScreen
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsScreen
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant

@UnstableApi
fun NavGraphBuilder.flowAppGraph(
    navController: NavHostController,
    currentRoute: MutableState<String>,
    playerSheetState: PlayerDraggableState,
    musicPlayerSheetState: MusicPlayerSheetState,
    homeViewModel: HomeViewModel,
    playerViewModel: VideoPlayerViewModel,
    playerUiStateResult: State<VideoPlayerUiState>,
    playerVisibleState: MutableState<Boolean>,
    currentTheme: ThemeMode,
    themeVariant: ThemeVariant,
    customThemePalettes: CustomThemePalettes,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant,
    onThemeChange: (ThemeMode) -> Unit,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    onCustomThemePalettesChange: (CustomThemePalettes) -> Unit,
    onSystemLightThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeVariantChange: (ThemeVariant) -> Unit,
    disableShortsPlayer: Boolean = false,
    defaultStartRoute: String = "home",
    /**
     * Read lazily inside the destination that needs it. Destination lambdas are captured once
     * when NavHost remembers the graph, so a by-value Dp here is frozen at graph-construction
     * time and never reflects the bar showing or hiding.
     */
    bottomNavOverlayPadding: () -> Dp = { 0.dp },
) {
    // =============================================
    // ONBOARDING (First-time user experience)
    // =============================================
    composable("onboarding") {
        currentRoute.value = "onboarding"
        OnboardingScreen(
            onComplete = {
                // Navigate to the selected default tab and clear the backstack so user can't go back to onboarding
                navController.navigate(defaultStartRoute) {
                    popUpTo("onboarding") { inclusive = true }
                }
            },
        )
    }

    composable("home") {
        currentRoute.value = "home"
        HomeScreen(
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) {
                    playerViewModel.playVideo(it)
                    GlobalPlayerState.setCurrentVideo(it)
                }
            },
            onShortClick = { source ->
                navController.openShortsOrPlayer(source, disableShortsPlayer)
            },
            onSearchClick = {
                navController.navigate("search")
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
            onNavigateToHistory = {
                navController.navigate("history")
            },
            onOpenShortsFeed = {
                navController.openShorts(ShortsQueueSource.Feed)
            },
            viewModel = homeViewModel,
        )
    }

    // Notifications Screen
    composable("notifications") {
        currentRoute.value = "notifications"
        NotificationScreen(
            onBackClick = { navController.popBackStack() },
            onNotificationClick = { videoId ->
                navController.navigateToPlayer(videoId)
            },
        )
    }

    composable(
        route = SHORTS_ROUTE_PATTERN,
        arguments =
            listOf(
                navArgument(SHORTS_ROUTE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        currentRoute.value = SHORTS_ROUTE_KEY
        val source = ShortsQueueSource.decode(backStackEntry.arguments?.getString(SHORTS_ROUTE_ARG))
        val isRootTab = source == ShortsQueueSource.Feed
        ShortsScreen(
            source = source,
            bottomNavOverlayPadding = if (isRootTab) bottomNavOverlayPadding() else 0.dp,
            onBack = {
                navController.popBackStack()
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
        )
    }

    composable("subscriptions") {
        currentRoute.value = "subscriptions"
        SubscriptionsScreen(
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) {
                    playerViewModel.playVideo(it)
                    GlobalPlayerState.setCurrentVideo(it)
                }
            },
            onShortClick = { source ->
                navController.openShortsOrPlayer(source, disableShortsPlayer)
            },
            onChannelClick = { channel ->
                if (channel.isMusic && channel.id.isNotBlank()) {
                    navController.navigate("artist/${channel.id}")
                } else {
                    navController.navigateToYoutubeChannel(channel.url.ifBlank { channel.id })
                }
            },
        )
    }

    composable("library") {
        currentRoute.value = "library"
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val downloadsSourceName =
            androidx.compose.ui.res.stringResource(
                io.github.aedev.flow.R.string.library_downloads_label,
            )
        LibraryScreen(
            onNavigateToHistory = {
                navController.navigate("history")
            },
            onNavigateToPlaylists = {
                navController.navigate("playlists")
            },
            onNavigateToLikedVideos = {
                navController.navigate("likes")
            },
            onNavigateToWatchLater = {
                navController.navigate("playlist/${PlaylistRepository.WATCH_LATER_ID}")
            },
            onNavigateToSavedShorts = {
                navController.navigate("savedShorts")
            },
            onNavigateToDownloads = {
                navController.navigate("downloads")
            },
            onNavigateToLocalMedia = {
                navController.navigate("localMedia")
            },
            onManageData = {
                navController.navigate("settings")
            },
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) { navController.navigateToPlayer(it.id) }
            },
            onMusicClick = { track, queue, sourceName ->
                musicPlayerViewModel.loadAndPlayTrack(track, queue, sourceName)
                val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(track.title)
                val encodedArtist = android.net.Uri.encode(track.artist)
                navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onPlaylistClick = { playlistId ->
                navController.navigate("playlist/$playlistId")
            },
            onMusicPlaylistClick = { playlistId ->
                navController.navigate("musicPlaylist/$playlistId")
            },
            onDownloadedVideoClick = { videos, index ->
                val videoList = videos.map { it.video }
                playerViewModel.playPlaylist(videoList, index, downloadsSourceName)
                GlobalPlayerState.setCurrentVideo(videoList[index])
            },
            onDownloadedMusicClick = { tracks, index ->
                val musicTracks = tracks.map { it.track }
                val selectedTrack = musicTracks[index]
                musicPlayerViewModel.loadAndPlayTrack(selectedTrack, musicTracks, downloadsSourceName)
                val encodedUrl = android.net.Uri.encode(selectedTrack.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(selectedTrack.title)
                val encodedArtist = android.net.Uri.encode(selectedTrack.artist)
                navController.navigate(
                    "musicPlayer/${selectedTrack.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl",
                )
            },
            onSavedShortClick = { video ->
                navController.openShortsOrPlayer(ShortsQueueSource.Saved(video.id), disableShortsPlayer)
            },
        )
    }

    composable("search") {
        currentRoute.value = "search"
        // Search owns the whole screen, the way YouTube's does.
        SearchScreen(
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) { navController.navigateToPlayer(it.id) }
            },
            onShortsQueue = { source ->
                navController.openShortsOrPlayer(source, disableShortsPlayer)
            },
            onChannelClick = { channel ->
                navController.navigateToYoutubeChannel(channel.url.ifBlank { channel.id })
            },
            onPlaylistClick = { playlist ->
                navController.navigate("playlist/${playlist.id}")
            },
            onBack = {
                if (!navController.popBackStack()) navController.navigate("home")
            },
        )
    }

    composable("categories") {
        currentRoute.value = "categories"
        io.github.aedev.flow.ui.screens.categories.CategoriesScreen(
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) { navController.navigateToPlayer(it.id) }
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
            onShortClick = { videoId ->
                navController.openShortsOrPlayer(ShortsQueueSource.SeededFeed(videoId), disableShortsPlayer)
            },
            onPlaylistClick = { playlistId ->
                navController.navigate("playlist/$playlistId")
            },
        )
    }

    composable(
        route = "settings?target={target}",
        arguments =
            listOf(
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        currentRoute.value = "settings"
        SettingsHost(
            start = SettingsTarget.decode(backStackEntry.arguments?.getString("target")),
            onExit = { navController.popBackStack() },
            onOpenPersona = { navController.navigate("personality") },
            onOpenDonations = { navController.navigate("donations") },
        )
    }

    composable("donations") {
        currentRoute.value = "donations"
        io.github.aedev.flow.ui.screens.settings.DonationsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable("personality") {
        currentRoute.value = "personality"
        FlowPersonalityScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(
        route = "channel?url={channelUrl}",
        arguments = listOf(navArgument("channelUrl") { type = NavType.StringType }),
    ) { backStackEntry ->
        currentRoute.value = "channel"
        val channelUrl =
            backStackEntry.arguments?.getString("channelUrl")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""

        ChannelScreen(
            channelUrl = channelUrl,
            onVideoClick = { video ->
                navController.openVideoOrShorts(video, disableShortsPlayer) { navController.navigateToPlayer(it.id) }
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
            onShortClick = { videoId, sortIndex ->
                navController.openShortsOrPlayer(
                    ShortsQueueSource.Channel(channelUrl = channelUrl, startVideoId = videoId, sortIndex = sortIndex),
                    disableShortsPlayer,
                )
            },
            onPlaylistClick = { playlistId ->
                navController.navigate("playlist/$playlistId")
            },
            onBackClick = { navController.popBackStack() },
        )
    }

    // History Screen
    composable("history") {
        currentRoute.value = "history"
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        HistoryScreen(
            onVideoClick = { track ->
                val localId = track.videoId.removePrefix("local_").toLongOrNull()
                if (track.videoId.startsWith("local_") && localId != null) {
                    val uri =
                        android.content.ContentUris
                            .withAppendedId(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                localId,
                            ).toString()
                    val video =
                        io.github.aedev.flow.data.model.Video(
                            id = track.videoId,
                            title = track.title,
                            channelName = track.artist,
                            channelId = "local",
                            thumbnailUrl = uri,
                            duration = track.duration,
                            viewCount = 0,
                            uploadDate = "",
                            description = "",
                        )
                    playerViewModel.playLocalVideo(video, uri)
                    GlobalPlayerState.setCurrentVideo(video)
                } else {
                    navController.navigateToPlayer(track.videoId)
                }
            },
            onShortsQueue = { source ->
                navController.openShortsOrPlayer(source, disableShortsPlayer)
            },
            onMusicClick = { track, queue ->
                if (track.videoId.startsWith("local_")) {
                    val localTracks = queue.filter { it.videoId.startsWith("local_") }.ifEmpty { listOf(track) }
                    val localUris =
                        localTracks
                            .mapNotNull { t ->
                                t.videoId.removePrefix("local_").toLongOrNull()?.let { id ->
                                    t.videoId to
                                        android.content.ContentUris.withAppendedId(
                                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            id,
                                        )
                                }
                            }.toMap()
                    musicPlayerViewModel.playLocalMusic(track, localTracks, localUris)
                } else {
                    musicPlayerViewModel.loadAndPlayTrack(track, queue, "History")
                }
                val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(track.title)
                val encodedArtist = android.net.Uri.encode(track.artist)
                navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onBackClick = { navController.popBackStack() },
        )
    }

    // Likes Screen
    composable("likes") {
        currentRoute.value = "likes"
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        LikesScreen(
            onVideoClick = { track ->
                navController.navigateToPlayer(track.videoId)
            },
            onMusicClick = { track, queue ->
                musicPlayerViewModel.loadAndPlayTrack(track, queue, "Likes")
                val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(track.title)
                val encodedArtist = android.net.Uri.encode(track.artist)
                navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onBackClick = { navController.popBackStack() },
        )
    }

    // Playlists Screen
    composable("playlists") {
        currentRoute.value = "playlists"
        PlaylistsScreen(
            onBackClick = { navController.popBackStack() },
            onVideoPlaylistClick = { playlist ->
                navController.navigate("playlist/${playlist.id}")
            },
            onMusicPlaylistClick = { playlist ->
                navController.navigate("musicPlaylist/${playlist.id}")
            },
        )
    }

    // Playlist Detail Screen
    composable("playlist/{playlistId}") { _ ->
        currentRoute.value = "playlist"
        PlaylistDetailScreen(
            // playlistId is handled by ViewModel via SavedStateHandle
            // playlistRepository is injected by Hilt
            onNavigateBack = { navController.popBackStack() },
            onVideoClick = { video ->
                if (video.isMusic) {
                    navController.navigate("musicPlayer/${video.id}")
                } else {
                    navController.openVideoOrShorts(video, disableShortsPlayer) { navController.navigateToPlayer(it.id) }
                }
            },
            onPlayPlaylist = { videos, index ->
                playerViewModel.playPlaylist(videos, index, "Playlist")
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
        )
    }

    // Saved Shorts Grid
    composable("savedShorts") {
        currentRoute.value = "savedShorts"
        io.github.aedev.flow.ui.screens.library.SavedShortsGridScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { videoId ->
                navController.openShortsOrPlayer(ShortsQueueSource.Saved(videoId), disableShortsPlayer)
            },
        )
    }

    composable("downloads") {
        currentRoute.value = "downloads"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        io.github.aedev.flow.ui.screens.library.DownloadsScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { videos, index ->
                val videoList = videos.map { it.video }
                playerViewModel.playPlaylist(videoList, index, "Downloads")
                GlobalPlayerState.setCurrentVideo(videoList[index])
            },
            onMusicClick = { tracks, index ->
                val musicTracks = tracks.map { it.track }
                val selectedTrack = musicTracks[index]

                musicPlayerViewModel.loadAndPlayTrack(selectedTrack, musicTracks, "Downloads")

                val encodedUrl = android.net.Uri.encode(selectedTrack.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(selectedTrack.title)
                val encodedArtist = android.net.Uri.encode(selectedTrack.artist)
                navController.navigate(
                    "musicPlayer/${selectedTrack.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl",
                )
            },
            onHomeClick = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            },
        )
    }
    composable("localMedia") {
        currentRoute.value = "localMedia"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        io.github.aedev.flow.ui.screens.library.LocalMediaScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { item ->
                val video =
                    io.github.aedev.flow.data.model.Video(
                        id =
                            io.github.aedev.flow.ui.screens.library.LocalMediaViewModel
                                .localMediaId(item),
                        title = item.title,
                        channelName = item.subtitle.ifBlank { "Local video" },
                        channelId = "local",
                        thumbnailUrl = item.contentUri,
                        duration = (item.durationMs / 1000).toInt(),
                        viewCount = 0,
                        uploadDate = "",
                        description = "",
                    )
                playerViewModel.playLocalVideo(video, item.contentUri)
                GlobalPlayerState.setCurrentVideo(video)
            },
            onMusicClick = { items, index ->
                val tracks =
                    items.map { item ->
                        MusicTrack(
                            videoId =
                                io.github.aedev.flow.ui.screens.library.LocalMediaViewModel
                                    .localMediaId(item),
                            title = item.title,
                            artist = item.subtitle.ifBlank { "Local audio" },
                            thumbnailUrl = item.artworkUri ?: "",
                            duration = (item.durationMs / 1000).toInt(),
                        )
                    }
                val localUris =
                    items.associate { item ->
                        io.github.aedev.flow.ui.screens.library.LocalMediaViewModel
                            .localMediaId(item) to
                            android.net.Uri.parse(item.contentUri)
                    }
                val selected = tracks[index]
                musicPlayerViewModel.playLocalMusic(selected, tracks, localUris)

                val encodedTitle = android.net.Uri.encode(selected.title)
                val encodedArtist = android.net.Uri.encode(selected.artist)
                val encodedUrl = android.net.Uri.encode(selected.thumbnailUrl)
                navController.navigate("musicPlayer/${selected.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
        )
    }
    composable("music") {
        currentRoute.value = "music"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        EnhancedMusicScreen(
            bottomNavOverlayPadding = bottomNavOverlayPadding,
            onSongClick = { track, queue, source ->
                musicPlayerViewModel.loadAndPlayTrack(track, queue, source)

                // Navigate to player
                val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                val encodedTitle = android.net.Uri.encode(track.title)
                val encodedArtist = android.net.Uri.encode(track.artist)
                navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
            },
            onVideoClick = { track ->
                navController.navigateToPlayer(track.videoId)
            },
            onArtistClick = { channelId ->
                navController.navigate("artist/$channelId")
            },
            onSearchClick = {
                navController.navigate("musicSearch")
            },
            onRecognizeClick = {
                navController.navigate("musicRecognize")
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
            },
        )
    }

    composable("moodsAndGenres") {
        currentRoute.value = "moodsAndGenres"
        io.github.aedev.flow.ui.screens.music.MoodsAndGenresScreen(
            onBackClick = { navController.popBackStack() },
            onGenreClick = { item ->
                val encodedParams = android.net.Uri.encode(item.endpoint.params ?: "")
                navController.navigate("youtube_browse/${item.endpoint.browseId}?params=$encodedParams")
            },
        )
    }

    // Music Search Screen
    composable(
        route = "musicSearch?query={query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        currentRoute.value = "musicSearch"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val initialQuery = backStackEntry.arguments?.getString("query")

        io.github.aedev.flow.ui.screens.music.MusicSearchScreen(
            initialQuery = initialQuery,
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
            },
        )
    }

    // Music Recognition (Shazam) Screen
    composable("musicRecognize") {
        currentRoute.value = "musicRecognize"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        fun playRecognized(result: io.github.aedev.flow.data.recognition.RecognitionResult) {
            val track =
                io.github.aedev.flow.ui.screens.recognition.RecognitionViewModel
                    .toMusicTrack(result) ?: return
            musicPlayerViewModel.loadAndPlayTrack(track, listOf(track), "Recognized")
            val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
            val encodedTitle = android.net.Uri.encode(track.title)
            val encodedArtist = android.net.Uri.encode(track.artist)
            navController.navigate("musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl")
        }

        fun searchRecognized(
            title: String,
            artist: String,
        ) {
            val query =
                io.github.aedev.flow.ui.screens.recognition.RecognitionViewModel
                    .searchQueryFor(title, artist)
            navController.navigate("musicSearch?query=${android.net.Uri.encode(query)}")
        }

        io.github.aedev.flow.ui.screens.recognition.RecognitionScreen(
            onBackClick = { navController.popBackStack() },
            onHistoryClick = { navController.navigate("recognitionHistory") },
            onPlay = { result -> playRecognized(result) },
            onSearch = { result -> searchRecognized(result.title, result.artist) },
        )
    }

    // Music Recognition History Screen
    composable("recognitionHistory") {
        currentRoute.value = "recognitionHistory"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        io.github.aedev.flow.ui.screens.recognition.RecognitionHistoryScreen(
            onBackClick = { navController.popBackStack() },
            onItemClick = { item ->
                val videoId = item.youtubeVideoId
                if (!videoId.isNullOrBlank()) {
                    val track =
                        MusicTrack(
                            videoId = videoId,
                            title = item.title,
                            artist = item.artist,
                            thumbnailUrl = item.coverArtHqUrl ?: item.coverArtUrl ?: "",
                            duration = 0,
                            album = item.album.orEmpty(),
                        )
                    musicPlayerViewModel.loadAndPlayTrack(track, listOf(track), "Recognized")
                    val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                    val encodedTitle = android.net.Uri.encode(track.title)
                    val encodedArtist = android.net.Uri.encode(track.artist)
                    navController.navigate(
                        "musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl",
                    )
                } else {
                    val query =
                        io.github.aedev.flow.ui.screens.recognition.RecognitionViewModel
                            .searchQueryFor(item.title, item.artist)
                    navController.navigate("musicSearch?query=${android.net.Uri.encode(query)}")
                }
            },
        )
    }

    // YouTube Browse Screen (for mood/genre content)
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) {
        currentRoute.value = "youtube_browse"

        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        io.github.aedev.flow.ui.screens.music.YouTubeBrowseScreen(
            onBackClick = { navController.popBackStack() },
            onSongClick = { song ->
                val track =
                    MusicTrack(
                        videoId = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        thumbnailUrl = song.thumbnail,
                        duration = song.duration ?: 0,
                        album = song.album?.name ?: "",
                        channelId = song.artists.firstOrNull()?.id ?: "",
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
            },
        )
    }

    // Artist Page
    composable("artist/{channelId}") { backStackEntry ->
        val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
        val musicViewModel: MusicViewModel =
            io.github.aedev.flow.ui.screens.music
                .sharedMusicViewModel()
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
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
                    downloadedTrackIds = uiState.downloadedTrackIds,
                    insights = uiState.artistInsights,
                    knownRelatedArtistIds = uiState.knownRelatedArtistIds,
                    onBackClick = { navController.popBackStack() },
                    onTrackClick = { track, queue ->
                        musicPlayerViewModel.loadAndPlayTrack(track, queue)
                        val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                        val encodedTitle = android.net.Uri.encode(track.title)
                        val encodedArtist = android.net.Uri.encode(track.artist)
                        navController.navigate(
                            "musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl",
                        )
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
                    },
                )
            }
        }
    }

    // Artist Items Page (View All)
    composable(
        "artistItems/{channelId}/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("channelId") { type = NavType.StringType },
            ),
    ) { backStackEntry ->
        val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
        val params = backStackEntry.arguments?.getString("params")
        // channelId is available if needed contextually

        val musicViewModel: MusicViewModel =
            io.github.aedev.flow.ui.screens.music
                .sharedMusicViewModel()
        val musicPlayerViewModel = sharedMusicPlayerViewModel()

        io.github.aedev.flow.ui.screens.music.ArtistItemsScreen(
            browseId = browseId,
            params = params,
            onBackClick = { navController.popBackStack() },
            viewModel = musicViewModel,
            onTrackClick = { songItem ->
                val track =
                    MusicTrack(
                        videoId = songItem.id,
                        title = songItem.title,
                        artist = songItem.artists.joinToString(", ") { it.name },
                        thumbnailUrl = songItem.thumbnail,
                        duration = songItem.duration ?: 0,
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
            },
        )
    }

    // Music Playlist Page
    composable("musicPlaylist/{playlistId}") { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
        val musicViewModel: MusicViewModel =
            io.github.aedev.flow.ui.screens.music
                .sharedMusicViewModel()
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val musicPlaylistsViewModel: io.github.aedev.flow.ui.screens.music.MusicPlaylistsViewModel = hiltViewModel()
        val uiState by musicViewModel.uiState.collectAsState()
        val isSaved by musicPlaylistsViewModel.isSavedPlaylist.collectAsState()

        LaunchedEffect(playlistId) {
            if (playlistId.startsWith("community_")) {
                val genre = playlistId.substringAfter("community_")
                musicViewModel.loadCommunityPlaylist(genre)
            } else if (playlistId.startsWith(MusicViewModel.DAILY_MIX_ID_PREFIX)) {
                musicViewModel.loadDailyMixPage(playlistId)
            } else {
                musicViewModel.fetchPlaylistDetails(playlistId)
            }
        }

        val isUserPlaylist =
            playlistId.matches(
                Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            )

        LaunchedEffect(playlistId, isUserPlaylist) {
            if (!isUserPlaylist) {
                musicPlaylistsViewModel.checkIfPlaylistSaved(playlistId)
            }
        }

        if (uiState.isPlaylistLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            uiState.playlistDetails?.let { details ->
                io.github.aedev.flow.ui.screens.music.PlaylistPage(
                    playlistDetails = details,
                    onBackClick = { navController.popBackStack() },
                    onTrackClick = { track, queue ->
                        musicPlayerViewModel.loadAndPlayTrack(track, queue)
                        val encodedUrl = android.net.Uri.encode(track.thumbnailUrl)
                        val encodedTitle = android.net.Uri.encode(track.title)
                        val encodedArtist = android.net.Uri.encode(track.artist)
                        navController.navigate(
                            "musicPlayer/${track.videoId}?title=$encodedTitle&artist=$encodedArtist&thumbnailUrl=$encodedUrl",
                        )
                    },
                    onArtistClick = { channelId ->
                        navController.navigate("artist/$channelId")
                    },
                    onCollectionClick = { navController.navigate("musicPlaylist/$it") },
                    onLoadMore = { musicViewModel.loadMorePlaylistTracks() },
                    isUserPlaylist = isUserPlaylist,
                    isSaved = isSaved,
                    onSaveToggle = {
                        if (isSaved) {
                            musicPlaylistsViewModel.unsavePlaylistFromLibrary(details.id)
                        } else {
                            musicPlaylistsViewModel.savePlaylistToLibrary(details)
                        }
                    },
                )
            }
        }
    }

    // Music Player Screen - now a global draggable overlay.
    composable(
        route = "musicPlayer/{trackId}?title={title}&artist={artist}&thumbnailUrl={thumbnailUrl}",
        arguments =
            listOf(
                navArgument("trackId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("artist") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("thumbnailUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) { backStackEntry ->
        currentRoute.value = "musicPlayer"

        LaunchedEffect(Unit) {
            musicPlayerSheetState.expand()
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }
    }

    composable(
        route = "player/{videoId}",
        arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
        deepLinks =
            listOf(
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
                },
            ),
    ) { backStackEntry ->
        val videoId = backStackEntry.arguments?.getString("videoId")
        val effectiveVideoId =
            when {
                !videoId.isNullOrEmpty() && videoId != "sample" -> videoId
                else -> "jNQXAC9IVRw"
            }

        // Use passed state
        val playerUiState = playerUiStateResult.value
        LaunchedEffect(effectiveVideoId) {
            val isAlreadyPlayingThis =
                playerUiState.cachedVideo?.id == effectiveVideoId &&
                    !playerUiState.isRestoredSession
            if (!isAlreadyPlayingThis) {
                val placeholder =
                    Video(
                        id = effectiveVideoId,
                        title = "",
                        channelName = "",
                        channelId = "",
                        thumbnailUrl = "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = "",
                        description = "",
                        channelThumbnailUrl = "",
                    )
                playerViewModel.playVideo(placeholder)
                GlobalPlayerState.setCurrentVideo(placeholder)
            } else {
                playerViewModel.showVideoPlayer()
                playerVisibleState.value = true
                playerSheetState.expand()
            }
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }

        Box(modifier = Modifier.fillMaxSize())
    }
}

private fun NavHostController.popTransientRouteOrNavigateStart(defaultStartRoute: String) {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(defaultStartRoute) {
            launchSingleTop = true
        }
    }
}
