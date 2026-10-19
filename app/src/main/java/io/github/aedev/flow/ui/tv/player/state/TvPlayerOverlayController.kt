package io.github.aedev.flow.ui.tv.player.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the TV player shows over the video. In [PANEL] one of the phone player's sheets is open beside
 * it; which one lives in the shared player screen state.
 */
enum class TvOverlayMode { HIDDEN, TRANSPORT, PANEL }

data class TvOverlayState(
    val mode: TvOverlayMode = TvOverlayMode.HIDDEN,
    val lastInteractionAtMs: Long = 0L,
)

/**
 * Overlay state machine for the TV player, free of Compose/Android types.
 * Auto-hide is *computed* here ([autoHideDeadline]) but executed by the UI with
 * a single restartable delay — never a polling loop.
 */
class TvPlayerOverlayController(
    private val nowMs: () -> Long,
) {
    private val _state = MutableStateFlow(TvOverlayState())
    val state: StateFlow<TvOverlayState> = _state.asStateFlow()

    fun onUserInteraction() {
        _state.update { it.copy(lastInteractionAtMs = nowMs()) }
    }

    fun showTransport() {
        _state.update { it.copy(mode = TvOverlayMode.TRANSPORT, lastInteractionAtMs = nowMs()) }
    }

    fun hide() {
        _state.update { it.copy(mode = TvOverlayMode.HIDDEN) }
    }

    fun openPanel() {
        _state.update { it.copy(mode = TvOverlayMode.PANEL, lastInteractionAtMs = nowMs()) }
    }

    /** Closes the open panel back to the transport. */
    fun closePanel() {
        _state.update { current ->
            if (current.mode != TvOverlayMode.PANEL) return@update current
            current.copy(mode = TvOverlayMode.TRANSPORT, lastInteractionAtMs = nowMs())
        }
    }

    /** Walks PANEL → TRANSPORT → HIDDEN. Returns false when Back should close the player. */
    fun onBack(): Boolean =
        when (_state.value.mode) {
            TvOverlayMode.PANEL -> {
                closePanel()
                true
            }

            TvOverlayMode.TRANSPORT -> {
                hide()
                true
            }

            TvOverlayMode.HIDDEN -> {
                false
            }
        }

    /**
     * Epoch-ms moment the transport should hide, or null when it must stay up
     * (hidden/panel mode, paused, or an active scrub).
     */
    fun autoHideDeadline(
        isPlaying: Boolean,
        isScrubbing: Boolean,
    ): Long? {
        val current = _state.value
        return if (current.mode == TvOverlayMode.TRANSPORT && isPlaying && !isScrubbing) {
            current.lastInteractionAtMs + AUTO_HIDE_DELAY_MS
        } else {
            null
        }
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS = 5_000L
    }
}
