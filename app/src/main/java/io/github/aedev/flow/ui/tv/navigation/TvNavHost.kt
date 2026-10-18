package io.github.aedev.flow.ui.tv.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.toMusicTrack
import io.github.aedev.flow.data.music.model.toVideo
import io.github.aedev.flow.ui.components.layout.navigation.FlowTab
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.screens.channel.ChannelScreen
import io.github.aedev.flow.ui.screens.history.HistoryScreen
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.playlists.PlaylistDetailScreen
import io.github.aedev.flow.ui.screens.playlists.PlaylistsScreen
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.components.TvScreenFrame
import io.github.aedev.flow.ui.tv.screens.TvArtistScreen
import io.github.aedev.flow.ui.tv.screens.TvHomeScreen
import io.github.aedev.flow.ui.tv.screens.TvLibraryScreen
import io.github.aedev.flow.ui.tv.screens.TvMusicCollectionScreen
import io.github.aedev.flow.ui.tv.screens.TvMusicScreen
import io.github.aedev.flow.ui.tv.screens.TvRemoteGuideScreen
import io.github.aedev.flow.ui.tv.screens.TvSearchScreen
import io.github.aedev.flow.ui.tv.screens.TvSettingsScreen
import io.github.aedev.flow.ui.tv.screens.TvSubscriptionsScreen
import io.github.aedev.flow.ui.tv.screens.TvSyncScreen

/** Top-level TV navigation graph plus detail routes (channel, …). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvNavHost(
    navController: NavHostController,
    startRoute: String,
    homeViewModel: HomeViewModel,
    musicViewModel: MusicViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    subscriptionsViewModel: SubscriptionsViewModel,
    searchViewModel: SearchViewModel,
    onPlayVideo: (Video) -> Unit,
    onPlayPlaylist: (videos: List<Video>, startIndex: Int, title: String, shuffle: Boolean) -> Unit,
    onPlayVideoId: (String) -> Unit,
    onSelectRoot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openChannel: (String) -> Unit = { channelRef ->
        navController.navigate(TvRoutes.channel(channelRef))
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(FlowTab.Home.route) {
            TvHomeScreen(
                viewModel = homeViewModel,
                onVideoClick = onPlayVideo,
                onPlayPlaylist = { videos, title -> onPlayPlaylist(videos, 0, title, false) },
                onOpenLibrary = { onSelectRoot(FlowTab.Library.route) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FlowTab.Music.route) {
            TvMusicScreen(
                viewModel = musicViewModel,
                onTrackClick = musicPlayerViewModel::loadAndPlayTrack,
                onOpenCollection = { navController.navigate(TvRoutes.musicCollection(it)) },
                onOpenArtist = { navController.navigate(TvRoutes.musicArtist(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = TvRoutes.MUSIC_ARTIST,
            arguments = listOf(navArgument(TvRoutes.MUSIC_ARTIST_ARG) { type = NavType.StringType }),
        ) { entry ->
            val artistChannelId = entry.arguments?.getString(TvRoutes.MUSIC_ARTIST_ARG).orEmpty()
            TvArtistScreen(
                channelId = artistChannelId,
                viewModel = musicViewModel,
                onTrackClick = musicPlayerViewModel::loadAndPlayTrack,
                onOpenCollection = { navController.navigate(TvRoutes.musicCollection(it)) },
                onOpenArtist = { navController.navigate(TvRoutes.musicArtist(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = TvRoutes.MUSIC_COLLECTION,
            arguments = listOf(navArgument(TvRoutes.MUSIC_COLLECTION_ARG) { type = NavType.StringType }),
        ) {
            TvMusicCollectionScreen(
                onTrackClick = musicPlayerViewModel::loadAndPlayTrack,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FlowTab.Subscriptions.route) {
            TvSubscriptionsScreen(
                viewModel = subscriptionsViewModel,
                onVideoClick = onPlayVideo,
                onChannelClick = { channel -> openChannel(channel.url.ifBlank { channel.id }) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FlowTab.Search.route) {
            TvSearchScreen(
                viewModel = searchViewModel,
                onVideoClick = onPlayVideo,
                onChannelClick = openChannel,
                onOpenPlaylist = { navController.navigate(TvRoutes.playlist(it)) },
                onPlayTrack = musicPlayerViewModel::loadAndPlayTrack,
                onOpenMusicCollection = { navController.navigate(TvRoutes.musicCollection(it)) },
                onOpenMusicArtist = { navController.navigate(TvRoutes.musicArtist(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FlowTab.Library.route) {
            TvLibraryScreen(
                onVideoClick = onPlayVideo,
                onMusicClick = musicPlayerViewModel::loadAndPlayTrack,
                onOpenHistory = { navController.navigate(TvRoutes.HISTORY) },
                onOpenPlaylists = { kind -> navController.navigate(TvRoutes.playlists(kind)) },
                onOpenPlaylist = { navController.navigate(TvRoutes.playlist(it)) },
                onOpenMusicPlaylist = { navController.navigate(TvRoutes.musicCollection(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvRoutes.HISTORY) {
            TvScreenFrame {
                HistoryScreen(
                    onVideoClick = { track -> onPlayVideo(track.toVideo()) },
                    onBackClick = { navController.popBackStack() },
                    onShortsQueue = {},
                    onMusicClick = { track, queue -> musicPlayerViewModel.loadAndPlayTrack(track, queue) },
                )
            }
        }
        composable(
            route = TvRoutes.PLAYLISTS,
            arguments =
                listOf(
                    navArgument(TvRoutes.PLAYLISTS_KIND_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { entry ->
            val kind =
                entry.arguments?.getString(TvRoutes.PLAYLISTS_KIND_ARG)?.let { name ->
                    MediaKind.entries.firstOrNull {
                        it.name ==
                            name
                    }
                }
            TvScreenFrame {
                PlaylistsScreen(
                    fixedKind = kind,
                    onBackClick = { navController.popBackStack() },
                    onVideoPlaylistClick = { navController.navigate(TvRoutes.playlist(it.id)) },
                    onMusicPlaylistClick = { navController.navigate(TvRoutes.musicCollection(it.id)) },
                )
            }
        }
        composable(TV_SETTINGS_ROUTE) {
            TvSettingsScreen(
                onOpenSync = { navController.navigate(TvRoutes.SYNC) },
                onOpenRemoteGuide = { navController.navigate(TvRoutes.REMOTE_GUIDE) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvRoutes.SYNC) {
            TvSyncScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(TvRoutes.REMOTE_GUIDE) {
            TvRemoteGuideScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = TvRoutes.CHANNEL,
            arguments =
                listOf(
                    navArgument(TvRoutes.CHANNEL_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { entry ->
            val channelRef =
                entry.arguments
                    ?.getString(TvRoutes.CHANNEL_ARG)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()
            TvScreenFrame {
                ChannelScreen(
                    channelUrl = channelRef,
                    onVideoClick = onPlayVideo,
                    onChannelClick = openChannel,
                    onShortClick = { videoId, _ -> onPlayVideoId(videoId) },
                    onPlaylistClick = { navController.navigate(TvRoutes.playlist(it)) },
                    onBackClick = { navController.popBackStack() },
                    showShorts = false,
                )
            }
        }
        composable(
            route = TvRoutes.PLAYLIST,
            arguments = listOf(navArgument(TvRoutes.PLAYLIST_ARG) { type = NavType.StringType }),
        ) {
            TvScreenFrame {
                PlaylistDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPlayPlaylist = { videos, index, shuffle, title ->
                        val start = videos[index]
                        if (start.isMusic) {
                            val tracks = videos.filter { it.isMusic }.map { it.toMusicTrack() }
                            musicPlayerViewModel.loadAndPlayTrack(start.toMusicTrack(), tracks, title)
                        } else {
                            onPlayPlaylist(videos, index, title, shuffle)
                        }
                    },
                )
            }
        }
    }
}
