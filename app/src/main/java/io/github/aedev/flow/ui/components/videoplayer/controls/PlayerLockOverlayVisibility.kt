package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// How long the lock-mode unlock affordance stays on screen before it auto-hides
// for a clean, unobstructed locked view. A single tap re-reveals it (see issue #619).
private const val LOCKED_OVERLAY_AUTO_HIDE_MS = 3_000L

/** Whether the unlock affordance is currently showing, and the tap that brings it back. */
internal class PlayerLockOverlayVisibility(
    val isVisible: Boolean,
    val reveal: () -> Unit,
)

/**
 * Lock-mode unlock affordance auto-hide (issue #619). While touch-locked, the
 * unlock button hides itself after a short delay so the locked view is clean,
 * then a single tap anywhere re-reveals it and restarts the timer.
 */
@Composable
internal fun rememberLockOverlayVisibility(
    isTouchLocked: Boolean,
    revealSignal: Int,
): PlayerLockOverlayVisibility {
    var isLockOverlayVisible by remember { mutableStateOf(true) }
    // Bumped on every reveal so that re-revealing while already visible still
    // restarts the auto-hide timer (a no-op `isLockOverlayVisible = true` would not).
    var lockOverlayRevealTick by remember { mutableIntStateOf(0) }

    val revealLockOverlay: () -> Unit = {
        isLockOverlayVisible = true
        lockOverlayRevealTick++
    }

    // Reset the unlock affordance to visible whenever lock mode is (re-)entered.
    LaunchedEffect(isTouchLocked, revealSignal) {
        if (isTouchLocked) {
            revealLockOverlay()
        }
    }

    // Auto-hide the unlock affordance after the delay while it is showing in lock mode.
    // Keyed on the reveal tick so each tap restarts the full delay window.
    LaunchedEffect(isTouchLocked, isLockOverlayVisible, lockOverlayRevealTick) {
        if (isTouchLocked && isLockOverlayVisible) {
            delay(LOCKED_OVERLAY_AUTO_HIDE_MS)
            isLockOverlayVisible = false
        }
    }

    return PlayerLockOverlayVisibility(
        isVisible = isLockOverlayVisible,
        reveal = revealLockOverlay,
    )
}
