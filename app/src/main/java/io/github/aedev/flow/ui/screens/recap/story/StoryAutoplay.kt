package io.github.aedev.flow.ui.screens.recap.story

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_MS = 6_500

/**
 * The story's clock. [progress] runs 0..1 across the page on screen and is what every animation on
 * the page reads, in the draw phase. It stops while the viewer holds the page, pauses it, drags the
 * pager, or leaves the app, and resumes from where it was; the last page waits instead of advancing.
 */
@Stable
internal class StoryAutoplay(
    private val clock: Animatable<Float, AnimationVector1D>,
) {
    var held by mutableStateOf(false)
    var pausedByUser by mutableStateOf(false)

    val progress: Float get() = clock.value
}

@Composable
internal fun rememberStoryAutoplay(pager: PagerState): StoryAutoplay {
    val clock = remember { Animatable(0f) }
    val autoplay = remember { StoryAutoplay(clock) }
    val scope = rememberCoroutineScope()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var clockPage by remember { mutableIntStateOf(-1) }
    val page = pager.settledPage
    val running =
        lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
            !autoplay.held &&
            !autoplay.pausedByUser &&
            !pager.isScrollInProgress

    LaunchedEffect(page, running) {
        if (page != clockPage) {
            clockPage = page
            clock.snapTo(0f)
        }
        if (!running) return@LaunchedEffect
        val remaining = ((1f - clock.value) * PAGE_MS).roundToInt()
        if (remaining > 0) clock.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        // The turn runs outside this effect: it makes the pager scroll, which is one of this
        // effect's own keys, and would otherwise cancel the turn it started.
        if (page < pager.pageCount - 1) scope.launch { pager.animateScrollToPage(page + 1) }
    }
    return autoplay
}
