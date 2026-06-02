package io.github.aedev.flow.player.service

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.aedev.flow.service.VideoPlayerService


class BackgroundServiceManager {

    companion object {
        private const val TAG = "BackgroundServiceMgr"
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null

    /**
     * Connect a controller to the video MediaSessionService, which starts it
     */
    fun startService(context: Context?, videoId: String, title: String, channel: String, thumbnail: String) {
        val ctx = context?.applicationContext ?: return
        if (controllerFuture != null) {
            Log.w(TAG, "Video MediaSessionService controller already connected for $videoId")
            return
        }
        try {
            Log.w(TAG, "Connecting to video MediaSessionService for $videoId")
            val token = SessionToken(ctx, ComponentName(ctx, VideoPlayerService::class.java))
            val future = MediaController.Builder(ctx, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    future.get()
                    Log.w(TAG, "Connected to video MediaSessionService for $videoId")
                    Log.d(TAG, "Connected to video MediaSessionService for $videoId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to video MediaSessionService", e)
                    if (controllerFuture === future) controllerFuture = null
                }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video MediaSessionService", e)
            controllerFuture = null
        }
    }

    /**
     * Release the controller. Once no controllers are connected and the player is not playing,
     * Media3 transitions the service out of the foreground and stops it.
     */
    fun stopService(context: Context?) {
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release video session controller", e)
            }
        }
        controllerFuture = null
        Log.w(TAG, "Video MediaSessionService controller released")
        Log.d(TAG, "Video MediaSessionService controller released")
    }
}
