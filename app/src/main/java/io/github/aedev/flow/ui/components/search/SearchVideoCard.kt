package io.github.aedev.flow.ui.components.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth

/**
 * One video result, in whichever of the app's two card shapes the row can carry.
 *
 * [asThumbnailRow] picks the thumbnail-left row, which is what a lone card on a wide window needs —
 * the full-width card puts a 16:9 image across the whole screen there.
 */
@Composable
fun SearchVideoCard(
    video: Video,
    asThumbnailRow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: ((String) -> Unit)? = null,
) {
    if (asThumbnailRow) {
        CompactVideoCard(
            video = video,
            onClick = onClick,
            onChannelClick = onChannelClick,
            modifier = modifier,
        )
    } else {
        VideoCardFullWidth(
            video = video,
            onClick = onClick,
            onChannelClick = onChannelClick,
            modifier = modifier,
        )
    }
}
