package io.github.aedev.flow.ui.screens.player.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.partialRowIndices
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.card.VideoCardLayout

/**
 * Related videos content for LazyListScope.
 */
internal fun LazyListScope.relatedVideosContent(
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH,
) {
    // Video items
    items(
        count = relatedVideos.size,
        key = { index -> relatedVideos[index].id },
    ) { index ->
        val relatedVideo = relatedVideos[index]
        when (cardStyle) {
            PlayerRelatedCardStyle.COMPACT -> {
                MediaVideoCard(
                    video = relatedVideo,
                    layout = VideoCardLayout.Row,
                    onClick = { onVideoClick(relatedVideo) },
                )
            }

            PlayerRelatedCardStyle.FULL_WIDTH -> {
                MediaVideoCard(
                    video = relatedVideo,
                    onClick = { onVideoClick(relatedVideo) },
                )
            }
        }
    }
}

/**
 * The related list on a wide single-pane player: the shared feed grid's columns, chunked into rows
 * of a [androidx.compose.foundation.lazy.LazyColumn] so the video details above keep their own
 * padding. A row the grid cannot fill, and every row in the compact style, is a thumbnail-left card
 * whose thumbnail is one grid column wide.
 */
internal fun LazyListScope.relatedVideosGridContent(
    relatedVideos: List<Video>,
    layout: FeedGridLayout,
    onVideoClick: (Video) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH,
) {
    val columns = if (cardStyle == PlayerRelatedCardStyle.COMPACT) 1 else layout.columns
    val rows = relatedGridRows(relatedVideos.size, columns)

    items(
        count = rows.size,
        key = { index -> rows[index].joinToString { relatedVideos[it].id } },
        contentType = { index -> if (rows[index].size == 1) "related_row" else "related_grid_row" },
    ) { index ->
        val row = rows[index]
        if (row.size == 1) {
            val video = relatedVideos[row.single()]
            MediaVideoCard(
                video = video,
                layout = VideoCardLayout.Row,
                onClick = { onVideoClick(video) },
                thumbnailWidth = layout.thumbnailWidth,
                modifier = Modifier.padding(horizontal = layout.contentPadding),
            )
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.contentPadding, vertical = GridRowVerticalPadding),
            ) {
                row.forEach { videoIndex ->
                    val video = relatedVideos[videoIndex]
                    Box(modifier = Modifier.weight(1f)) {
                        MediaVideoCard(video = video, onClick = { onVideoClick(video) })
                    }
                }
            }
        }
    }
}

/** Full rows of [columns] indices, then each index a full row cannot hold on a row of its own. */
internal fun relatedGridRows(
    count: Int,
    columns: Int,
): List<List<Int>> {
    val partial = partialRowIndices(List(count) { false }, columns)
    val full = (0 until count).filterNot { it in partial }.chunked(columns.coerceAtLeast(1))
    return full + partial.sorted().map(::listOf)
}

private val GridRowVerticalPadding = 6.dp
