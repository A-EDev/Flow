package io.github.aedev.flow.ui.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

@Composable
fun Modifier.tvInitialFocus(vararg keys: Any?): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(keys = keys) {
        delay(80)
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
