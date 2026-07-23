package io.github.aedev.flow.ui.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Requests focus once when this target enters composition, and again only when a supplied key
 * actually changes. A structural key prevents recompositions from stealing focus back.
 */
@Composable
fun Modifier.tvInitialFocus(vararg keys: Any?): Modifier {
    val requester = remember { FocusRequester() }
    val requestKeys = keys.toList()
    LaunchedEffect(requestKeys) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
