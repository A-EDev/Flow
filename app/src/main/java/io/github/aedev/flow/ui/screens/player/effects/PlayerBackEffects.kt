package io.github.aedev.flow.ui.screens.player.effects

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import io.github.aedev.flow.ui.components.videoplayer.PlayerSheetValue
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlin.coroutines.cancellation.CancellationException

/**
 * Back leaves fullscreen, then scrubs the expanded sheet down with the finger and collapses it on
 * commit. While the player is touch-locked, back only reveals the lock; it is registered last so it
 * wins over both.
 */
@Composable
internal fun PlayerBackEffects(
    playerSheetState: PlayerDraggableState,
    screenState: PlayerScreenState,
    isInPipMode: Boolean,
) {
    BackHandler(enabled = screenState.isFullscreen) {
        screenState.isFullscreen = false
        screenState.isFullscreenPortrait = false
    }

    // Stays enabled for the whole scrub: the sheet's currentValue flips to Collapsed halfway
    // through, and disabling the handler mid-gesture would cancel it as if the user had backed out.
    var backScrubInFlight by remember { mutableStateOf(false) }
    PredictiveBackHandler(
        enabled =
            (playerSheetState.currentValue == PlayerSheetValue.Expanded || backScrubInFlight) &&
                !isInPipMode &&
                !screenState.isFullscreen,
    ) { progress ->
        backScrubInFlight = true
        try {
            playerSheetState.beginBackScrub()
            progress.collect { event -> playerSheetState.scrubBack(event.progress) }
            playerSheetState.collapse()
        } catch (e: CancellationException) {
            playerSheetState.expand()
            // Returning normally from here, before the flow was collected, makes activity-compose
            // throw "You must collect the progress flow" (#1307).
            throw e
        } finally {
            backScrubInFlight = false
        }
    }

    BackHandler(enabled = screenState.isTouchLocked && !isInPipMode) {
        screenState.revealLockOverlay()
        screenState.onInteraction()
    }
}
