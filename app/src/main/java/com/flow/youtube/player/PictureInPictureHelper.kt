package com.flow.youtube.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.flow.youtube.R

/**
 * Helper class for Picture-in-Picture mode support
 */
object PictureInPictureHelper {
    
    const val ACTION_PLAY = "com.flow.youtube.action.PIP_PLAY"
    const val ACTION_PAUSE = "com.flow.youtube.action.PIP_PAUSE"
    const val ACTION_PREVIOUS = "com.flow.youtube.action.PIP_PREVIOUS"
    const val ACTION_NEXT = "com.flow.youtube.action.PIP_NEXT"
    const val ACTION_CLOSE = "com.flow.youtube.action.PIP_CLOSE"
    
    private const val REQUEST_CODE_PLAY = 1
    private const val REQUEST_CODE_PAUSE = 2
    private const val REQUEST_CODE_PREVIOUS = 3
    private const val REQUEST_CODE_NEXT = 4
    private const val REQUEST_CODE_CLOSE = 5
    
    /**
     * Check if the device supports PiP
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
               context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    
    /**
     * Enter Picture-in-Picture mode with the given aspect ratio
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPipMode(
        activity: Activity,
        aspectRatioWidth: Int = 16,
        aspectRatioHeight: Int = 9,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = true
    ): Boolean {
        if (!isPipSupported(activity)) return false
        
        return try {
            val params = buildPipParams(
                activity,
                aspectRatioWidth,
                aspectRatioHeight,
                isPlaying,
                autoEnterEnabled
            )
            activity.enterPictureInPictureMode(params)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Update PiP params (for playback state changes)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePipParams(
        activity: Activity,
        aspectRatioWidth: Int = 16,
        aspectRatioHeight: Int = 9,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = true
    ) {
        if (!isPipSupported(activity)) return
        
        try {
            val params = buildPipParams(
                activity,
                aspectRatioWidth,
                aspectRatioHeight,
                isPlaying,
                autoEnterEnabled
            )
            activity.setPictureInPictureParams(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        context: Context,
        aspectRatioWidth: Int,
        aspectRatioHeight: Int,
        isPlaying: Boolean,
        autoEnterEnabled: Boolean
    ): PictureInPictureParams {
        val aspectRatio = Rational(aspectRatioWidth, aspectRatioHeight)
        
        val actions = mutableListOf<RemoteAction>()
        
        // Play/Pause action
        if (isPlaying) {
            actions.add(createRemoteAction(
                context,
                android.R.drawable.ic_media_pause,
                "Pause",
                ACTION_PAUSE,
                REQUEST_CODE_PAUSE
            ))
        } else {
            actions.add(createRemoteAction(
                context,
                android.R.drawable.ic_media_play,
                "Play",
                ACTION_PLAY,
                REQUEST_CODE_PLAY
            ))
        }
        
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(actions)
        
        // Auto-enter PiP when user goes to home screen (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && autoEnterEnabled) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        
        return builder.build()
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRemoteAction(
        context: Context,
        iconResId: Int,
        title: String,
        action: String,
        requestCode: Int
    ): RemoteAction {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            requestCode, 
            intent, 
            flags
        )
        
        return RemoteAction(
            Icon.createWithResource(context, iconResId),
            title,
            title,
            pendingIntent
        )
    }
    
    /**
     * Create a broadcast receiver for PiP actions
     */
    fun createPipActionReceiver(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onClose: () -> Unit = {}
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PLAY -> onPlay()
                    ACTION_PAUSE -> onPause()
                    ACTION_PREVIOUS -> onPrevious()
                    ACTION_NEXT -> onNext()
                    ACTION_CLOSE -> onClose()
                }
            }
        }
    }
    
    /**
     * Get the intent filter for PiP actions
     */
    fun getPipIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_CLOSE)
        }
    }
}
