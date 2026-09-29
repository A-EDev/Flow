package io.github.aedev.flow.ui.screens.player.effects

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PlayerHardwareController
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class AudioSystemInfo(
    val audioManager: AudioManager,
    val maxVolume: Int,
)

@Composable
fun rememberAudioSystemInfo(context: Context): AudioSystemInfo =
    remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioSystemInfo(
            audioManager = audioManager,
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        )
    }

/**
 * Keeps the overlay's volume model in step with the system stream: clamps a boost the user has
 * since disallowed, reseeds on every new video, hands the fullscreen/overlay verdict to the
 * hardware key controller, and re-reads the stream whenever a hardware key was pressed.
 */
@Composable
internal fun PlayerVolumeEffects(
    videoId: String,
    screenState: PlayerScreenState,
    audioSystemInfo: AudioSystemInfo,
    allowVolumeBoost: Boolean,
    volumeSwipeGesturesEnabled: Boolean,
) {
    LaunchedEffect(allowVolumeBoost) {
        if (!allowVolumeBoost && screenState.volumeLevel > 1f) {
            screenState.volumeLevel = 1f
            EnhancedPlayerManager.getInstance().setVolumeBoost(1f)
        }
    }

    val syncVolumeFromSystem: () -> Unit = {
        val max = audioSystemInfo.maxVolume
        if (max > 0) {
            val systemVolume = audioSystemInfo.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            screenState.volumeLevel = (systemVolume.toFloat() / max).coerceIn(0f, 1f)
            EnhancedPlayerManager.getInstance().setVolumeBoost(1f)
        }
    }

    LaunchedEffect(videoId) {
        syncVolumeFromSystem()
    }

    LaunchedEffect(screenState.isFullscreen, volumeSwipeGesturesEnabled) {
        PlayerHardwareController.setFullscreenVideoActive(screenState.isFullscreen)
        PlayerHardwareController.setInAppVolumeOverlayEnabled(volumeSwipeGesturesEnabled)
    }
    DisposableEffect(Unit) {
        onDispose {
            PlayerHardwareController.setFullscreenVideoActive(false)
            PlayerHardwareController.setInAppVolumeOverlayEnabled(true)
        }
    }

    val volumeKeySignal by PlayerHardwareController.volumeKeySignal.collectAsStateWithLifecycle()
    LaunchedEffect(volumeKeySignal) {
        if (volumeKeySignal > 0L) {
            syncVolumeFromSystem()
            screenState.showVolumeOverlay = true
        }
    }
}

@Composable
internal fun PlayerBrightnessRestoreEffect(
    screenState: PlayerScreenState,
    rememberBrightnessEnabled: Boolean,
    rememberedBrightnessLevel: Float,
) {
    LaunchedEffect(rememberBrightnessEnabled, rememberedBrightnessLevel) {
        if (rememberBrightnessEnabled) {
            screenState.brightnessLevel =
                if (rememberedBrightnessLevel < 0f) {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    rememberedBrightnessLevel.coerceIn(0f, 1f)
                }
        }
    }
}

internal fun brightnessLevelUpdater(
    screenState: PlayerScreenState,
    playerPreferences: PlayerPreferences,
    rememberBrightnessEnabled: Boolean,
    scope: CoroutineScope,
): (Float) -> Unit =
    { brightnessLevel ->
        screenState.brightnessLevel = brightnessLevel
        if (rememberBrightnessEnabled) {
            scope.launch {
                playerPreferences.setRememberedBrightnessLevel(brightnessLevel)
            }
        }
    }
