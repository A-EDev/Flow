package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelItem
import io.github.aedev.flow.innertube.pages.channel.ChannelSection
import io.github.aedev.flow.innertube.pages.channel.ChannelSectionStyle
import io.github.aedev.flow.innertube.pages.channel.CommunityPost
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.ShortsShelf
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator

/**
 * The channel's own shelves, in the order it arranged them.
 *
 * On a phone every shelf is a vertical list of the rows the rest of the app already uses. An earlier
 * version put them in fixed-width horizontal carousels, which crushed thumbnail-left cards into
 * two-word columns and stretched a Shorts card to half the screen; the card decides its own width
 * here. A wider window turns each shelf into the same card grid the tabs use, previewing two rows.
 */
@Composable
internal fun ChannelHomeSections(
    sections: List<ChannelSection>,
    isLoading: Boolean,
    listState: LazyGridState,
    columnPreference: HomeFeedColumns,
    contentPadding: PaddingValues,
    topInset: Dp,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onSectionMore: (ChannelSection) -> Unit,
    canOpenSection: (ChannelSection) -> Boolean,
    subscribedChannelIds: Set<String>,
    onSubscribeChannel: (io.github.aedev.flow.data.model.Channel, Boolean) -> Unit,
    onAuthorClick: () -> Unit,
    onPostComments: (CommunityPost) -> Unit,
    onPostShare: (CommunityPost) -> Unit,
) {
    if (sections.isEmpty()) {
        if (isLoading) {
            FlowLoadingIndicator(modifier = Modifier.padding(top = topInset))
        }
        return
    }

    val expanded = remember(sections) { mutableStateMapOf<String, Boolean>() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val feedLayout = rememberFeedGridLayout(maxWidth, columnPreference, CHANNEL_MAX_AUTO_COLUMNS)
        val columns = feedLayout.columns
        LazyVerticalGrid(
            columns = feedLayout.cells,
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(if (columns > 1) feedLayout.cardSpacing else 0.dp),
        ) {
            fullSpanItem(key = "top_gap") { Spacer(Modifier.height(8.dp)) }
            sections.forEach { section ->
                homeSection(
                    section = section,
                    columns = columns,
                    isExpanded = expanded[section.id] == true,
                    onToggleExpanded = { expanded[section.id] = expanded[section.id] != true },
                    onVideoClick = onVideoClick,
                    onShortClick = onShortClick,
                    onPlaylistClick = onPlaylistClick,
                    onChannelClick = onChannelClick,
                    onSectionMore = onSectionMore,
                    canOpenSection = canOpenSection,
                    subscribedChannelIds = subscribedChannelIds,
                    onSubscribeChannel = onSubscribeChannel,
                    onAuthorClick = onAuthorClick,
                    onPostComments = onPostComments,
                    onPostShare = onPostShare,
                )
            }
            fullSpanItem(key = "bottom_gap") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun LazyGridScope.homeSection(
    section: ChannelSection,
    columns: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onSectionMore: (ChannelSection) -> Unit,
    canOpenSection: (ChannelSection) -> Boolean,
    subscribedChannelIds: Set<String>,
    onSubscribeChannel: (io.github.aedev.flow.data.model.Channel, Boolean) -> Unit,
    onAuthorClick: () -> Unit,
    onPostComments: (CommunityPost) -> Unit,
    onPostShare: (CommunityPost) -> Unit,
) {
    if (section.style == ChannelSectionStyle.Trailer) {
        val trailer = section.items.filterIsInstance<ChannelItem.VideoItem>().firstOrNull() ?: return
        fullSpanItem(key = section.id) {
            if (columns > 1) {
                CompactVideoCard(
                    video = trailer.video,
                    showChannelName = false,
                    onClick = { onVideoClick(trailer.video) },
                )
            } else {
                VideoCardFullWidth(
                    video = trailer.video,
                    showChannelAvatar = false,
                    showChannelName = false,
                    onClick = { onVideoClick(trailer.video) },
                )
            }
        }
        return
    }

    // Shorts already have a shelf of their own, header and all.
    if (section.items.isNotEmpty() && section.items.all { it is ChannelItem.ShortItem }) {
        val shorts = section.items.map { (it as ChannelItem.ShortItem).video }
        fullSpanItem(key = section.id) {
            ShortsShelf(shorts = shorts, onShortClick = { _, tapped -> onShortClick(tapped.id) })
        }
        return
    }

    fullSpanItem(key = "${section.id}:header") {
        ChannelShelfHeader(
            title = section.title,
            hasMore = canOpenSection(section),
            onClick = { onSectionMore(section) },
        )
    }

    if (columns > 1 && section.items.isNotEmpty() && section.items.all { it is ChannelItem.PostItem }) {
        fullSpanItem(key = "${section.id}:posts") {
            ChannelPostsShelf(
                posts = section.items.map { (it as ChannelItem.PostItem).post },
                onAuthorClick = onAuthorClick,
                onPostComments = onPostComments,
                onPostShare = onPostShare,
            )
        }
        return
    }

    val gridCards = channelCardsFormGrid(columns, section.items.size)
    val previewCount = channelShelfPreviewCount(columns, section.items.size)
    val visible = if (isExpanded) section.items else section.items.take(previewCount)
    items(
        items = visible,
        key = { "${section.id}:${it.shelfKey()}" },
        span = { item ->
            if (gridCards && item !is ChannelItem.PostItem) GridItemSpan(1) else GridItemSpan(maxLineSpan)
        },
    ) { item ->
        Box(modifier = shelfItemMotion()) {
            ShelfItem(
                item = item,
                gridCards = gridCards,
                onVideoClick = onVideoClick,
                onShortClick = onShortClick,
                onPlaylistClick = onPlaylistClick,
                onChannelClick = onChannelClick,
                subscribedChannelIds = subscribedChannelIds,
                onSubscribeChannel = onSubscribeChannel,
                onAuthorClick = onAuthorClick,
                onPostComments = onPostComments,
                onPostShare = onPostShare,
            )
        }
    }

    if (section.items.size > previewCount) {
        item(key = "${section.id}:expander", span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = shelfItemMotion()) {
                ChannelShelfExpander(isExpanded = isExpanded, onClick = onToggleExpanded)
            }
        }
    }
    if (columns == 1) {
        fullSpanItem(key = "${section.id}:gap") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ShelfItem(
    item: ChannelItem,
    gridCards: Boolean,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    subscribedChannelIds: Set<String>,
    onSubscribeChannel: (io.github.aedev.flow.data.model.Channel, Boolean) -> Unit,
    onAuthorClick: () -> Unit,
    onPostComments: (CommunityPost) -> Unit,
    onPostShare: (CommunityPost) -> Unit,
) {
    when (item) {
        is ChannelItem.VideoItem -> {
            ShelfVideoCard(video = item.video, gridCard = gridCards, onClick = { onVideoClick(item.video) })
        }

        is ChannelItem.ShortItem -> {
            ShelfVideoCard(video = item.video, gridCard = gridCards, onClick = { onShortClick(item.video.id) })
        }

        is ChannelItem.PlaylistItem -> {
            if (gridCards) {
                PlaylistCard(
                    playlist = item.playlist,
                    onClick = { onPlaylistClick(item.playlist.id) },
                    layout = PlaylistCardLayout.SHELF,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                PlaylistCard(playlist = item.playlist, onClick = { onPlaylistClick(item.playlist.id) })
            }
        }

        is ChannelItem.RelatedChannelItem -> {
            ChannelRow(
                channel = item.channel,
                onClick = { onChannelClick(item.channel.id) },
                isSubscribed = item.channel.id in subscribedChannelIds,
                onSubscribeClick = { onSubscribeChannel(item.channel, true) },
                onUnsubscribeClick = { onSubscribeChannel(item.channel, false) },
            )
        }

        is ChannelItem.PostItem -> {
            CommunityPostCard(
                post = item.post,
                onAuthorClick = onAuthorClick,
                onCommentsClick = { onPostComments(item.post) },
                onShareClick = { onPostShare(item.post) },
            )
        }
    }
}

/** Expanding a shelf slides the rows below it down and fades the new cards in, on the theme's springs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LazyGridItemScope.shelfItemMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

@Composable
private fun ShelfVideoCard(
    video: Video,
    gridCard: Boolean,
    onClick: () -> Unit,
) {
    if (gridCard) {
        VideoCardFullWidth(
            video = video,
            showChannelAvatar = false,
            showChannelName = false,
            onClick = onClick,
        )
    } else {
        CompactVideoCard(
            video = video,
            showChannelName = false,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPostsShelf(
    posts: List<CommunityPost>,
    onAuthorClick: () -> Unit,
    onPostComments: (CommunityPost) -> Unit,
    onPostShare: (CommunityPost) -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { posts.size },
        preferredItemWidth = PostShelfCardWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PostShelfHeight),
    ) { index ->
        val post = posts[index]
        Card(
            shape = shape,
            colors = CardDefaults.outlinedCardColors(),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .maskClip(shape)
                    .maskBorder(CardDefaults.outlinedCardBorder(), shape),
        ) {
            CommunityPostCard(
                post = post,
                onAuthorClick = onAuthorClick,
                onCommentsClick = { onPostComments(post) },
                onShareClick = { onPostShare(post) },
                showDivider = false,
                compact = true,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelShelfExpander(
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun ChannelItem.shelfKey(): String =
    when (this) {
        is ChannelItem.VideoItem -> "v_${video.id}"
        is ChannelItem.ShortItem -> "s_${video.id}"
        is ChannelItem.PlaylistItem -> "p_${playlist.id}"
        is ChannelItem.RelatedChannelItem -> "c_${channel.id}"
        is ChannelItem.PostItem -> "b_${post.id}"
    }

private val PostShelfCardWidth = 380.dp

/** Header, four lines of text, a 16:9 crop of the item width and the action row. */
private val PostShelfHeight = 440.dp
