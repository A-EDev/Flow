package com.flow.youtube.ui.screens.player.effects

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.PictureInPictureHelper

private const val TAG = "PipModeHandler"

object PipModeHandler {
    
    /**
     * Check if PiP is supported on this device and enabled by user
     */
    fun isPipSupported(context: Context, manualPipButtonEnabled: Boolean): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
               PictureInPictureHelper.isPipSupported(context) &&
               manualPipButtonEnabled
    }
    
    /**
     * Enter Picture-in-Picture mode
     */
    fun enterPipMode(activity: Activity?, isPlaying: Boolean) {
        activity?.let { act ->
            PictureInPictureHelper.enterPipMode(
                activity = act,
                isPlaying = isPlaying
            )
        }
    }
}

/**
 * Effect to detect PiP mode state changes
 */
@Composable
fun PipModeDetectionEffect(
    lifecycleOwner: LifecycleOwner,
    activity: Activity?,
    onPipModeChanged: (Boolean) -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                onPipModeChanged(activity.isInPictureInPictureMode)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Effect to register PiP broadcast receiver for play/pause controls
 */
@Composable
fun PipBroadcastReceiverEffect(context: Context) {
    DisposableEffect(Unit) {
        val receiver = PictureInPictureHelper.createPipActionReceiver(
            onPlay = { EnhancedPlayerManager.getInstance().play() },
            onPause = { EnhancedPlayerManager.getInstance().pause() }
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                PictureInPictureHelper.getPipIntentFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, PictureInPictureHelper.getPipIntentFilter())
        }
        
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
fun PipParamsUpdateEffect(
    isPlaying: Boolean,
    autoPipEnabled: Boolean,
    activity: Activity?
) {
    LaunchedEffect(isPlaying, autoPipEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatioWidth = 16,
                aspectRatioHeight = 9,
                isPlaying = isPlaying,
                autoEnterEnabled = autoPipEnabled
            )
        }
    }
}

/**
 * Collects PiP preferences from DataStore
 */
@Composable
fun rememberPipPreferences(context: Context): PipPreferences {
    val autoPipEnabled by remember(context) { 
        PlayerPreferences(context).autoPipEnabled 
    }.collectAsState(initial = false)
    
    val manualPipButtonEnabled by remember(context) { 
        PlayerPreferences(context).manualPipButtonEnabled 
    }.collectAsState(initial = true)
    
    return PipPreferences(
        autoPipEnabled = autoPipEnabled,
        manualPipButtonEnabled = manualPipButtonEnabled
    )
}

/**
 * Data class holding PiP preferences
 */
data class PipPreferences(
    val autoPipEnabled: Boolean,
    val manualPipButtonEnabled: Boolean
)

/**
 * All-in-one composable that sets up all PiP-related effects
 */
@Composable
fun SetupPipEffects(
    context: Context,
    activity: Activity?,
    lifecycleOwner: LifecycleOwner,
    isPlaying: Boolean,
    pipPreferences: PipPreferences,
    onPipModeChanged: (Boolean) -> Unit
) {
    // Detect PiP state changes
    PipModeDetectionEffect(
        lifecycleOwner = lifecycleOwner,
        activity = activity,
        onPipModeChanged = onPipModeChanged
    )
    
    // Register broadcast receiver
    PipBroadcastReceiverEffect(context)
    
    // Update PiP params
    PipParamsUpdateEffect(
        isPlaying = isPlaying,
        autoPipEnabled = pipPreferences.autoPipEnabled,
        activity = activity
    )
}
