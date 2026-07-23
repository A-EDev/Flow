package io.github.aedev.flow.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.music.TvMusicNowPlayingScreen
import io.github.aedev.flow.ui.tv.screens.TvPlayerScreen
import io.github.aedev.flow.ui.tv.theme.TvTheme

@androidx.annotation.OptIn(UnstableApi::class)
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
    val musicViewModel: MusicViewModel = hiltViewModel(activity)
    val musicPlayerViewModel: MusicPlayerViewModel = hiltViewModel(activity)
    val navController = rememberNavController()
    val activeVideo by GlobalPlayerState.currentVideo.collectAsStateWithLifecycle()
    val activeMusicTrack by EnhancedMusicPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val musicPlayerState by EnhancedMusicPlayerManager.playerState.collectAsStateWithLifecycle()
    var musicExpanded by rememberSaveable { mutableStateOf(false) }

    // The manager restores the last session's track (paused) at startup. Only
    // surface the mini player once something has actually played this session.
    var musicSessionActive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(musicPlayerState.isPlaying) {
        if (musicPlayerState.isPlaying) musicSessionActive = true
    }

    fun play(video: Video) {
        EnhancedMusicPlayerManager.pause()
        GlobalPlayerState.setCurrentVideo(video)
        playerViewModel.playVideo(video)
    }

    fun playPlaylist(videos: List<Video>, title: String) {
        val first = videos.firstOrNull() ?: return
        EnhancedMusicPlayerManager.pause()
        GlobalPlayerState.setCurrentVideo(first)
        playerViewModel.playPlaylist(videos, startIndex = 0, title = title)
    }

    LaunchedEffect(deeplinkVideoId) {
        val videoId = deeplinkVideoId ?: return@LaunchedEffect
        // Metadata is resolved by playVideo -> loadVideoInfo; the stub only seeds the id.
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

    TvTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val video = activeVideo
            val visibleMusicTrack = activeMusicTrack.takeIf { musicSessionActive }
            if (video == null && musicExpanded && visibleMusicTrack != null) {
                TvMusicNowPlayingScreen(
                    viewModel = musicPlayerViewModel,
                    onCollapse = { musicExpanded = false },
                )
            } else if (video == null) {
                TvShell(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    musicViewModel = musicViewModel,
                    musicPlayerViewModel = musicPlayerViewModel,
                    subscriptionsViewModel = subscriptionsViewModel,
                    searchViewModel = searchViewModel,
                    onPlayVideo = ::play,
                    onPlayPlaylist = ::playPlaylist,
                    activeMusicTrack = visibleMusicTrack,
                    onExpandMusic = { musicExpanded = true },
                    onDismissMusic = {
                        musicExpanded = false
                        musicSessionActive = false
                        EnhancedMusicPlayerManager.clearCurrentTrack()
                    },
                )
            } else {
                TvPlayerScreen(
                    video = video,
                    viewModel = playerViewModel,
                    onClose = {
                        playerViewModel.clearVideo()
                        GlobalPlayerState.setCurrentVideo(null)
                    },
                )
            }
        }
    }
}
