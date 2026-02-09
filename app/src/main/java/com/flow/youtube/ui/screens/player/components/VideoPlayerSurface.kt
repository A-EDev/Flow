package com.flow.youtube.ui.screens.player.components

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
import androidx.media3.ui.PlayerView
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    video: Video,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // ExoPlayer view - create new instance per video to ensure fresh surface
    // This guarantees surfaceCreated() fires for every video switch
    val playerView = remember(video.id) {
        Log.d("EnhancedVideoPlayer", "Creating new PlayerView for video ${video.id}")
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
    
    // Setup surface callback immediately when playerView changes
    DisposableEffect(playerView) {
        val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
        val callback = if (surfaceView != null) {
            object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    Log.d("EnhancedVideoPlayer", "Surface created for video ${video.id}")
                    EnhancedPlayerManager.getInstance().attachVideoSurface(holder)
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
            view.player = EnhancedPlayerManager.getInstance().getPlayer()
            
            // Apply resize mode
            view.resizeMode = when (resizeMode) {
                0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            
            // CRITICAL: Ensure surface is attached whenever this view updates
            // This handles the case where PlayerView is recreated after navigation
            try {
                val surfaceView = view.videoSurfaceView
                if (surfaceView is android.view.SurfaceView) {
                    val holder = surfaceView.holder
                    val surface = holder.surface
                    if (surface != null && surface.isValid) {
                        EnhancedPlayerManager.getInstance().attachVideoSurface(holder)
                        Log.d("EnhancedVideoPlayer", "Surface reattached in update block")
                    }
                }
            } catch (e: Exception) {
                Log.w("EnhancedVideoPlayer", "Failed to reattach surface in update", e)
            }
            
            // Fallback: If surface is still not ready after update, mark it as ready
            // This ensures media loads even if the surface callback doesn't fire properly
            if (!EnhancedPlayerManager.getInstance().isSurfaceReady) {
                try {
                    val surfaceView = view.videoSurfaceView
                    Log.d("EnhancedVideoPlayer", "Fallback check - videoSurfaceView: ${surfaceView?.javaClass?.simpleName}, width=${(surfaceView as? android.view.SurfaceView)?.width}, height=${(surfaceView as? android.view.SurfaceView)?.height}, isSurfaceReady: ${EnhancedPlayerManager.getInstance().isSurfaceReady}")
                    if (surfaceView is android.view.SurfaceView) {
                        EnhancedPlayerManager.getInstance().attachVideoSurface(surfaceView.holder)
                        val holderIsValid = runCatching { surfaceView.holder.surface?.isValid == true }.getOrDefault(false)
                        if (holderIsValid) {
                            Log.d("EnhancedVideoPlayer", "Surface exists, marking as ready (fallback)")
                            EnhancedPlayerManager.getInstance().setSurfaceReady(true)
                            EnhancedPlayerManager.getInstance().retryLoadMediaIfSurfaceReady()
                        }
                    }
                } catch (e: Exception) {
                    Log.d("EnhancedVideoPlayer", "Fallback surface ready check error: ${e.message}")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
