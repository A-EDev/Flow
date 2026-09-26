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
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.service.VideoPlayerService
import kotlin.math.roundToInt

/**
 * Helper class for Picture-in-Picture mode support
 */
object PictureInPictureHelper {
    const val ACTION_PLAY = "io.github.aedev.flow.action.PIP_PLAY"
    const val ACTION_PAUSE = "io.github.aedev.flow.action.PIP_PAUSE"
    const val ACTION_NEXT = "io.github.aedev.flow.action.PIP_NEXT"
    const val ACTION_BACKGROUND_AUDIO = "io.github.aedev.flow.action.PIP_BACKGROUND_AUDIO"

    private const val REQUEST_CODE_PLAY = 1
    private const val REQUEST_CODE_PAUSE = 2
    private const val REQUEST_CODE_NEXT = 4
    private const val REQUEST_CODE_BACKGROUND_AUDIO = 6
    private const val ACTION_PICTURE_IN_PICTURE_SETTINGS = "android.settings.PICTURE_IN_PICTURE_SETTINGS"

    @Volatile
    var sourceRectHint: android.graphics.Rect? = null

    @Volatile
    var currentVideoAspectRatio: Float = DEFAULT_VIDEO_ASPECT_RATIO
        private set

    @Volatile
    var isPopupActive: Boolean = false
        private set

    /**
     * Latest Next availability for the regular video player. MainActivity does not own the
     * Compose/ViewModel state, so automatic PiP reads this snapshot instead of reconstructing a
     * queue-only approximation during onUserLeaveHint().
     */
    @Volatile
    private var latestRegularVideoHasNext: Boolean = false

    internal fun setPopupActive(active: Boolean) {
        isPopupActive = active
    }

    /**
     * Check if the device supports PiP
     */
    fun isPipSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun isPlayerPopupSupported(context: Context): Boolean = isPipSupported(context) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun isPipAllowed(context: Context): Boolean {
        if (!isPipSupported(context)) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
        return try {
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                        Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                        Process.myUid(),
                        context.packageName,
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
        val intents =
            listOf(
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
        aspectRatio: Float = currentVideoAspectRatio,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false,
        hasNext: Boolean? = null,
        includeBackgroundAction: Boolean = true,
    ): Boolean {
        if (!isPipSupported(activity)) return false

        if (includeBackgroundAction && hasNext != null) {
            latestRegularVideoHasNext = hasNext
        }

        return try {
            val effectiveHasNext =
                if (includeBackgroundAction) hasNext ?: latestRegularVideoHasNext else hasNext ?: false
            val params =
                buildPipParams(
                    activity,
                    aspectRatio,
                    isPlaying,
                    autoEnterEnabled,
                    hasNext = effectiveHasNext,
                    includeBackgroundAction = includeBackgroundAction,
                )
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun requestPlayerPipMode(
        activity: Activity,
        aspectRatio: Float = currentVideoAspectRatio,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false,
        hasNext: Boolean? = null,
    ): Boolean {
        if (isPipSupported(activity)) {
            if (!isPipAllowed(activity)) {
                openPipSettings(activity)
                return false
            }
            val entered =
                if (activity is MainActivity) {
                    activity.enterPlayerPictureInPictureMode(
                        aspectRatio = aspectRatio,
                        isPlaying = isPlaying,
                        openSettingsOnDenied = true,
                        hasNext = hasNext,
                    )
                } else {
                    enterPipMode(
                        activity = activity,
                        aspectRatio = aspectRatio,
                        isPlaying = isPlaying,
                        autoEnterEnabled = autoEnterEnabled,
                        hasNext = hasNext,
                    )
                }
            if (entered) return true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (!Settings.canDrawOverlays(activity)) {
            openOverlaySettings(activity)
            return false
        }
        ContextCompat.startForegroundService(
            activity,
            Intent(activity, VideoPlayerService::class.java).setAction(VideoPlayerService.ACTION_SHOW_POPUP),
        )
        return true
    }

    fun dismissPopup(context: Context) {
        if (!isPopupActive) return
        context.startService(
            Intent(context, VideoPlayerService::class.java).setAction(VideoPlayerService.ACTION_HIDE_POPUP),
        )
    }

    private fun openOverlaySettings(activity: Activity) {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            } else {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                )
            }
        runCatching { activity.startActivity(intent) }
            .onFailure {
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
                )
            }
    }

    /**
     * Update PiP params (for playback state changes)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePipParams(
        activity: Activity,
        aspectRatio: Float = currentVideoAspectRatio,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false,
        hasNext: Boolean? = null,
        includeBackgroundAction: Boolean = true,
    ) {
        if (!isPipSupported(activity)) return

        if (includeBackgroundAction && hasNext != null) {
            latestRegularVideoHasNext = hasNext
        }

        try {
            val effectiveHasNext =
                if (includeBackgroundAction) hasNext ?: latestRegularVideoHasNext else hasNext ?: false
            val params =
                buildPipParams(
                    activity,
                    aspectRatio,
                    isPlaying,
                    autoEnterEnabled,
                    hasNext = effectiveHasNext,
                    includeBackgroundAction = includeBackgroundAction,
                )
            activity.setPictureInPictureParams(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        context: Context,
        requestedAspectRatio: Float,
        isPlaying: Boolean,
        autoEnterEnabled: Boolean,
        hasNext: Boolean = false,
        includeBackgroundAction: Boolean = true,
    ): PictureInPictureParams {
        val safeAspectRatio = sanitizePipAspectRatio(requestedAspectRatio)
        currentVideoAspectRatio = safeAspectRatio
        val aspectRatio = Rational((safeAspectRatio * 1_000).roundToInt(), 1_000)

        val selected =
            PipActionPolicy
                .selectActions(
                    isPlaying = isPlaying,
                    hasNext = hasNext,
                    includeBackgroundAction = includeBackgroundAction,
                )
        val actions = mutableListOf<RemoteAction>()
        for (action in selected) {
            when (action) {
                PipAction.BACKGROUND_AUDIO -> {
                    actions.add(
                        createRemoteAction(
                            context,
                            R.drawable.ic_pip_headphones,
                            context.getString(R.string.player_action_background),
                            ACTION_BACKGROUND_AUDIO,
                            REQUEST_CODE_BACKGROUND_AUDIO,
                        ),
                    )
                }

                PipAction.PAUSE -> {
                    actions.add(
                        createRemoteAction(
                            context,
                            android.R.drawable.ic_media_pause,
                            context.getString(R.string.pause),
                            ACTION_PAUSE,
                            REQUEST_CODE_PAUSE,
                        ),
                    )
                }

                PipAction.PLAY -> {
                    actions.add(
                        createRemoteAction(
                            context,
                            android.R.drawable.ic_media_play,
                            context.getString(R.string.play),
                            ACTION_PLAY,
                            REQUEST_CODE_PLAY,
                        ),
                    )
                }

                PipAction.NEXT -> {
                    actions.add(
                        createRemoteAction(
                            context,
                            android.R.drawable.ic_media_next,
                            context.getString(R.string.next),
                            ACTION_NEXT,
                            REQUEST_CODE_NEXT,
                        ),
                    )
                }
            }
        }

        val builder =
            PictureInPictureParams
                .Builder()
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
        requestCode: Int,
    ): RemoteAction {
        val intent =
            Intent(action).apply {
                setPackage(context.packageName)
            }
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags,
            )

        return RemoteAction(
            Icon.createWithResource(context, iconResId),
            title,
            title,
            pendingIntent,
        )
    }

    /**
     * Create a broadcast receiver for PiP actions
     */
    fun createPipActionReceiver(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit = {},
        onBackgroundAudio: () -> Unit = {},
    ): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    ACTION_PLAY -> onPlay()
                    ACTION_PAUSE -> onPause()
                    ACTION_NEXT -> onNext()
                    ACTION_BACKGROUND_AUDIO -> onBackgroundAudio()
                }
            }
        }

    /**
     * Get the intent filter for PiP actions
     */
    fun getPipIntentFilter(): IntentFilter =
        IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_BACKGROUND_AUDIO)
        }
}
