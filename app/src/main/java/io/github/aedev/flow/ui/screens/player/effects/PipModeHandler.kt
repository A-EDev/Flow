package io.github.aedev.flow.ui.screens.player.effects

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.BackgroundPlaybackPolicy
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper

private const val TAG = "PipModeHandler"

/**
 * Effect to register PiP broadcast receiver for playback and background-audio controls.
 */
@Composable
private fun PipBroadcastReceiverEffect(
    context: Context,
    onNext: () -> Unit,
    onBackgroundAudio: () -> Unit,
) {
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnBackgroundAudio by rememberUpdatedState(onBackgroundAudio)
    DisposableEffect(context) {
        val receiver =
            PictureInPictureHelper.createPipActionReceiver(
                onPlay = { EnhancedPlayerManager.getInstance().play() },
                onPause = { EnhancedPlayerManager.getInstance().pause() },
                onNext = { latestOnNext() },
                onBackgroundAudio = { latestOnBackgroundAudio() },
            )

        ContextCompat.registerReceiver(
            context,
            receiver,
            PictureInPictureHelper.getPipIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister PiP receiver", e)
            }
        }
    }
}

/**
 * Effect to update PiP params when playback state changes
 */
@Composable
private fun PipParamsUpdateEffect(
    isPlaying: Boolean,
    autoPipEnabled: Boolean,
    isBackgroundPlaybackMode: Boolean,
    videoAspectRatio: Float,
    activity: Activity?,
    hasNext: Boolean,
) {
    LaunchedEffect(isPlaying, autoPipEnabled, isBackgroundPlaybackMode, videoAspectRatio, hasNext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val autoEnterEnabled =
                BackgroundPlaybackPolicy.shouldEnterAutoPip(
                    autoPipEnabled = autoPipEnabled,
                    isVideoPlaying = isPlaying,
                    explicitBackgroundPlaybackActive = isBackgroundPlaybackMode,
                )
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatio = videoAspectRatio,
                isPlaying = isPlaying,
                autoEnterEnabled = autoEnterEnabled,
                hasNext = hasNext,
            )
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                PictureInPictureHelper.updatePipParams(
                    activity = activity,
                    aspectRatio = videoAspectRatio,
                    isPlaying = false,
                    autoEnterEnabled = false,
                    hasNext = false,
                )
            }
        }
    }
}

/**
 * Collects PiP preferences from DataStore
 */
@Composable
internal fun rememberPipPreferences(context: Context): PipPreferences {
    val preferences = remember(context) { PlayerPreferences(context) }
    val autoPipEnabled by preferences.autoPipEnabled.collectAsStateWithLifecycle(initialValue = false)
    val manualPipButtonEnabled by preferences.manualPipButtonEnabled.collectAsStateWithLifecycle(initialValue = true)

    return PipPreferences(
        autoPipEnabled = autoPipEnabled,
        manualPipButtonEnabled = manualPipButtonEnabled,
    )
}

/**
 * Data class holding PiP preferences
 */
internal data class PipPreferences(
    val autoPipEnabled: Boolean,
    val manualPipButtonEnabled: Boolean,
)

/**
 * All-in-one composable that sets up all PiP-related effects
 */
@Composable
internal fun SetupPipEffects(
    context: Context,
    activity: Activity?,
    isPlaying: Boolean,
    isBackgroundPlaybackMode: Boolean,
    videoAspectRatio: Float,
    pipPreferences: PipPreferences,
    hasNext: Boolean,
    onNext: () -> Unit,
    onBackgroundAudio: () -> Unit,
) {
    // Register broadcast receiver
    PipBroadcastReceiverEffect(
        context = context,
        onNext = onNext,
        onBackgroundAudio = onBackgroundAudio,
    )

    // Update PiP params
    PipParamsUpdateEffect(
        isPlaying = isPlaying,
        autoPipEnabled = pipPreferences.autoPipEnabled,
        isBackgroundPlaybackMode = isBackgroundPlaybackMode,
        videoAspectRatio = videoAspectRatio,
        activity = activity,
        hasNext = hasNext,
    )
}
