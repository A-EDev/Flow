package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.ShortsShelf
import io.github.aedev.flow.ui.components.VideoCardFullWidth

/**
 * The channel's own shelves, in the order it arranged them.
 *
 * Every shelf is a vertical list of the rows the rest of the app already uses. An earlier version put
 * them in fixed-width horizontal carousels, which crushed thumbnail-left cards into two-word columns
 * and stretched a Shorts card to half the screen; the card decides its own width here.
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
    onSectionMore: (ChannelSection) -> Unit,
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

    val expanded = remember(sections) { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "top_gap") { Spacer(Modifier.height(8.dp)) }
        sections.forEach { section ->
            homeSection(
                section = section,
                isExpanded = expanded[section.id] == true,
                onToggleExpanded = { expanded[section.id] = expanded[section.id] != true },
                onVideoClick = onVideoClick,
                onShortClick = onShortClick,
                onPlaylistClick = onPlaylistClick,
                onChannelClick = onChannelClick,
                onSectionMore = onSectionMore,
            )
        }
        item(key = "bottom_gap") { Spacer(Modifier.height(16.dp)) }
    }
}

private fun LazyListScope.homeSection(
    section: ChannelSection,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onSectionMore: (ChannelSection) -> Unit,
) {
    if (section.style == ChannelSectionStyle.Trailer) {
        val trailer = section.items.filterIsInstance<ChannelItem.VideoItem>().firstOrNull() ?: return
        item(key = section.id) {
            VideoCardFullWidth(
                video = trailer.video,
                showChannelAvatar = false,
                showChannelName = false,
                onClick = { onVideoClick(trailer.video) },
            )
        }
        return
    }

    // Shorts already have a shelf of their own, header and all.
    if (section.items.isNotEmpty() && section.items.all { it is ChannelItem.ShortItem }) {
        val shorts = section.items.map { (it as ChannelItem.ShortItem).video }
        item(key = section.id) {
            ShortsShelf(shorts = shorts, onShortClick = { _, tapped -> onShortClick(tapped.id) })
        }
        return
    }

    item(key = "${section.id}:header") {
        ChannelShelfHeader(
            title = section.title,
            hasMore = section.hasMoreTarget(),
            onClick = { onSectionMore(section) },
        )
    }

    val visible = if (isExpanded) section.items else section.items.take(SHELF_PREVIEW_COUNT)
    items(items = visible, key = { "${section.id}:${it.shelfKey()}" }) { item ->
        when (item) {
            is ChannelItem.VideoItem -> {
                CompactVideoCard(
                    video = item.video,
                    showChannelName = false,
                    onClick = { onVideoClick(item.video) },
                )
            }

            is ChannelItem.ShortItem -> {
                CompactVideoCard(
                    video = item.video,
                    showChannelName = false,
                    onClick = { onShortClick(item.video.id) },
                )
            }

            is ChannelItem.PlaylistItem -> {
                PlaylistCard(playlist = item.playlist, onClick = { onPlaylistClick(item.playlist.id) })
            }

            is ChannelItem.RelatedChannelItem -> {
                ChannelRow(channel = item.channel, onClick = { onChannelClick(item.channel.id) })
            }

            is ChannelItem.PostItem -> {
                Unit
            }
        }
    }

    if (section.items.size > SHELF_PREVIEW_COUNT) {
        item(key = "${section.id}:expander") {
            ChannelShelfExpander(isExpanded = isExpanded, onClick = onToggleExpanded)
        }
    }
    item(key = "${section.id}:gap") { Spacer(Modifier.height(12.dp)) }
}

@Composable
private fun ChannelShelfHeader(
    title: String?,
    hasMore: Boolean,
    onClick: () -> Unit,
) {
    if (title.isNullOrBlank()) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (hasMore) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasMore) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelShelfExpander(
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ChannelSection.hasMoreTarget(): Boolean = morePlaylistId != null || moreParams != null

private fun ChannelItem.shelfKey(): String =
    when (this) {
        is ChannelItem.VideoItem -> "v_${video.id}"
        is ChannelItem.ShortItem -> "s_${video.id}"
        is ChannelItem.PlaylistItem -> "p_${playlist.id}"
        is ChannelItem.RelatedChannelItem -> "c_${channel.id}"
        is ChannelItem.PostItem -> "b_${post.id}"
    }

private const val SHELF_PREVIEW_COUNT = 4
