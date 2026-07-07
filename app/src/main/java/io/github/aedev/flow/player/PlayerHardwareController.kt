package io.github.aedev.flow.player

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
}
