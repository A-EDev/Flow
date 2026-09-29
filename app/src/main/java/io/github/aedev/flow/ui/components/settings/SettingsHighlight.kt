package io.github.aedev.flow.ui.components.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline

private const val HIGHLIGHT_PEAK_ALPHA = 0.32f
private const val HIGHLIGHT_PULSES = 2

/**
 * Flashes a translucent layer over [content] when it is the option a search result opened, so the
 * eye lands on it after the scroll. Runs a fixed number of pulses and stops; it draws over the row
 * rather than resizing anything, so nothing moves while it plays.
 */
@Composable
internal fun SettingsHighlightFrame(
    key: String,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val active = LocalSettingsHighlight.current == key
    val alpha = remember { Animatable(0f) }
    val color = MaterialTheme.colorScheme.primary
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        repeat(HIGHLIGHT_PULSES) {
            alpha.animateTo(HIGHLIGHT_PEAK_ALPHA, spec)
            alpha.animateTo(0f, spec)
        }
    }

    Box(
        modifier =
            Modifier.drawWithContent {
                drawContent()
                val value = alpha.value
                if (value > 0f) {
                    drawOutline(
                        outline = shape.createOutline(size, layoutDirection, this),
                        color = color.copy(alpha = value),
                    )
                }
            },
    ) {
        content()
    }
}
