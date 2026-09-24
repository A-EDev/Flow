package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video

enum class VideoCardLayout {
    /** The thumbnail across the full width with the details beneath. */
    Stacked,

    /** The thumbnail on the left with the details beside it. */
    Row,
}

object VideoCardDefaults {
    /** One grid column on a phone; callers widen it to line a row up with the grid above. */
    val RowThumbnailWidth: Dp = 168.dp

    /** The card's side inset, and the gap between its thumbnail, avatar and text. */
    val Inset: Dp = 12.dp

    val AvatarSize: Dp = 40.dp

    val RowVerticalPadding: Dp = 8.dp
}

/**
 * One video as a card. Every screen that lists videos draws them through here, so a video looks and
 * behaves the same on Home, Subscriptions, Search, Channel, Categories and the related list.
 *
 * [showChannel] hides the avatar and channel name where the screen already names the channel.
 * [useInternalPadding] insets a stacked card; container-padded grids pass false. [thumbnailWidth]
 * sizes a row's thumbnail, so a row the grid could not fill still lines up with the cards above it.
 */
@Composable
fun MediaVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layout: VideoCardLayout = VideoCardLayout.Stacked,
    showChannel: Boolean = true,
    useInternalPadding: Boolean = true,
    thumbnailWidth: Dp = VideoCardDefaults.RowThumbnailWidth,
) {
    when (layout) {
        VideoCardLayout.Stacked -> {
            VideoCardStacked(
                video = video,
                onClick = onClick,
                showChannel = showChannel,
                useInternalPadding = useInternalPadding,
                modifier = modifier,
            )
        }

        VideoCardLayout.Row -> {
            VideoCardRow(
                video = video,
                onClick = onClick,
                showChannel = showChannel,
                thumbnailWidth = thumbnailWidth,
                modifier = modifier,
            )
        }
    }
}
