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
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.screens.sync.SyncScreen
import io.github.aedev.flow.ui.tv.screens.TvArtistScreen
import io.github.aedev.flow.ui.tv.screens.TvChannelScreen
import io.github.aedev.flow.ui.tv.screens.TvHomeScreen
import io.github.aedev.flow.ui.tv.screens.TvLibraryScreen
import io.github.aedev.flow.ui.tv.screens.TvMusicCollectionScreen
import io.github.aedev.flow.ui.tv.screens.TvMusicScreen
import io.github.aedev.flow.ui.tv.screens.TvPlaylistDetailScreen
import io.github.aedev.flow.ui.tv.screens.TvSearchScreen
import io.github.aedev.flow.ui.tv.screens.TvSettingsScreen
import io.github.aedev.flow.ui.tv.screens.TvSubscriptionsScreen

/** Top-level TV navigation graph plus detail routes (channel, …). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    musicViewModel: MusicViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    subscriptionsViewModel: SubscriptionsViewModel,
    searchViewModel: SearchViewModel,
    onPlayVideo: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openChannel: (String) -> Unit = { channelRef ->
        navController.navigate(TvRoutes.channel(channelRef))
    }

    NavHost(
        navController = navController,
        startDestination = TvDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(TvDestination.HOME.route) {
            TvHomeScreen(
                viewModel = homeViewModel,
                onVideoClick = onPlayVideo,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvDestination.MUSIC.route) {
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
        ) { entry ->
            val collectionId = entry.arguments?.getString(TvRoutes.MUSIC_COLLECTION_ARG).orEmpty()
            TvMusicCollectionScreen(
                collectionId = collectionId,
                viewModel = musicViewModel,
                onTrackClick = musicPlayerViewModel::loadAndPlayTrack,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvDestination.SUBSCRIPTIONS.route) {
            TvSubscriptionsScreen(
                viewModel = subscriptionsViewModel,
                onVideoClick = onPlayVideo,
                onChannelClick = openChannel,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvDestination.SEARCH.route) {
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
        composable(TvDestination.LIBRARY.route) {
            TvLibraryScreen(
                onVideoClick = onPlayVideo,
                onOpenPlaylist = { navController.navigate(TvRoutes.playlist(it)) },
                onPlayTrack = musicPlayerViewModel::loadAndPlayTrack,
                onOpenMusicCollection = { navController.navigate(TvRoutes.musicCollection(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvDestination.SETTINGS.route) {
            TvSettingsScreen(
                onOpenSync = { navController.navigate(TvRoutes.SYNC) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(TvRoutes.SYNC) {
            SyncScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = TvRoutes.CHANNEL,
            arguments = listOf(
                navArgument(TvRoutes.CHANNEL_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val channelRef = entry.arguments?.getString(TvRoutes.CHANNEL_ARG)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                .orEmpty()
            TvChannelScreen(
                channelUrl = channelRef,
                onVideoClick = onPlayVideo,
                onOpenPlaylist = { navController.navigate(TvRoutes.playlist(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = TvRoutes.PLAYLIST,
            arguments = listOf(navArgument(TvRoutes.PLAYLIST_ARG) { type = NavType.StringType }),
        ) {
            TvPlaylistDetailScreen(
                onVideoClick = onPlayVideo,
                onPlayPlaylist = onPlayPlaylist,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
