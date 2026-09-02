/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.DailyDiscoverItem
import io.github.aedev.flow.data.music.model.MusicItemType
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.pages.HomePage
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.ui.components.ContentFilterChip
import io.github.aedev.flow.ui.components.MoodAndGenresButton
import io.github.aedev.flow.ui.components.currentGridThumbnailHeight
import io.github.aedev.flow.ui.components.music.card.DailyDiscoverCard
import io.github.aedev.flow.ui.components.music.common.MusicChartRankBadge
import io.github.aedev.flow.ui.components.music.common.MusicThumbnail
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.theme.Dimensions

/**
 * True when a shelf entry is really a collection wearing a track's shape — InnerTube returns albums
 * and playlists inside track lanes, and tapping one must open the collection, not start playback.
 */
val MusicTrack.isCollection: Boolean
    get() = itemType == MusicItemType.ALBUM || itemType == MusicItemType.PLAYLIST

/**
 * A lane of track cards. Handles the album/playlist routing that every track shelf needs, so the
 * itemType check lives here once instead of at each call site.
 */
@Composable
fun MusicTrackCardShelf(
    title: String,
    tracks: List<MusicTrack>,
    keyNamespace: String,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: MusicSectionAction? = null,
    downloadedTrackIds: Set<String> = emptySet(),
    onCollectionClick: ((MusicTrack) -> Unit)? = null,
    onCollectionMenu: ((MusicTrack) -> Unit)? = null,
    trackSubtitle: @Composable (MusicTrack) -> String = { it.artist },
) {
    val thumbnailHeight = currentGridThumbnailHeight()

    MusicShelf(
        title = title,
        items = tracks,
        key = { "$keyNamespace:${it.videoId}" },
        modifier = modifier,
        subtitle = subtitle,
        leading = leading,
        action = action,
    ) { track ->
        MusicCollectionCard(
            title = track.title,
            subtitle = trackSubtitle(track),
            thumbnailUrl = track.thumbnailUrl,
            thumbnailHeight = thumbnailHeight,
            isDownloaded = downloadedTrackIds.contains(track.videoId),
            onClick = {
                if (track.isCollection && onCollectionClick != null) onCollectionClick(track) else onTrackClick(track)
            },
            onLongClick = {
                if (track.isCollection && onCollectionMenu != null) onCollectionMenu(track) else onTrackMenu(track)
            },
        )
    }
}

/**
 * A lane of album or playlist cards.
 */
@Composable
fun MusicCollectionShelf(
    title: String,
    collections: List<MusicPlaylist>,
    keyNamespace: String,
    onCollectionClick: (MusicPlaylist) -> Unit,
    onCollectionMenu: (MusicPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    action: MusicSectionAction? = null,
) {
    val thumbnailHeight = currentGridThumbnailHeight()

    MusicShelf(
        title = title,
        items = collections,
        key = { "$keyNamespace:${it.id}" },
        modifier = modifier,
        action = action,
    ) { collection ->
        MusicCollectionCard(
            title = collection.title,
            subtitle = collection.author,
            thumbnailUrl = collection.thumbnailUrl,
            thumbnailHeight = thumbnailHeight,
            onClick = { onCollectionClick(collection) },
            onLongClick = { onCollectionMenu(collection) },
        )
    }
}

/**
 * A lane of circular artist portraits.
 */
@Composable
fun MusicArtistShelf(
    title: String,
    artists: List<MusicTrack>,
    keyNamespace: String,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicShelf(
        title = title,
        items = artists,
        key = { "$keyNamespace:${it.videoId}" },
        modifier = modifier,
    ) { artist ->
        MusicCollectionCard(
            title = artist.artist,
            thumbnailUrl = artist.thumbnailUrl,
            thumbnailHeight = 100.dp,
            shape = CircleShape,
            horizontalAlignment = Alignment.CenterHorizontally,
            onClick = { onArtistClick(artist.channelId) },
        )
    }
}

/**
 * A four-row lane of track rows — the Quick Picks shape.
 */
@Composable
fun MusicQuickPicksShelf(
    title: String,
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    action: MusicSectionAction? = null,
    playingVideoId: String? = null,
    downloadedTrackIds: Set<String> = emptySet(),
    state: LazyGridState = rememberLazyGridState(),
) {
    MusicTrackShelf(
        title = title,
        items = tracks,
        key = { "quick_picks:${it.videoId}" },
        modifier = modifier,
        action = action,
        state = state,
    ) { track ->
        MusicTrackItem(
            track = track,
            density = MusicItemDensity.Compact,
            isPlaying = playingVideoId == track.videoId,
            isDownloaded = downloadedTrackIds.contains(track.videoId),
            showMenu = false,
            onClick = { onTrackClick(track) },
            onLongClick = { onTrackMenu(track) },
            modifier = Modifier.width(320.dp),
        )
    }
}

/**
 * A four-row lane of ranked track rows — the Charts shape.
 */
@Composable
fun MusicChartsShelf(
    title: String,
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    playingVideoId: String? = null,
    downloadedTrackIds: Set<String> = emptySet(),
) {
    val ranked = remember(tracks) { tracks.take(20).mapIndexed { index, track -> index + 1 to track } }

    MusicTrackShelf(
        title = title,
        items = ranked,
        key = { (_, track) -> "charts:${track.videoId}" },
        modifier = modifier,
    ) { (rank, track) ->
        MusicTrackItem(
            track = track,
            density = MusicItemDensity.Compact,
            leadingContent = { MusicChartRankBadge(rank) },
            showMenu = false,
            isPlaying = playingVideoId == track.videoId,
            isDownloaded = downloadedTrackIds.contains(track.videoId),
            onClick = { onTrackClick(track) },
            onLongClick = { onTrackMenu(track) },
            modifier = Modifier.width(280.dp),
        )
    }
}

/**
 * The tall Daily Discover lane of seed-and-recommendation cards.
 */
@Composable
fun DailyDiscoverShelf(
    items: List<DailyDiscoverItem>,
    onItemClick: (DailyDiscoverItem) -> Unit,
    onItemMenu: (DailyDiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    action: MusicSectionAction? = null,
    downloadedTrackIds: Set<String> = emptySet(),
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(title = stringResource(R.string.section_daily_discover), action = action)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(336.dp)
                    .padding(bottom = 16.dp),
        ) {
            items(items = items, key = { "daily_discover:${it.recommendation.videoId}" }) { item ->
                DailyDiscoverCard(
                    item = item,
                    isDownloaded = downloadedTrackIds.contains(item.recommendation.videoId),
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemMenu(item) },
                )
            }
        }
    }
}

/**
 * The mood and genre tile grid shown on the home feed.
 */
@Composable
fun MusicMoodsShelf(
    moods: List<MoodAndGenres>,
    onMoodClick: (MoodAndGenres.Item) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (moods.isEmpty()) return

    val moodItems = remember(moods) { moods.flatMap { it.items } }
    val rows = 4
    val buttonWidth = (LocalConfiguration.current.screenWidthDp.dp - 36.dp) / 2
    val gridHeight = (Dimensions.MoodButtonHeight * rows) + (8.dp * (rows - 1))

    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(
            title = stringResource(R.string.section_mood_and_genres),
            action = MusicSectionAction.Navigate(onSeeAll),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
            modifier =
                Modifier
                    .height(gridHeight)
                    .fillMaxWidth(),
        ) {
            items(items = moodItems, key = { it.title }) { item ->
                MoodAndGenresButton(
                    title = item.title,
                    onClick = { onMoodClick(item) },
                    modifier = Modifier.width(buttonWidth),
                )
            }
        }
    }
}

/**
 * The home feed's filter chips.
 */
@Composable
fun MusicHomeChipRow(
    chips: List<HomePage.Chip>,
    selectedChipTitle: String?,
    onChipToggle: (HomePage.Chip?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return

    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = chips, key = { it.title }) { chip ->
            val isSelected = selectedChipTitle == chip.title
            ContentFilterChip(
                title = chip.title,
                isSelected = isSelected,
                onClick = { onChipToggle(if (isSelected) null else chip) },
            )
        }
    }
}

/**
 * The zero-network brain shelves (On Repeat, the time-of-day rotation, Rediscover).
 */
@Composable
fun BrainShelf(
    title: String,
    tracks: List<MusicTrack>,
    playFrom: String,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicTrackCardShelf(
        title = title,
        tracks = tracks,
        keyNamespace = playFrom,
        modifier = modifier,
        onTrackClick = { onSongClick(it, tracks, playFrom) },
        onTrackMenu = onTrackMenu,
    )
}

/**
 * The artwork of the seed a "similar to" shelf was built from.
 */
@Composable
fun MusicSeedThumbnail(
    url: String,
    isArtist: Boolean,
    modifier: Modifier = Modifier,
) {
    MusicThumbnail(
        thumbnailUrl = url,
        size = 40.dp,
        shape = if (isArtist) CircleShape else MaterialTheme.shapes.small,
        modifier = modifier,
    )
}
