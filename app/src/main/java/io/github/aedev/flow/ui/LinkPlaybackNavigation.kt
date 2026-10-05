package io.github.aedev.flow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.state.shouldExpandInsteadOfPlaying

/** Routes a link from another app opens to start playback; each plays, then leaves at once. */
internal fun NavGraphBuilder.linkPlaybackRoutes(
    navController: NavHostController,
    currentRoute: MutableState<String>,
    playerViewModel: VideoPlayerViewModel,
    playerUiStateResult: State<VideoPlayerUiState>,
    playerSheetState: PlayerDraggableState,
    playerVisibleState: MutableState<Boolean>,
    defaultStartRoute: String,
    onMusicStarted: () -> Unit,
) {
    // A YouTube Music link: the song plays in the music player, and this route leaves at once.
    composable(
        route = MUSIC_PLAYER_ROUTE_PATTERN,
        arguments = listOf(navArgument(MUSIC_PLAYER_ROUTE_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        currentRoute.value = "musicPlayer"
        val musicPlayerViewModel = sharedMusicPlayerViewModel()
        val videoId = backStackEntry.arguments?.getString(MUSIC_PLAYER_ROUTE_ARG).orEmpty()

        LaunchedEffect(videoId) {
            // The music player stays hidden while a video is loaded, so a music link closes it first.
            if (playerViewModel.uiState.value.cachedVideo != null) GlobalPlayerState.requestDismiss()
            musicPlayerViewModel.playFromLink(videoId)
            onMusicStarted()
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }
    }

    composable(
        route = "player/{videoId}",
        arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
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
            if (!playerUiState.shouldExpandInsteadOfPlaying(effectiveVideoId)) {
                val video =
                    playerUiState.cachedVideo?.takeIf { it.id == effectiveVideoId }
                        ?: Video(
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
                playerViewModel.playVideo(video)
                GlobalPlayerState.setCurrentVideo(video)
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
