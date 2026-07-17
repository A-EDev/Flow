package io.github.aedev.flow.widget.core

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.aedev.flow.service.Media3MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "WidgetActions"

/**
 * Connects a short-lived [MediaController] to the existing music session, runs [block],
 * then releases it. A widget-button PendingIntent counts as a user interaction, so this
 * is an allowed foreground-service start even from a cold process (Android 12+).
 */
private suspend fun withMusicController(context: Context, block: (MediaController) -> Unit) {
    // ActionCallbacks run inside a BroadcastReceiver whose context cannot bind services
    // (ReceiverCallNotAllowedException) — the MediaController must use the app context.
    val appContext = context.applicationContext
    // MediaController is bound to the application's main looper — connect and command on Main.
    withContext(Dispatchers.Main) {
        val controller = try {
            suspendCancellableCoroutine<MediaController> { continuation ->
                val token = SessionToken(appContext, ComponentName(appContext, Media3MusicService::class.java))
                val future = MediaController.Builder(appContext, token).buildAsync()
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(appContext))
                continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect widget MediaController: ${e.message}")
            return@withContext
        }
        try {
            block(controller)
        } finally {
            controller.release()
        }
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMusicController(context) { controller ->
            if (controller.playWhenReady) controller.pause() else controller.play()
        }
    }
}

class NextTrackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMusicController(context) { it.seekToNext() }
    }
}

class PreviousTrackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMusicController(context) { it.seekToPrevious() }
    }
}

class ToggleLikeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMusicController(context) { controller ->
            controller.sendCustomCommand(
                SessionCommand(Media3MusicService.ACTION_TOGGLE_LIKE, Bundle.EMPTY),
                Bundle.EMPTY,
            )
        }
    }
}
