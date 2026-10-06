package io.github.aedev.flow.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

/**
 * Shared sleep timer for both music and video playback.
 *
 * Usage:
 *   1. Call [attachToPlayer] when the player becomes active, passing the [Player] instance
 *      and a lambda that invokes the correct pause action for that player.
 *   2. Call [start] with a minute count, or [startEndOfMedia] for end-of-song mode.
 *   3. Call [cancel] to clear all state.
 *   4. Observe [isActive], [pauseAtEndOfMedia], and [triggerTimeMs] in the UI.
 */
object SleepTimerManager {
    // ── Compose-observable state ──────────────────────────────────────────────

    var isActive by mutableStateOf(false)
        private set

    /** True when the timer should fire when the current media item finishes. */
    var pauseAtEndOfMedia by mutableStateOf(false)
        private set

    /** True when the timer should close the app instead of pausing playback. */
    var closeAppOnExpiry by mutableStateOf(false)
        private set

    /** Remembered default for future sleep timers. */
    var preferredCloseAppOnExpiry by mutableStateOf(false)
        private set

    /**
     * Epoch-millisecond timestamp at which the player will be paused.
     * -1 when no countdown is running.
     */
    var triggerTimeMs by mutableLongStateOf(-1L)
        private set

    // ── Internal state ────────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    private data class PlayerOwner(
        val owner: Any,
        val player: Player?,
        val pause: () -> Unit,
        var exit: (() -> Unit)?,
        var active: Boolean = false,
        var activationOrder: Long = 0L,
    )

    private val playerOwners = LinkedHashMap<Any, PlayerOwner>()
    private var nextActivationOrder = 0L
    private var timerOwner: PlayerOwner? = null
    private var activeOwner: PlayerOwner? = null
    private var currentPlayer: Player? = null

    private val playerListener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (pauseAtEndOfMedia && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    firePause()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && pauseAtEndOfMedia) {
                    firePause()
                }
            }
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attach a player to receive end-of-media events.
     * Must be called whenever the active player changes.
     *
     * @param player   The Media3 [Player] for listening to playback events.
     * @param pauseFn  Lambda that pauses the correct player (music or video).
     */
    fun attachToPlayer(
        owner: Any,
        player: Player?,
        pauseFn: () -> Unit,
        exitFn: () -> Unit,
    ) {
        val current = playerOwners[owner]
        val replacement =
            PlayerOwner(
                owner = owner,
                player = player,
                pause = pauseFn,
                exit = exitFn,
                active = current?.active ?: false,
                activationOrder = current?.activationOrder ?: 0L,
            )
        playerOwners[owner] = replacement
        if (timerOwner?.owner === owner) timerOwner = replacement
        updateCurrentOwner()
    }

    fun updateOwnerActive(
        owner: Any,
        active: Boolean,
    ) {
        val attachment = playerOwners[owner] ?: return
        if (attachment.active == active) return
        attachment.active = active
        if (active) {
            attachment.activationOrder = ++nextActivationOrder
        } else if (timerOwner === attachment) {
            timerOwner = null
        }
        updateCurrentOwner()
    }

    /** Detach only this composition's registration; an active timer keeps its player target. */
    fun detachPlayer(owner: Any) {
        val detached = playerOwners.remove(owner) ?: return
        detached.exit = null
        if (activeOwner === detached && isActive) timerOwner = detached
        if (timerOwner === detached && !isActive) timerOwner = null
        updateCurrentOwner()
    }

    /**
     * Start a countdown timer.
     *
     * @param minutes  Duration in minutes, must be > 0.
     */
    fun start(
        minutes: Int,
        closeApp: Boolean = false,
    ) {
        require(minutes > 0) { "minutes must be positive" }
        clearState()
        closeAppOnExpiry = closeApp
        triggerTimeMs = System.currentTimeMillis() + minutes * 60_000L
        isActive = true
        timerJob =
            scope.launch {
                delay(minutes * 60_000L)
                firePause()
            }
    }

    /** Start end-of-media mode — player pauses (or closes the app) when the current item ends. */
    fun startEndOfMedia(closeApp: Boolean = false) {
        clearState()
        closeAppOnExpiry = closeApp
        pauseAtEndOfMedia = true
        isActive = true
    }

    fun updatePreferredCloseAppOnExpiry(enabled: Boolean) {
        preferredCloseAppOnExpiry = enabled
    }

    /** Cancel the timer and reset all state. */
    fun cancel() {
        clearState()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun firePause() {
        val target = activeOwner
        if (closeAppOnExpiry) {
            val exit = target?.exit
            if (exit != null) exit() else target?.pause?.invoke()
        } else {
            target?.pause?.invoke()
        }
        cancel()
    }

    private fun clearState() {
        timerJob?.cancel()
        timerJob = null
        pauseAtEndOfMedia = false
        closeAppOnExpiry = false
        triggerTimeMs = -1L
        isActive = false
        timerOwner = null
        updateCurrentOwner()
    }

    private fun updateCurrentOwner() {
        val nextOwner =
            playerOwners.values
                .filter { it.active }
                .maxByOrNull(PlayerOwner::activationOrder)
                ?: timerOwner
        if (activeOwner === nextOwner) return
        currentPlayer?.removeListener(playerListener)
        activeOwner = nextOwner
        currentPlayer = nextOwner?.player
        currentPlayer?.addListener(playerListener)
    }
}
