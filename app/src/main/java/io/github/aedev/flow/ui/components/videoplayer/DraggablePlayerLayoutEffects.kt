package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_RESNAP_DEBOUNCE_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.MiniPlayerResnapTargets
import io.github.aedev.flow.ui.components.videoplayer.motion.resnapMiniPlayer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Nudges a settled mini player back onto its resting corner whenever the bounds change under it
 * (nav bar shown or hidden, rotation, wide mode). Keyed on settled values only, never on a live
 * fraction, so it cannot restart per frame.
 */
@Composable
internal fun MiniPlayerResnapEffect(
    state: PlayerDraggableState,
    isCollapsedTarget: Boolean,
    targets: MiniPlayerResnapTargets,
) {
    LaunchedEffect(
        isCollapsedTarget,
        targets.targetMiniX,
        targets.targetMiniY,
        targets.isWideMode,
        targets.isLargeScreen,
    ) {
        if (state.expandFraction.targetValue <= 0.5f || state.isDragging) return@LaunchedEffect
        delay(MINI_RESNAP_DEBOUNCE_MS)
        if (state.isDragging) return@LaunchedEffect
        resnapMiniPlayer(state, targets)
    }
}

/**
 * Publishes the expanded player's bottom edge to the host from its own recomposition scope.
 * The host sizes media sheets from it, so it must be state, but rounding to whole dp keeps the
 * host from recomposing on every pixel of the adaptive-height shrink.
 */
@Composable
internal fun ReportExpandedPlayerBottom(
    statusBarHeight: Float,
    videoHeightProvider: () -> Float,
    onChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val bottom by remember(statusBarHeight, density, videoHeightProvider) {
        derivedStateOf {
            with(density) { (statusBarHeight + videoHeightProvider()).toDp() }
                .value
                .roundToInt()
                .dp
        }
    }
    val currentOnChanged by rememberUpdatedState(onChanged)
    SideEffect { currentOnChanged(bottom) }
}
