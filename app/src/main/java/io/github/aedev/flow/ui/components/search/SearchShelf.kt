package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.paging.SearchShelfKind
import io.github.aedev.flow.ui.components.ShortsShelf
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.channel.CommunityPostCard

/**
 * One of the strips YouTube interleaves between search results: a creator's latest uploads, an
 * inline Shorts row, or their recent community posts.
 *
 * A videos strip arrives with the count YouTube itself collapses it to, so it opens showing the
 * same two rows the web player does rather than all ten.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchShelf(
    shelf: SearchResultItem.ShelfResult,
    onVideoClick: (Video) -> Unit,
    onShortsClick: (shelf: List<Video>, tapped: Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (shelf.kind) {
        SearchShelfKind.SHORTS -> ShortsShelf(shelf.videos, onShortsClick, modifier)
        SearchShelfKind.VIDEOS -> VideoStrip(shelf, onVideoClick, onChannelClick, modifier)
        SearchShelfKind.POSTS -> PostStrip(shelf, onVideoClick, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoStrip(
    shelf: SearchResultItem.ShelfResult,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier,
) {
    var expanded by rememberSaveable(shelf.id) { mutableStateOf(false) }
    val collapsedCount = shelf.collapsedItemCount ?: shelf.videos.size
    val shown =
        remember(shelf.videos, expanded, collapsedCount) {
            if (expanded) shelf.videos else shelf.videos.take(collapsedCount)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        shelf.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = StripHorizontalPadding, vertical = TitleVerticalPadding),
            )
        }
        shown.forEach { video ->
            VideoCardFullWidth(
                video = video,
                onClick = { onVideoClick(video) },
                onChannelClick = onChannelClick,
            )
        }
        if (shelf.videos.size > collapsedCount) {
            TextButton(
                onClick = { expanded = !expanded },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(horizontal = StripHorizontalPadding),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(if (expanded) R.string.search_shelf_show_less else R.string.search_shelf_show_more),
                    modifier = Modifier.padding(start = ButtonIconSpacing),
                )
            }
        }
    }
}

@Composable
private fun PostStrip(
    shelf: SearchResultItem.ShelfResult,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        shelf.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = StripHorizontalPadding, vertical = TitleVerticalPadding),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = StripHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(PostSpacing),
        ) {
            items(shelf.posts, key = { it.id }) { post ->
                Row(modifier = Modifier.width(PostWidth)) {
                    CommunityPostCard(
                        post = post,
                        onAuthorClick = {},
                        onCommentsClick = {},
                        onShareClick = {},
                        onVideoClick = onVideoClick,
                        showDivider = false,
                        compact = true,
                    )
                }
            }
        }
    }
}

private val StripHorizontalPadding = 12.dp
private val TitleVerticalPadding = 8.dp
private val PostSpacing = 12.dp
private val PostWidth = 300.dp
private val ButtonIconSpacing = 8.dp
