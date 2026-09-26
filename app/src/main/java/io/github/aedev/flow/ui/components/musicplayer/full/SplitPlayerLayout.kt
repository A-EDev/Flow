package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

private val SplitHorizontalPadding = 24.dp
private val SplitVerticalPadding = 12.dp
private val SplitGap = 32.dp
private val SplitRowGap = 10.dp

/**
 * Short landscape windows: the cover on the start side at the height the window allows, and the
 * details and controls beside it, centred and scrolling only when a very short window needs it.
 */
@Composable
internal fun SplitPlayerLayout(
    slots: NowPlayingSlots,
    queueFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - queueFraction() / 0.4f).coerceIn(0f, 1f) }
                .then(modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = SplitHorizontalPadding, vertical = SplitVerticalPadding),
    ) {
        val artworkSize = min(maxHeight, maxWidth * 0.42f)
        val columnHeight = maxHeight
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SplitGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.artwork(
                Modifier
                    .size(artworkSize)
                    .graphicsLayer {
                        val scale = 1f - queueFraction() * 0.10f
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = columnHeight),
                verticalArrangement = Arrangement.spacedBy(SplitRowGap, Alignment.CenterVertically),
            ) {
                slots.topBar(Modifier)
                slots.header(Modifier)
                slots.progress(Modifier)
                slots.controls(Modifier)
                slots.actions(Modifier)
            }
        }
    }
}
