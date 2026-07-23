package io.github.aedev.flow.ui.tv.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Pivot scrolling: the focused item is pinned at [parentFraction] of the viewport
 * and the list scrolls underneath it (the standard TV browsing feel). Lazy lists
 * clamp to their bounds, so items near the edges settle naturally.
 */
@OptIn(ExperimentalFoundationApi::class)
private class TvPivotBringIntoViewSpec(
    private val parentFraction: Float,
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 260, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - containerSize * parentFraction
}

/** Applies pivot scrolling to horizontal rows placed in [content]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvRowPivot(content: @Composable () -> Unit) {
    val spec = remember { TvPivotBringIntoViewSpec(parentFraction = 0.08f) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Applies pivot scrolling to vertical containers (grids, columns) in [content]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvColumnPivot(content: @Composable () -> Unit) {
    val spec = remember { TvPivotBringIntoViewSpec(parentFraction = 0.25f) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
