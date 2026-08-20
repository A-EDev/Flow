package io.github.aedev.flow.ui.screens.shorts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.aedev.flow.ui.components.FlowBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How much of the Shorts screen a sheet may take; the reel keeps the rest. */
internal const val SHORTS_SHEET_HEIGHT_FRACTION = 0.62f

/** Landscape has no height to give away, so a sheet there grows instead of the reel shrinking. */
internal const val SHORTS_SHEET_LANDSCAPE_HEIGHT_FRACTION = 0.9f

internal fun shortsSheetReservedPx(
    sheetHeightPx: Float,
    containerHeightPx: Float,
): Float =
    if (containerHeightPx <= 0f || sheetHeightPx <= 0f) {
        0f
    } else {
        sheetHeightPx.coerceAtMost(containerHeightPx * SHORTS_SHEET_HEIGHT_FRACTION)
    }

/**
 * How much of the Shorts screen an open sheet covers, so the reel can shrink out from under it the
 * way the fullscreen player narrows for its side panel.
 *
 * [reservedPx] changes on every frame of a sheet animation, so it is read from the layout phase
 * (see [shortsSheetInset]) and never from composition: the reel re-measures without the page
 * subtree recomposing sixty times a second.
 */
@Stable
internal class ShortsSheetInsetState(
    private val scope: CoroutineScope,
) {
    /** Height of the Shorts screen itself, which is what a sheet is measured and capped against. */
    var containerHeightPx by mutableFloatStateOf(0f)

    var shrinkEnabled by mutableStateOf(false)

    val sheetMaxHeightPx: Float
        get() =
            containerHeightPx *
                if (shrinkEnabled) SHORTS_SHEET_HEIGHT_FRACTION else SHORTS_SHEET_LANDSCAPE_HEIGHT_FRACTION

    var reservedPx by mutableFloatStateOf(0f)
        private set

    private var releaseJob: Job? = null

    /** Track a sheet that runs its own open/close animation, frame by frame. */
    fun follow(sheetHeightPx: Float) {
        releaseJob?.cancel()
        releaseJob = null
        val next = if (shrinkEnabled) shortsSheetReservedPx(sheetHeightPx, containerHeightPx) else 0f
        if (next != reservedPx) reservedPx = next
    }

    /**
     * A sheet that left composition without animating out — picking an option closes it outright —
     * so the reel glides back instead of snapping.
     */
    fun release() {
        if (reservedPx == 0f || releaseJob != null) return
        releaseJob =
            scope.launch {
                animate(
                    initialValue = reservedPx,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) { value, _ -> reservedPx = value }
                releaseJob = null
            }
    }
}

@Composable
internal fun rememberShortsSheetInsetState(): ShortsSheetInsetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ShortsSheetInsetState(scope) }
}

/**
 * Shrinks the reel from the bottom by whatever an open sheet covers.
 *
 * The inset is read inside [layout] on purpose: a sheet animating open re-measures this node
 * without invalidating the composition above it.
 *
 * Applied to the reel itself rather than to the pager, so that the pager's page size — and with it
 * the settled page and everything keyed off it — never changes while a sheet opens.
 */
internal fun Modifier.shortsSheetInset(state: ShortsSheetInsetState): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val reserved = state.reservedPx.roundToInt().coerceIn(0, constraints.maxHeight)
        val height = constraints.maxHeight - reserved
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/**
 * Gives a child back the height [shortsSheetInset] took away from its parent.
 *
 * A Shorts page is one item of the pager, and a pager item lays its root nodes out one after another
 * down the scroll axis — so a sheet emitted as a second root of the page lands a whole screen below
 * it, which is why the sheets are children of the reel's own container. This measures such a child
 * against the page rather than against the reel that has shrunk inside it; it then draws past the
 * bottom of that container on purpose, and nothing between there and the pager's viewport clips.
 */
internal fun Modifier.shortsSheetOutset(state: ShortsSheetInsetState): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val height = constraints.maxHeight + state.reservedPx.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/**
 * A Shorts player settings sheet: the same slide-up as the comments sheet, reporting its own height
 * so the reel lifts clear of it instead of hiding behind it.
 */
@Composable
internal fun ShortsPlayerSheet(
    insets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = with(LocalDensity.current) { insets.sheetMaxHeightPx.toDp() }
    FlowBottomSheet(
        modifier = Modifier.zIndex(2f).shortsSheetOutset(insets),
        onDismiss = onDismiss,
        maxHeight = maxHeight.takeIf { it > 0.dp },
        onVisibleHeightChange = insets::follow,
        content = content,
    )
    DisposableEffect(insets) {
        onDispose { insets.release() }
    }
}
