package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelItem
import io.github.aedev.flow.innertube.pages.channel.ChannelSection
import io.github.aedev.flow.innertube.pages.channel.ChannelSectionStyle
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.VideoCardHorizontal

/**
 * The Home tab: the channel's own shelves, in the order it arranged them.
 *
 * Shelves that parsed to nothing never arrive here, so an absent shelf is absent rather than an empty
 * heading.
 */
@Composable
internal fun ChannelHomeSections(
    sections: List<ChannelSection>,
    isLoading: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    topInset: Dp,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    if (sections.isEmpty()) {
        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = topInset),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "top_gap") { Spacer(Modifier.height(8.dp)) }
        items(items = sections, key = ChannelSection::id) { section ->
            ChannelHomeSection(section, onVideoClick, onShortClick, onPlaylistClick, onChannelClick)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ChannelHomeSection(
    section: ChannelSection,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        section.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (section.style == ChannelSectionStyle.Trailer) {
            section.items.filterIsInstance<ChannelItem.VideoItem>().firstOrNull()?.let { trailer ->
                VideoCardHorizontal(
                    video = trailer.video,
                    showChannelName = false,
                    onClick = { onVideoClick(trailer.video) },
                )
            }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = section.items, key = { it.homeKey() }) { item ->
                Box(modifier = Modifier.width(SHELF_ITEM_WIDTH)) {
                    when (item) {
                        is ChannelItem.VideoItem -> {
                            VideoCardHorizontal(
                                video = item.video,
                                showChannelName = false,
                                onClick = { onVideoClick(item.video) },
                            )
                        }

                        is ChannelItem.ShortItem -> {
                            ChannelShortCard(video = item.video, onClick = { onShortClick(item.video.id) })
                        }

                        is ChannelItem.PlaylistItem -> {
                            PlaylistCard(
                                playlist = item.playlist,
                                onClick = { onPlaylistClick(item.playlist.id) },
                                useInternalPadding = false,
                            )
                        }

                        is ChannelItem.RelatedChannelItem -> {
                            ChannelRow(channel = item.channel, onClick = { onChannelClick(item.channel.id) })
                        }

                        is ChannelItem.PostItem -> {
                            Unit
                        }
                    }
                }
            }
        }
    }
}

private fun ChannelItem.homeKey(): String =
    when (this) {
        is ChannelItem.VideoItem -> "v_${video.id}"
        is ChannelItem.ShortItem -> "s_${video.id}"
        is ChannelItem.PlaylistItem -> "p_${playlist.id}"
        is ChannelItem.RelatedChannelItem -> "c_${channel.id}"
        is ChannelItem.PostItem -> "b_${post.id}"
    }

private val SHELF_ITEM_WIDTH = 240.dp
