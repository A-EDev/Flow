package io.github.aedev.flow.ui.screens.player.components

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    video: Video,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    onVideoAspectRatioChanged: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    
    val playerView = remember {
        Log.d("EnhancedVideoPlayer", "Creating shared PlayerView (TextureView surface)")
        (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        }
    }
    
    val videoSizeListener = remember {
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    onVideoAspectRatioChanged?.invoke(ratio.coerceIn(0.56f, 2.5f))
                }
            }
        }
    }

    DisposableEffect(playerView) {
        onDispose {
            playerView.player?.removeListener(videoSizeListener)
            playerView.player = null
        }
    }

    AndroidView(
        factory = { playerView },
        update = { view ->
            val manager = EnhancedPlayerManager.getInstance()
            val newPlayer = manager.getPlayer()
            val oldPlayer = view.player
            if (oldPlayer !== newPlayer) {
                oldPlayer?.removeListener(videoSizeListener)
                newPlayer?.addListener(videoSizeListener)
                view.player = newPlayer
                if (newPlayer != null &&
                    newPlayer.playbackState == Player.STATE_IDLE &&
                    newPlayer.currentMediaItem != null
                ) {
                    Log.d("VideoPlayerSurface", "New PlayerView attached; player IDLE with media — calling prepare()")
                    newPlayer.prepare()
                }
            }

            // Apply resize mode
            view.resizeMode = when (resizeMode) {
                0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
