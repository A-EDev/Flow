package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.player.stream.StoryboardLevel
import io.github.aedev.flow.ui.theme.PlayerScrim

/** Width the preview is drawn at, and the size the storyboard level is chosen to match. */
internal val SeekPreviewWidth: Dp = 160.dp

internal val SeekPreviewCorner = 8.dp

/**
 * One storyboard frame, cropped out of the sprite sheet it shares with 25-100 others.
 *
 * Cropped while drawing rather than by laying the sheet out oversized and shifting it: the sheet is
 * several tiles wide, so a layout-based crop has to defeat the parent's constraints and its clip at
 * once, which is what rendered this as a black box. Scaling and translating the draw needs neither,
 * and it keeps the frame under the thumb a redraw rather than a recomposition.
 */
@Composable
internal fun SeekPreviewThumbnail(
    level: StoryboardLevel,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    width: Dp = SeekPreviewWidth,
) {
    val height = width * (level.thumbnailHeight.toFloat() / level.thumbnailWidth.toFloat())
    val sheetUrl by remember(level, positionProvider) {
        derivedStateOf { level.tileAt(positionProvider())?.sheetUrl }
    }
    val sheets = rememberStoryboardSheets(listOfNotNull(sheetUrl))

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
        val tile = level.tileAt(positionProvider()) ?: return@Canvas
        val painter = sheets.painterFor(tile.sheetUrl) ?: return@Canvas
        val scale = size.width / tile.width.toFloat()
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
            translate(left = -tile.left.toFloat(), top = -tile.top.toFloat()) {
                with(painter) { draw(size = Size(tile.sheetWidth.toFloat(), tile.sheetHeight.toFloat())) }
            }
        }
    }
}
