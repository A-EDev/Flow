package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Keeps a touch from the controls beneath it without consuming it, so the player's tap detector,
 * an ancestor, still sees the whole gesture. Compose does not share a touch between siblings, which
 * is what stops the buttons below from firing (#1308).
 */
@Composable
internal fun PlayerTapShield(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
    )
}
