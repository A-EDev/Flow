package io.github.aedev.flow.player

import android.app.Activity
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.aedev.flow.R
import io.github.aedev.flow.MainActivity

/**
 * Helper class for Picture-in-Picture mode support
 */
object PictureInPictureHelper {
    
    const val ACTION_PLAY = "io.github.aedev.flow.action.PIP_PLAY"
    const val ACTION_PAUSE = "io.github.aedev.flow.action.PIP_PAUSE"
    const val ACTION_PREVIOUS = "io.github.aedev.flow.action.PIP_PREVIOUS"
    const val ACTION_NEXT = "io.github.aedev.flow.action.PIP_NEXT"
    const val ACTION_CLOSE = "io.github.aedev.flow.action.PIP_CLOSE"
    
    private const val REQUEST_CODE_PLAY = 1
    private const val REQUEST_CODE_PAUSE = 2
    private const val REQUEST_CODE_PREVIOUS = 3
    private const val REQUEST_CODE_NEXT = 4
    private const val REQUEST_CODE_CLOSE = 5
    private const val ACTION_PICTURE_IN_PICTURE_SETTINGS = "android.settings.PICTURE_IN_PICTURE_SETTINGS"

    @Volatile
    var sourceRectHint: android.graphics.Rect? = null
    
    /**
     * Check if the device supports PiP
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
               context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun isPipAllowed(context: Context): Boolean {
        if (!isPipSupported(context)) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        } catch (_: Exception) {
            true
        }
    }

    fun openPipSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val packageUri = Uri.parse("package:${activity.packageName}")
        val intents = listOf(
            Intent(ACTION_PICTURE_IN_PICTURE_SETTINGS, packageUri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        for (intent in intents) {
            runCatching {
                activity.startActivity(intent)
                return
            }
        }
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
        autoEnterEnabled: Boolean = false
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

    fun requestPlayerPipMode(
        activity: Activity,
        aspectRatioWidth: Int = 16,
        aspectRatioHeight: Int = 9,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false
    ): Boolean {
        if (!isPipAllowed(activity)) {
            openPipSettings(activity)
            return false
        }
        return if (activity is MainActivity) {
            activity.enterPlayerPictureInPictureMode(
                aspectRatioWidth = aspectRatioWidth,
                aspectRatioHeight = aspectRatioHeight,
                isPlaying = isPlaying,
                openSettingsOnDenied = true
            )
        } else {
            enterPipMode(
                activity = activity,
                aspectRatioWidth = aspectRatioWidth,
                aspectRatioHeight = aspectRatioHeight,
                isPlaying = isPlaying,
                autoEnterEnabled = autoEnterEnabled
            )
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
        autoEnterEnabled: Boolean = false
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

        sourceRectHint?.takeIf { !it.isEmpty }?.let { builder.setSourceRectHint(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(false)
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
