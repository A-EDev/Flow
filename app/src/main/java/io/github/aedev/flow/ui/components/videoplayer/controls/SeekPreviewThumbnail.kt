package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import io.github.aedev.flow.player.stream.StoryboardTile
import io.github.aedev.flow.ui.theme.PlayerScrim

/** Width the preview is drawn at, and the size the storyboard level is chosen to match. */
internal val SeekPreviewWidth: Dp = 160.dp

private val SeekPreviewCorner = 8.dp

/**
 * One storyboard frame, cropped out of the sprite sheet it shares with 25-100 others.
 *
 * Cropped while drawing rather than by laying the sheet out oversized and shifting it: the sheet is
 * several tiles wide, so a layout-based crop has to defeat the parent's constraints and its clip at
 * once, which is what rendered this as a black box. Scaling and translating the draw needs neither.
 */
@Composable
internal fun SeekPreviewThumbnail(
    tile: StoryboardTile,
    modifier: Modifier = Modifier,
    width: Dp = SeekPreviewWidth,
) {
    val painter = rememberAsyncImagePainter(model = tile.sheetUrl)
    val height = width * (tile.height.toFloat() / tile.width.toFloat())

    Canvas(
        modifier =
            modifier
                .size(width, height)
                .clip(RoundedCornerShape(SeekPreviewCorner))
                .background(PlayerScrim),
    ) {
        // Drawn unconditionally: AsyncImagePainter resolves its size and starts loading from the
        // draw pass, so skipping the draw until it reports success is a deadlock. It paints nothing
        // while loading, which leaves the scrim behind it showing.
        val scale = size.width / tile.width.toFloat()
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
            translate(left = -tile.left.toFloat(), top = -tile.top.toFloat()) {
                drawSheet(painter, tile)
            }
        }
    }
}

private fun DrawScope.drawSheet(
    painter: Painter,
    tile: StoryboardTile,
) {
    with(painter) {
        draw(size = Size(tile.sheetWidth.toFloat(), tile.sheetHeight.toFloat()))
    }
}
