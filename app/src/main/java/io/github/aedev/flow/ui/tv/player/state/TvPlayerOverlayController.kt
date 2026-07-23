package io.github.aedev.flow.ui.tv.player.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TvOverlayMode { HIDDEN, TRANSPORT, PANEL }

/** Side panels the TV player can open. Quality/speed/audio/subtitles are settings sub-pages. */
enum class TvPlayerPanel {
    SETTINGS,
    QUALITY,
    SPEED,
    AUDIO,
    SUBTITLES,
    QUEUE,
    COMMENTS,
    LIVE_CHAT,
    DESCRIPTION,
    SLEEP_TIMER;

    /** Back from a sub-page returns to its parent panel instead of the transport. */
    val parent: TvPlayerPanel?
        get() = when (this) {
            QUALITY, SPEED, AUDIO, SUBTITLES -> SETTINGS
            else -> null
        }
}

data class TvOverlayState(
    val mode: TvOverlayMode = TvOverlayMode.HIDDEN,
    val activePanel: TvPlayerPanel? = null,
    val lastInteractionAtMs: Long = 0L,
)

/**
 * Overlay state machine for the TV player, free of Compose/Android types.
 * Auto-hide is *computed* here ([autoHideDeadline]) but executed by the UI with
 * a single restartable delay — never a polling loop.
 */
class TvPlayerOverlayController(private val nowMs: () -> Long) {

    private val _state = MutableStateFlow(TvOverlayState())
    val state: StateFlow<TvOverlayState> = _state.asStateFlow()

    fun onUserInteraction() {
        _state.update { it.copy(lastInteractionAtMs = nowMs()) }
    }

    fun showTransport() {
        _state.update {
            it.copy(mode = TvOverlayMode.TRANSPORT, activePanel = null, lastInteractionAtMs = nowMs())
        }
    }

    fun hide() {
        _state.update { it.copy(mode = TvOverlayMode.HIDDEN, activePanel = null) }
    }

    fun openPanel(panel: TvPlayerPanel) {
        _state.update {
            it.copy(mode = TvOverlayMode.PANEL, activePanel = panel, lastInteractionAtMs = nowMs())
        }
    }

    /** Closes the active panel: sub-pages pop to their parent, roots return to the transport. */
    fun closePanel() {
        _state.update { current ->
            if (current.mode != TvOverlayMode.PANEL) return@update current
            val parent = current.activePanel?.parent
            if (parent != null) {
                current.copy(activePanel = parent, lastInteractionAtMs = nowMs())
            } else {
                current.copy(mode = TvOverlayMode.TRANSPORT, activePanel = null, lastInteractionAtMs = nowMs())
            }
        }
    }

    /** Walks PANEL → TRANSPORT → HIDDEN. Returns false when Back should close the player. */
    fun onBack(): Boolean = when (_state.value.mode) {
        TvOverlayMode.PANEL -> {
            closePanel()
            true
        }
        TvOverlayMode.TRANSPORT -> {
            hide()
            true
        }
        TvOverlayMode.HIDDEN -> false
    }

    /**
     * Epoch-ms moment the transport should hide, or null when it must stay up
     * (hidden/panel mode, paused, or an active scrub).
     */
    fun autoHideDeadline(isPlaying: Boolean, isScrubbing: Boolean): Long? {
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
