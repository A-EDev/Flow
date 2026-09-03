package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.ui.components.ShimmerGridItem
import io.github.aedev.flow.ui.components.ShimmerHost
import io.github.aedev.flow.ui.components.ShimmerSectionTitle
import io.github.aedev.flow.ui.components.currentGridThumbnailHeight
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.common.MusicEmptyState
import io.github.aedev.flow.ui.components.music.common.MusicErrorState
import io.github.aedev.flow.ui.components.music.common.musicArtistShape
import io.github.aedev.flow.ui.components.music.common.musicLaneItemWidth
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.section.MusicShelf
import io.github.aedev.flow.ui.components.music.section.MusicTrackShelf

private val BrowseRowMaxWidth = 360.dp
private val BrowseRowPeek = 48.dp
private const val SHIMMER_SECTIONS = 3
private const val SHIMMER_ITEMS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowseScreen(
    onBackClick: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            FlowTopBar(
                title = uiState.title ?: stringResource(R.string.title_browse),
                onBack = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item {
                            ShimmerHost {
                                repeat(SHIMMER_SECTIONS) {
                                    ShimmerSectionTitle(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(SHIMMER_ITEMS) {
                                            ShimmerGridItem()
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                uiState.error != null -> {
                    MusicErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.retry() },
                    )
                }

                uiState.sections.isEmpty() -> {
                    MusicEmptyState(title = stringResource(R.string.empty_browse_content))
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        uiState.sections.forEachIndexed { index, section ->
                            if (section.items.isEmpty()) return@forEachIndexed
                            val sectionKey = "${index}_${section.title?.hashCode() ?: 0}"

                            if (section.items.all { it is SongItem }) {
                                item(key = "songs_$sectionKey") {
                                    val rowWidth = musicLaneItemWidth(maxWidth = BrowseRowMaxWidth, peek = BrowseRowPeek)
                                    MusicTrackShelf(
                                        title = section.title,
                                        items = section.items.filterIsInstance<SongItem>(),
                                        key = { it.stableLazyKey("browse_grid_$sectionKey") },
                                    ) { song ->
                                        MusicTrackItem(
                                            track = convertSongToMusicTrack(song),
                                            density = MusicItemDensity.Compact,
                                            showMenu = false,
                                            shape = MaterialTheme.shapes.medium,
                                            onClick = { onSongClick(song) },
                                            modifier = Modifier.width(rowWidth),
                                        )
                                    }
                                }
                            } else {
                                item(key = "items_$sectionKey") {
                                    val artistShape = musicArtistShape()
                                    val thumbnailHeight = currentGridThumbnailHeight()
                                    MusicShelf(
                                        title = section.title,
                                        items = section.items,
                                        key = { it.stableLazyKey("browse_row_$sectionKey") },
                                    ) { item ->
                                        when (item) {
                                            is SongItem -> {
                                                MusicCollectionCard(
                                                    title = item.title,
                                                    subtitle = item.artists.joinToString { it.name },
                                                    thumbnailUrl = item.thumbnail,
                                                    thumbnailHeight = thumbnailHeight,
                                                    onClick = { onSongClick(item) },
                                                )
                                            }

                                            is AlbumItem -> {
                                                MusicCollectionCard(
                                                    title = item.title,
                                                    subtitle = item.artists?.joinToString { it.name }.orEmpty(),
                                                    thumbnailUrl = item.thumbnail,
                                                    thumbnailHeight = thumbnailHeight,
                                                    onClick = { onAlbumClick(item.id) },
                                                )
                                            }

                                            is ArtistItem -> {
                                                MusicCollectionCard(
                                                    title = item.title,
                                                    thumbnailUrl = item.thumbnail,
                                                    thumbnailHeight = thumbnailHeight,
                                                    shape = artistShape,
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    onClick = { onArtistClick(item.id) },
                                                )
                                            }

                                            is PlaylistItem -> {
                                                MusicCollectionCard(
                                                    title = item.title,
                                                    subtitle = item.author?.name.orEmpty(),
                                                    thumbnailUrl = item.thumbnail,
                                                    thumbnailHeight = thumbnailHeight,
                                                    onClick = { onPlaylistClick(item.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
