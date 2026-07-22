package io.github.aedev.flow.ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.components.TvNavigationRail
import io.github.aedev.flow.ui.tv.navigation.TvDestination
import io.github.aedev.flow.ui.tv.screens.TvHomeScreen
import io.github.aedev.flow.ui.tv.screens.TvLibraryScreen
import io.github.aedev.flow.ui.tv.screens.TvPlayerScreen
import io.github.aedev.flow.ui.tv.screens.TvSearchScreen
import io.github.aedev.flow.ui.tv.screens.TvSettingsScreen
import io.github.aedev.flow.ui.tv.screens.TvSubscriptionsScreen

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FlowTvApp(
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    onDeeplinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val homeViewModel: HomeViewModel = hiltViewModel(activity)
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)
    val subscriptionsViewModel: SubscriptionsViewModel = viewModel(viewModelStoreOwner = activity)
    val searchViewModel: SearchViewModel = viewModel(viewModelStoreOwner = activity)
    var destination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    val activeVideo by GlobalPlayerState.currentVideo.collectAsStateWithLifecycle()
    val appFocusRequester = remember { FocusRequester() }
    var restoreAppFocus by remember { mutableStateOf(true) }

    fun play(video: Video) {
        GlobalPlayerState.setCurrentVideo(video)
        playerViewModel.playVideo(video)
    }

    LaunchedEffect(deeplinkVideoId) {
        val videoId = deeplinkVideoId ?: return@LaunchedEffect
        play(
            Video(
                id = videoId,
                title = "",
                channelName = "",
                channelId = "",
                thumbnailUrl = "",
                duration = 0,
                viewCount = 0L,
                uploadDate = "",
                isShort = isShort,
            )
        )
        onDeeplinkConsumed()
    }

    LaunchedEffect(activeVideo, restoreAppFocus) {
        if (activeVideo == null && restoreAppFocus) {
            appFocusRequester.requestFocus()
            restoreAppFocus = false
        }
    }

    val backDestination = destination.backDestination()
    BackHandler(enabled = activeVideo == null && backDestination != null) {
        destination = backDestination ?: TvDestination.HOME
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer()
                .focusRequester(appFocusRequester)
                .focusProperties { canFocus = activeVideo == null },
        ) {
            TvNavigationRail(
                selected = destination,
                onSelected = { destination = it },
            )
            when (destination) {
                TvDestination.HOME -> TvHomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = ::play,
                    modifier = Modifier.weight(1f),
                )
                TvDestination.SUBSCRIPTIONS -> TvSubscriptionsScreen(
                    viewModel = subscriptionsViewModel,
                    onVideoClick = ::play,
                    modifier = Modifier.weight(1f),
                )
                TvDestination.SEARCH -> TvSearchScreen(
                    viewModel = searchViewModel,
                    onVideoClick = ::play,
                    modifier = Modifier.weight(1f),
                )
                TvDestination.LIBRARY -> TvLibraryScreen(
                    onVideoClick = ::play,
                    modifier = Modifier.weight(1f),
                )
                TvDestination.SETTINGS -> TvSettingsScreen(
                    modifier = Modifier.weight(1f),
                )
            }
        }

        activeVideo?.let { video ->
            TvPlayerScreen(
                video = video,
                viewModel = playerViewModel,
                onClose = {
                    playerViewModel.clearVideo()
                    GlobalPlayerState.setCurrentVideo(null)
                    restoreAppFocus = true
                },
            )
        }
    }
}
