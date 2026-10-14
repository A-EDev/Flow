package io.github.aedev.flow.ui.components.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onFirstVisible
import kotlinx.coroutines.launch

private const val VISIBLE_FRACTION = 0.3f

/**
 * A chart's one-time entrance. It plays once, the first time the chart is mostly on screen, and
 * then the chart is still: nothing keeps drawing afterwards. [progress] is meant to be read only in
 * the draw or layout phase, so the animation never recomposes anything.
 */
@Stable
class StatEntrance internal constructor(
    private val animatable: Animatable<Float, AnimationVector1D>,
    internal val play: () -> Unit,
) {
    val progress: Float get() = animatable.value
}

/** An entrance that stays finished across recomposition, scrolling back and configuration changes. */
@Composable
fun rememberStatEntrance(key: Any = Unit): StatEntrance {
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val animatable = remember(key) { Animatable(if (played) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val scope = rememberCoroutineScope()
    return remember(animatable, spec) {
        StatEntrance(animatable) {
            if (!played) {
                played = true
                scope.launch { animatable.animateTo(1f, spec) }
            }
        }
    }
}

/** Starts [entrance] when this node first becomes mostly visible. */
fun Modifier.playsEntrance(entrance: StatEntrance): Modifier = onFirstVisible(minFractionVisible = VISIBLE_FRACTION) { entrance.play() }
