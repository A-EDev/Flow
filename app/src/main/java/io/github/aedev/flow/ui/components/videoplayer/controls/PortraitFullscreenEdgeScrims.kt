package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aedev.flow.ui.theme.PlayerScrimEdgeGradient

@Composable
internal fun PortraitFullscreenEdgeScrims(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.24f)
                    .playerEdgeScrim(ScrimEdge.Top, PlayerScrimEdgeGradient),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.24f)
                    .playerEdgeScrim(ScrimEdge.Bottom, PlayerScrimEdgeGradient),
        )
    }
}
