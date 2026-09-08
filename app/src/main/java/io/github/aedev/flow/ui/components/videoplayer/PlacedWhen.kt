package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Keeps the content composed and measured but neither drawn nor hit-testable while [visible]
 * returns false. The read happens in the placement pass, so a flip re-places this node only.
 * Compose ships no visibility modifier: `alpha(0f)` still receives pointer input, and dropping the
 * content from composition is exactly the mount cost this exists to keep off the sheet motion.
 */
internal fun Modifier.placedWhen(visible: () -> Boolean): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (visible()) placeable.place(0, 0)
        }
    }
