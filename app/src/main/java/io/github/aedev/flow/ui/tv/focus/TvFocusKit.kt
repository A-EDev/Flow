package io.github.aedev.flow.ui.tv.focus

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Canonical focus state for TV focusables. All TV components observe
 * [isFocused] (never `hasFocus`) so focus visuals behave identically everywhere.
 */
@Stable
class TvFocusState {
    var isFocused by mutableStateOf(false)
        internal set
}

@Composable
fun rememberTvFocusState(): TvFocusState = remember { TvFocusState() }

/**
 * The one focus transform for the ten-foot UI: tracks focus into [state],
 * spring-scales via graphicsLayer only (no relayout), and lifts the focused
 * item above its neighbors so the scaled card overlaps them cleanly.
 * Focus *colors* (border/container) stay in the component, from M3 tokens.
 */
@Composable
fun Modifier.tvFocusScale(state: TvFocusState): Modifier {
    val focusScale = LocalTvDimens.current.focusScale
    val scale by animateFloatAsState(
        targetValue = if (state.isFocused) focusScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale",
    )
    return this
        .zIndex(if (state.isFocused) 1f else 0f)
        .onFocusChanged { state.isFocused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
