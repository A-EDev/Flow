package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aedev.flow.ui.theme.PlayerScrimEdge

private const val EDGE_SCRIM_HEIGHT_FRACTION = 0.16f

@Composable
internal fun PortraitFullscreenEdgeScrims(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(EDGE_SCRIM_HEIGHT_FRACTION)
                    .background(PlayerScrimEdge),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(EDGE_SCRIM_HEIGHT_FRACTION)
                    .background(PlayerScrimEdge),
        )
    }
}
