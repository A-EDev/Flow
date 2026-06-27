package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import io.github.aedev.flow.R

@Composable
fun LockModeTouchShield(
    onRevealUnlock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockLabel = stringResource(R.string.player_unlock_controls)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        onRevealUnlock()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .semantics {
                onClick(label = unlockLabel) {
                    onUnlock()
                    true
                }
            }
    )
}
