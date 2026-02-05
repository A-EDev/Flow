package com.flow.youtube.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flow.youtube.innertube.models.AlbumItem
import com.flow.youtube.innertube.models.ArtistItem
import com.flow.youtube.innertube.models.PlaylistItem
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.ui.theme.Dimensions

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (item is SongItem && item.explicit) {
            BadgeIcon.Explicit()
        }
        if (item is SongItem && item.chartPosition != null) {
            BadgeIcon.ChartPosition(item.chartPosition!!)
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    onMenuClick: (() -> Unit)? = null
) {
    val (title, subtitle, thumbnailUrl, shape) = when (item) {
        is SongItem -> {
            val artistNames = item.artists.joinToString { it.name }
            val duration = item.duration?.let { formatDuration(it) } ?: ""
            val subtitleText = if (duration.isNotEmpty()) "$artistNames • $duration" else artistNames
            Quadruple(
                item.title,
                subtitleText,
                item.thumbnail,
                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            )
        }
        is AlbumItem -> {
            val artistNames = item.artists?.joinToString { it.name } ?: ""
            val year = item.year?.toString() ?: ""
            val subtitleText = listOfNotNull(artistNames.takeIf { it.isNotEmpty() }, year.takeIf { it.isNotEmpty() })
                .joinToString(" • ")
            Quadruple(
                item.title,
                subtitleText,
                item.thumbnail,
                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            )
        }
        is ArtistItem -> Quadruple(
            item.title,
            "Artist",
            item.thumbnail,
            CircleShape
        )
        is PlaylistItem -> Quadruple(
            item.title,
            item.author?.name ?: item.songCountText ?: "Playlist",
            item.thumbnail,
            RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
        )
    }
    
    ListItem(
        title = title,
        subtitle = subtitle,
        badges = badges,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                shape = shape,
                isActive = isActive,
                isPlaying = isPlaying,
                isSelected = isSelected,
                modifier = Modifier.size(Dimensions.ListThumbnailSize)
            )
        },
        trailingContent = {
            trailingContent()
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        isSelected = isSelected,
        isActive = isActive,
        modifier = modifier
    )
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    thumbnailRatio: Float = if (item is SongItem && item.musicVideoType != null) 16f / 9 else 1f,
    fillMaxWidth: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (item is SongItem && item.explicit) {
            BadgeIcon.Explicit()
        }
        if (item is AlbumItem && item.explicit) {
            BadgeIcon.Explicit()
        }
    }
) {
    val (title, subtitle, thumbnailUrl, shape) = when (item) {
        is SongItem -> {
            val artistNames = item.artists.joinToString { it.name }
            Quadruple(
                item.title,
                artistNames,
                item.thumbnail,
                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            )
        }
        is AlbumItem -> {
            val artistNames = item.artists?.joinToString { it.name } ?: ""
            val year = item.year?.toString() ?: ""
            val subtitleText = listOfNotNull(artistNames.takeIf { it.isNotEmpty() }, year.takeIf { it.isNotEmpty() })
                .joinToString(" • ")
            Quadruple(
                item.title,
                subtitleText,
                item.thumbnail,
                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
            )
        }
        is ArtistItem -> Quadruple(
            item.title,
            "Artist",
            item.thumbnail,
            CircleShape
        )
        is PlaylistItem -> Quadruple(
            item.title,
            item.author?.name ?: item.songCountText ?: "Playlist",
            item.thumbnail,
            RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
        )
    }
    
    GridItem(
        title = title,
        subtitle = subtitle,
        badges = badges,
        thumbnailContent = {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
            
            if (isActive || isPlaying) {
                ItemThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    shape = shape,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        thumbnailRatio = thumbnailRatio,
        fillMaxWidth = fillMaxWidth,
        modifier = modifier
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun QuickPickTrackItem(
    item: SongItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null
) {
    YouTubeListItem(
        item = item,
        isActive = isActive,
        isPlaying = isPlaying,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}

@Composable
fun ChartTrackItem(
    item: SongItem,
    rank: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null
) {
    ListItem(
        title = item.title,
        subtitle = {
            BadgeIcon.ChartPosition(rank)
            if (item.chartChange != null) {
                BadgeIcon.ChartChange(item.chartChange)
            }
            if (item.explicit) {
                BadgeIcon.Explicit()
            }
            Text(
                text = item.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                modifier = Modifier.size(Dimensions.ListThumbnailSize)
            )
        },
        trailingContent = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        isActive = isActive,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartTrackItem(
    rank: Int,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(Dimensions.ListItemHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp)
    ) {
        BadgeIcon.ChartPosition(rank)
        Spacer(modifier = Modifier.width(8.dp))
        
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimensions.ListThumbnailSize)
                .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius))
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
