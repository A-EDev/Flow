package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reading surfaces — posts and the about panel — run edge to edge on a phone and stop widening on a
 * tablet, where a full-width line of text is unreadable.
 */
@Composable
internal fun ChannelReadingPane(
    maxWidth: Dp = ReadingPaneMaxWidth,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (this.maxWidth <= maxWidth) {
            content()
            return@BoxWithConstraints
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.widthIn(max = maxWidth)) { content() }
        }
    }
}

private val ReadingPaneMaxWidth = 640.dp

/** Posts carry images, which read better wider than a column of prose. */
internal val PostsPaneMaxWidth = 760.dp
