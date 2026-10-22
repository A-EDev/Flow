package io.github.aedev.flow.player

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerHardwareController {
    private val _fullscreenVideoActive = MutableStateFlow(false)
    val fullscreenVideoActive: StateFlow<Boolean> = _fullscreenVideoActive.asStateFlow()

    fun setFullscreenVideoActive(active: Boolean) {
        _fullscreenVideoActive.value = active
    }

    private val _inAppVolumeOverlayEnabled = MutableStateFlow(true)
    val inAppVolumeOverlayEnabled: StateFlow<Boolean> = _inAppVolumeOverlayEnabled.asStateFlow()

    fun setInAppVolumeOverlayEnabled(enabled: Boolean) {
        _inAppVolumeOverlayEnabled.value = enabled
    }

    private val _volumeKeySignal = MutableStateFlow(0L)
    val volumeKeySignal: StateFlow<Long> = _volumeKeySignal.asStateFlow()

    fun notifyVolumeKey() {
        _volumeKeySignal.value = _volumeKeySignal.value + 1
    }

    /**
     * One step of the media volume, shown on the fullscreen player's own overlay when that is up
     * and on the system's otherwise. False when the device has no audio service.
     */
    fun adjustVolume(
        context: Context,
        raise: Boolean,
    ): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val inAppOverlay = fullscreenVideoActive.value && inAppVolumeOverlayEnabled.value
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            if (inAppOverlay) 0 else AudioManager.FLAG_SHOW_UI,
        )
        if (inAppOverlay) notifyVolumeKey()
        return true
    }
}
