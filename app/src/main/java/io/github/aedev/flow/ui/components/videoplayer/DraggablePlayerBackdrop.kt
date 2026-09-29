package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.ui.theme.PlayerGround
import io.github.aedev.flow.ui.theme.PlayerScrimImmersiveBackdrop

/**
 * Ground behind an immersive fullscreen player: a blurred, dimmed copy of the thumbnail fills the
 * letterbox. The blur is static (it only re-renders when the thumbnail changes), which is what
 * keeps it within the player's one sanctioned blur surface.
 */
@Composable
internal fun ImmersiveFullscreenBackdrop(thumbnailUrl: String?) {
    Box(modifier = Modifier.fillMaxSize().background(PlayerGround))
    if (!thumbnailUrl.isNullOrEmpty()) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(60.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.65f,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PlayerScrimImmersiveBackdrop),
        )
    }
}

/**
 * The opaque page behind the expanded player that fades out as it collapses; gone entirely once
 * the sheet has settled mini or the mini player is in wide mode.
 */
@Composable
internal fun CollapsingPlayerScrim(
    state: PlayerDraggableState,
    statusBarHeight: Float,
) {
    val scrimVisible by remember(state) { derivedStateOf { state.expandFraction.value < 0.999f } }
    val inlineMode by remember(state) { derivedStateOf { state.miniSizeScale.value > 1.5f } }
    if (!scrimVisible || inlineMode) return
    val density = LocalDensity.current
    val statusBarHeightDp = with(density) { statusBarHeight.toDp() }
    Box(
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                alpha = (1f - state.expandFraction.value).coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(statusBarHeightDp)
                    .background(PlayerGround),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarHeightDp)
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}
