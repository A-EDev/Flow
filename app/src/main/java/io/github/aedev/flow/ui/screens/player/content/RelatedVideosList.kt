package io.github.aedev.flow.ui.screens.player.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Video
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
 * Related videos grid content for LazyListScope.
 */
internal fun LazyListScope.relatedVideosGridContent(
    relatedVideos: List<Video>,
    columns: Int,
    onVideoClick: (Video) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH,
) {
    val chunkedVideos = relatedVideos.chunked(columns)

    items(
        count = chunkedVideos.size,
        key = { index -> chunkedVideos[index].joinToString { it.id } },
    ) { index ->
        val rowVideos = chunkedVideos[index]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (video in rowVideos) {
                Box(modifier = Modifier.weight(1f)) {
                    when (cardStyle) {
                        PlayerRelatedCardStyle.COMPACT -> {
                            MediaVideoCard(
                                video = video,
                                layout = VideoCardLayout.Row,
                                onClick = { onVideoClick(video) },
                            )
                        }

                        PlayerRelatedCardStyle.FULL_WIDTH -> {
                            MediaVideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                            )
                        }
                    }
                }
            }
            val emptySpaces = columns - rowVideos.size
            for (i in 0 until emptySpaces) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
