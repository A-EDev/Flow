package com.flow.youtube.player.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.flow.youtube.service.VideoPlayerService

/**
 * Manages background playback service lifecycle.
 */
class BackgroundServiceManager {
    
    companion object {
        private const val TAG = "BackgroundServiceMgr"
    }
    
    /**
     * Start background service for persistent playback.
     */
    fun startService(context: Context?, videoId: String, title: String, channel: String, thumbnail: String) {
        context?.let { ctx ->
            val intent = Intent(ctx, VideoPlayerService::class.java).apply {
                putExtra(VideoPlayerService.EXTRA_VIDEO_ID, videoId)
                putExtra(VideoPlayerService.EXTRA_VIDEO_TITLE, title)
                putExtra(VideoPlayerService.EXTRA_VIDEO_CHANNEL, channel)
                putExtra(VideoPlayerService.EXTRA_VIDEO_THUMBNAIL, thumbnail)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
                Log.d(TAG, "Background service started for video: $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background service", e)
            }
        }
    }
    
    /**
     * Stop background service.
     */
    fun stopService(context: Context?) {
        context?.let { ctx ->
            try {
                ctx.stopService(Intent(ctx, VideoPlayerService::class.java))
                Log.d(TAG, "Background service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop background service", e)
            }
        }
    }
}
