package io.github.aedev.flow.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.aedev.flow.R
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel

internal const val ON_REPEAT_SHUFFLE_ROUTE = "onRepeatShuffle"

/** Routes a home-screen widget opens to start music; each plays, then leaves at once. */
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
