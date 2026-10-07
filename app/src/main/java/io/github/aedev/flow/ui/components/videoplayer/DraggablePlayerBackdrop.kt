package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.ui.components.videoplayer.motion.OpenOriginRect
import io.github.aedev.flow.ui.components.videoplayer.motion.collapsingGroundAlpha
import io.github.aedev.flow.ui.components.videoplayer.motion.openGroundCornerRadius
import io.github.aedev.flow.ui.components.videoplayer.motion.openGroundRect
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

/** How dark the page under a growing open gets once the player covers it. */
private const val OPEN_SCRIM_ALPHA = 0.32f

/**
 * The opaque page behind the expanded player. It fades out as the player collapses and is gone once
 * the sheet has settled mini or the mini player is in wide mode. While an open grows out of a card
 * it is that card's rectangle growing to fill the layout instead, over a scrim on the page below.
 */
@Composable
internal fun CollapsingPlayerScrim(
    state: PlayerDraggableState,
    statusBarHeight: Float,
    openRect: () -> OpenOriginRect?,
) {
    val scrimVisible by remember(state) { derivedStateOf { state.expandFraction.value < 0.999f } }
    val inlineMode by remember(state) { derivedStateOf { state.miniSizeScale.value > 1.5f } }
    if (!scrimVisible || inlineMode) return
    val background = MaterialTheme.colorScheme.background
    val scrim = MaterialTheme.colorScheme.scrim
    Box(
        modifier =
            Modifier.fillMaxSize().drawBehind {
                val fraction = state.expandFraction.value
                val origin = openRect()
                if (origin == null) {
                    val alpha = collapsingGroundAlpha(fraction)
                    drawRect(PlayerGround, size = Size(size.width, statusBarHeight), alpha = alpha)
                    drawRect(
                        background,
                        topLeft = Offset(0f, statusBarHeight),
                        size = Size(size.width, size.height - statusBarHeight),
                        alpha = alpha,
                    )
                } else {
                    drawRect(scrim, alpha = OPEN_SCRIM_ALPHA * (1f - fraction).coerceIn(0f, 1f))
                    val ground = openGroundRect(origin, fraction, size.width, size.height)
                    val radius = openGroundCornerRadius(origin, fraction)
                    drawRoundRect(background, ground.topLeft, ground.size, CornerRadius(radius))
                    if (ground.top < statusBarHeight) {
                        drawRect(PlayerGround, ground.topLeft, Size(ground.width, statusBarHeight - ground.top))
                    }
                }
            },
    )
}
