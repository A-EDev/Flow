package io.github.aedev.flow.ui.tv.focus

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
private class TvPivotBringIntoViewSpec(
    private val parentFraction: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = offset - containerSize * parentFraction
}

/** Applies pivot scrolling to horizontal rows placed in [content]. */
@Composable
fun ProvideTvRowPivot(content: @Composable () -> Unit) {
    val spec = remember { TvPivotBringIntoViewSpec(parentFraction = 0.08f) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
