package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.ShortsCard
import io.github.aedev.flow.ui.screens.music.MusicTrack

@Composable
internal fun LibraryShelf(
    title: String,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun LibraryMediaShelf(
    title: String,
    items: List<LibraryMediaItem>,
    sourceName: String,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDownloadedMusicClick: (List<DownloadedTrack>, Int) -> Unit
) {
    val musicQueue = remember(items) {
        items.mapNotNull { (it as? LibraryMediaItem.MusicItem)?.track }
    }
    val downloadedVideoQueue = remember(items) {
        items.mapNotNull { (it as? LibraryMediaItem.DownloadedVideoItem)?.download }
    }
    val downloadedMusicQueue = remember(items) {
        items.mapNotNull { (it as? LibraryMediaItem.DownloadedMusicItem)?.download }
    }

    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(
            items = items,
            key = LibraryMediaItem::key,
            contentType = {
                when (it) {
                    is LibraryMediaItem.VideoItem,
                    is LibraryMediaItem.DownloadedVideoItem -> "video"
                    is LibraryMediaItem.MusicItem,
                    is LibraryMediaItem.DownloadedMusicItem -> "music"
                }
            }
        ) { item ->
            when (item) {
                is LibraryMediaItem.VideoItem -> LibraryVideoCard(
                    video = item.video,
                    onClick = { onVideoClick(item.video) }
                )
                is LibraryMediaItem.MusicItem -> LibraryAlbumCard(
                    title = item.track.title,
                    subtitle = item.track.artist,
                    thumbnailUrl = item.track.thumbnailUrl,
                    onClick = { onMusicClick(item.track, musicQueue, sourceName) }
                )
                is LibraryMediaItem.DownloadedVideoItem -> LibraryVideoCard(
                    video = item.download.video,
                    onClick = {
                        val index = downloadedVideoQueue.indexOfFirst {
                            it.video.id == item.download.video.id
                        }
                        if (index >= 0) onDownloadedVideoClick(downloadedVideoQueue, index)
                    }
                )
                is LibraryMediaItem.DownloadedMusicItem -> LibraryAlbumCard(
                    title = item.download.track.title,
                    subtitle = item.download.track.artist,
                    thumbnailUrl = item.download.track.thumbnailUrl,
                    isDownloaded = true,
                    onClick = {
                        val index = downloadedMusicQueue.indexOfFirst {
                            it.track.videoId == item.download.track.videoId
                        }
                        if (index >= 0) onDownloadedMusicClick(downloadedMusicQueue, index)
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryShortsShelf(
    title: String,
    shorts: List<Video>,
    onTitleClick: () -> Unit,
    onShortClick: (Video) -> Unit
) {
    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(shorts, key = Video::id, contentType = { "short" }) { short ->
            ShortsCard(video = short, onClick = { onShortClick(short) })
        }
    }
}

@Composable
internal fun LibraryNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
