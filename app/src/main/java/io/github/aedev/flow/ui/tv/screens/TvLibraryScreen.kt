package io.github.aedev.flow.ui.tv.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.toTvVideo

private enum class TvLibrarySection(@StringRes val titleRes: Int) {
    HISTORY(R.string.tv_library_history),
    LIKES(R.string.tv_library_likes),
    WATCH_LATER(R.string.tv_library_watch_later),
    SAVED_SHORTS(R.string.tv_library_saved_shorts),
}

@Composable
fun TvLibraryScreen(
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyRepository = remember { ViewHistory.getInstance(context.applicationContext) }
    val likedRepository = remember { LikedVideosRepository.getInstance(context.applicationContext) }
    val playlistRepository = remember { PlaylistRepository(context.applicationContext) }
    val history by historyRepository.getVideoHistoryFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val liked by likedRepository.getAllLikedVideos().collectAsStateWithLifecycle(initialValue = emptyList())
    val watchLater by playlistRepository.getVideoOnlyWatchLaterFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedShorts by playlistRepository.getVideoOnlySavedShortsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf(TvLibrarySection.HISTORY) }

    val videos = when (selected) {
        TvLibrarySection.HISTORY -> history.map(VideoHistoryEntry::toTvVideo)
        TvLibrarySection.LIKES -> liked.map(LikedVideoInfo::toTvVideo)
        TvLibrarySection.WATCH_LATER -> watchLater
        TvLibrarySection.SAVED_SHORTS -> savedShorts
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.library),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(TvLibrarySection.entries) { section ->
                TvFocusableCard(onClick = { selected = section }) {
                    Text(
                        text = stringResource(section.titleRes),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        fontWeight = if (selected == section) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        if (videos.isEmpty()) {
            TvMessageState(
                title = stringResource(R.string.tv_library_empty),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(videos, key = Video::id) { video ->
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
