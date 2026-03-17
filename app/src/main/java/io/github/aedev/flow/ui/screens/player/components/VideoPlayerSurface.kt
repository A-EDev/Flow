package io.github.aedev.flow.ui.screens.player.components

import android.util.Log
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
        Log.d("EnhancedVideoPlayer", "Creating shared PlayerView")
        PlayerView(context).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            // Set background to black to avoid white flash during transitions
            setBackgroundColor(android.graphics.Color.BLACK)
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

    DisposableEffect(Unit) {
        val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
        val callback = if (surfaceView != null) {
            object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    Log.d("EnhancedVideoPlayer", "Surface created for video ${video.id}")
                    EnhancedPlayerManager.getInstance().attachVideoSurface(holder, forceAttach = true)
                }

                override fun surfaceChanged(
                    holder: android.view.SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    // Surface resized but still valid
                }

                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    Log.d("EnhancedVideoPlayer", "Surface destroyed for video ${video.id}")
                    EnhancedPlayerManager.getInstance().detachVideoSurface(holder)
                }
            }.also { surfaceView.holder.addCallback(it) }
        } else null

        onDispose {
            callback?.let { surfaceView?.holder?.removeCallback(it) }
        }
    }

    AndroidView(
        factory = { playerView },
        update = { view ->
            val newPlayer = EnhancedPlayerManager.getInstance().getPlayer()
            val oldPlayer = view.player
            if (oldPlayer !== newPlayer) {
                oldPlayer?.removeListener(videoSizeListener)
                newPlayer?.addListener(videoSizeListener)
            }

            view.player = newPlayer

            // Apply resize mode
            view.resizeMode = when (resizeMode) {
                0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            
            if (!EnhancedPlayerManager.getInstance().isSurfaceReady) {
                try {
                    val surfaceView = view.videoSurfaceView
                    if (surfaceView is android.view.SurfaceView) {
                        val holder = surfaceView.holder
                        val surface = holder.surface
                        if (surface != null && surface.isValid) {
                            EnhancedPlayerManager.getInstance().attachVideoSurface(holder)
                            Log.d("EnhancedVideoPlayer", "Surface reattached in update block (fallback - was not ready)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("EnhancedVideoPlayer", "Failed to reattach surface in update fallback", e)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
