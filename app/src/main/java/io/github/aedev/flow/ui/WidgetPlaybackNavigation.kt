package io.github.aedev.flow.ui

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.toMusicTrack
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.widgets.WidgetPlaybackViewModel

internal const val ON_REPEAT_SHUFFLE_ROUTE = "onRepeatShuffle"
private const val WIDGET_PLAYLIST_ROUTE = "widgetPlaylist/{playlistId}?shuffle={shuffle}"

internal fun widgetPlaylistRoute(
    playlistId: String,
    shuffle: Boolean,
): String = "widgetPlaylist/${Uri.encode(playlistId)}?shuffle=$shuffle"

/** Routes a home-screen widget opens to start playback; each plays, then leaves at once. */
internal fun NavGraphBuilder.widgetPlaybackRoutes(
    navController: NavHostController,
    currentRoute: MutableState<String>,
    defaultStartRoute: String,
    playerViewModel: VideoPlayerViewModel,
    onMusicStarted: () -> Unit,
) {
    composable(ON_REPEAT_SHUFFLE_ROUTE) {
        currentRoute.value = "musicPlayer"
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val sourceName = stringResource(R.string.widget_on_repeat)
        LaunchedEffect(Unit) {
            // The music player stays hidden while a video is loaded, so it closes first.
            if (playerViewModel.uiState.value.cachedVideo != null) GlobalPlayerState.requestDismiss()
            musicPlayerViewModel.shuffleOnRepeat(sourceName)
            onMusicStarted()
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }
    }

    composable(
        route = WIDGET_PLAYLIST_ROUTE,
        arguments =
            listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("shuffle") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) { entry ->
        currentRoute.value = "musicPlayer"
        val playlistId = entry.arguments?.getString("playlistId").orEmpty()
        val shuffle = entry.arguments?.getBoolean("shuffle") ?: false
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val playlists: WidgetPlaybackViewModel = hiltViewModel()
        LaunchedEffect(playlistId, shuffle) {
            val (playlist, videos) = playlists.playlist(playlistId)
            if (playlist != null && videos.isNotEmpty()) {
                if (playlist.isMusic) {
                    if (playerViewModel.uiState.value.cachedVideo != null) GlobalPlayerState.requestDismiss()
                    val tracks = videos.map { it.toMusicTrack() }.let { if (shuffle) it.shuffled() else it }
                    musicPlayerViewModel.loadAndPlayTrack(tracks.first(), tracks, playlist.name)
                    onMusicStarted()
                } else {
                    playerViewModel.playPlaylist(videos, 0, playlist.name, shuffle)
                }
            }
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }
    }
}

internal fun NavHostController.popTransientRouteOrNavigateStart(defaultStartRoute: String) {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(defaultStartRoute) {
            launchSingleTop = true
        }
    }
}
