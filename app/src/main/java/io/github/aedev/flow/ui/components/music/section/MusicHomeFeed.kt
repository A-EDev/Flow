/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.CommunityMusicPlaylist
import io.github.aedev.flow.data.music.model.DailyDiscoverItem
import io.github.aedev.flow.data.music.model.MUSIC_GENRE_SOURCE_PREFIX
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.MusicTimeBucket
import io.github.aedev.flow.innertube.pages.HomePage
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.music.sheet.toCollectionActionItem
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.screens.music.MusicUiState
import io.github.aedev.flow.ui.screens.music.MusicViewModel

/**
 * Everything the music home feed draws, one section component per block.
 *
 * The screen supplies already-collected state and plain callbacks — no ViewModel reaches this far,
 * so the feed cannot start work of its own and the subscription count still tracks the screen.
 */
@Suppress("LongParameterList")
fun LazyListScope.musicHomeFeed(
    uiState: MusicUiState,
    sectionOrder: List<HomeSectionType>,
    quickPickTracks: List<MusicTrack>,
    speedDialTracks: List<MusicTrack>,
    popularArtists: List<MusicTrack>,
    quickPicksGridState: LazyGridState,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onVideoClick: (MusicTrack) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onMoodsClick: (MoodAndGenres.Item?) -> Unit,
    onChipToggle: (HomePage.Chip?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onCollectionMenu: (MusicCollectionActionItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val downloaded = uiState.downloadedTrackIds

    fun trackCollectionMenu(track: MusicTrack) =
        onCollectionMenu(
            MusicCollectionActionItem(
                id = track.videoId,
                title = track.title,
                subtitle = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                isAlbum = track.itemType == io.github.aedev.flow.data.music.model.MusicItemType.ALBUM,
            ),
        )

    fun collectionMenu(
        collection: MusicPlaylist,
        isAlbum: Boolean,
    ) = onCollectionMenu(collection.toCollectionActionItem(isAlbum))

    if (uiState.listenAgain.isNotEmpty()) {
        item(key = "listen_again") {
            MusicTrackCardShelf(
                title = stringResource(R.string.section_listen_again),
                tracks = uiState.listenAgain,
                keyNamespace = "listen_again",
                downloadedTrackIds = downloaded,
                onTrackClick = { onSongClick(it, uiState.listenAgain, "listen_again") },
                onTrackMenu = onTrackMenu,
            )
        }
    }

    if (uiState.homeChips.isNotEmpty()) {
        item(key = "home_chips") {
            MusicHomeChipRow(
                chips = uiState.homeChips,
                selectedChipTitle = uiState.selectedHomeChip?.title,
                onChipToggle = onChipToggle,
            )
        }
    }

    if (uiState.selectedFilter != null) {
        if (uiState.isSearching) {
            item(key = "filter_loading") { FlowFeedProgress() }
        } else {
            items(uiState.allSongs.distinctBy { it.videoId }, key = { "filtered:${it.videoId}" }) { track ->
                MusicTrackItem(
                    track = track,
                    isDownloaded = downloaded.contains(track.videoId),
                    onClick = { onSongClick(track, uiState.allSongs, uiState.selectedFilter) },
                    onLongClick = { onTrackMenu(track) },
                    onMenuClick = { onTrackMenu(track) },
                )
            }
        }
        return
    }

    if (uiState.onRepeatTracks.isNotEmpty()) {
        item(key = "on_repeat") {
            BrainShelf(
                title = stringResource(R.string.section_on_repeat),
                tracks = uiState.onRepeatTracks,
                playFrom = "on_repeat",
                onSongClick = onSongClick,
                onTrackMenu = onTrackMenu,
            )
        }
    }

    val rotationBucket = uiState.rotationBucket
    if (uiState.rotationTracks.isNotEmpty() && rotationBucket != null) {
        item(key = "rotation") {
            BrainShelf(
                title = stringResource(rotationTitleRes(rotationBucket)),
                tracks = uiState.rotationTracks,
                playFrom = "rotation",
                onSongClick = onSongClick,
                onTrackMenu = onTrackMenu,
            )
        }
    }

    if (speedDialTracks.isNotEmpty()) {
        item(key = "speed_dial") {
            SpeedDialSection(
                speedDialTracks = speedDialTracks,
                downloadedTrackIds = downloaded,
                onSongClick = onSongClick,
                onTrackMenu = onTrackMenu,
            )
        }
    }

    if (uiState.rediscoverTracks.isNotEmpty()) {
        item(key = "rediscover") {
            BrainShelf(
                title = stringResource(R.string.section_rediscover),
                tracks = uiState.rediscoverTracks,
                playFrom = "rediscover",
                onSongClick = onSongClick,
                onTrackMenu = onTrackMenu,
            )
        }
    }

    sectionOrder.forEach { sectionType ->
        when (sectionType) {
            HomeSectionType.DAILY_DISCOVER -> {
                dailyDiscover(uiState.dailyDiscover, downloaded, onSongClick, onTrackMenu)
            }

            HomeSectionType.QUICK_PICKS -> {
                quickPicks(quickPickTracks, downloaded, quickPicksGridState, onSongClick, onTrackMenu)
            }

            HomeSectionType.FROM_COMMUNITY -> {
                community(uiState.communityPlaylists, downloaded, onAlbumClick, onSongClick, onTrackMenu, onCollectionMenu)
            }

            HomeSectionType.RECOMMENDED -> {
                trackShelf(
                    id = "recommended",
                    titleRes = R.string.section_recommended,
                    tracks = uiState.recommendedTracks,
                    downloaded = downloaded,
                    playFrom = "recommended",
                    onSongClick = onSongClick,
                    onTrackMenu = onTrackMenu,
                )
            }

            HomeSectionType.SIMILAR_TO -> {
                similarTo(uiState, downloaded, onSongClick, onArtistClick, onAlbumClick, onTrackMenu, ::trackCollectionMenu)
            }

            HomeSectionType.LIVE_PERFORMANCES -> {
                mediaShelf(
                    id = "live_performances",
                    titleRes = R.string.section_live_performances,
                    tracks = uiState.livePerformances,
                    downloaded = downloaded,
                    onTrackClick = { onSongClick(it, uiState.livePerformances, "live_performances") },
                    onPlayAll = {
                        uiState.livePerformances.firstOrNull()?.let {
                            onSongClick(it, uiState.livePerformances, "live_performances")
                        }
                    },
                    onTrackMenu = onTrackMenu,
                )
            }

            HomeSectionType.MUSIC_VIDEOS_FOR_YOU -> {
                val videos = uiState.musicVideosForYou.ifEmpty { uiState.musicVideos }
                mediaShelf(
                    id = "music_videos_for_you",
                    titleRes = R.string.section_music_videos_for_you,
                    tracks = videos,
                    downloaded = downloaded,
                    onTrackClick = onVideoClick,
                    onPlayAll = { videos.firstOrNull()?.let(onVideoClick) },
                    onTrackMenu = onTrackMenu,
                )
            }

            HomeSectionType.MUSIC_VIDEOS -> {
                mediaShelf(
                    id = "music_videos",
                    titleRes = R.string.section_music_videos,
                    tracks = if (uiState.musicVideosForYou.isEmpty()) uiState.musicVideos else emptyList(),
                    downloaded = downloaded,
                    onTrackClick = onVideoClick,
                    onPlayAll = { uiState.musicVideos.firstOrNull()?.let(onVideoClick) },
                    onTrackMenu = onTrackMenu,
                )
            }

            HomeSectionType.GENRES -> {
                genres(uiState.genreTracks, downloaded, onSongClick, onTrackMenu)
            }

            HomeSectionType.DYNAMIC_HOME -> {
                dynamicHome(uiState, downloaded, onSongClick, onAlbumClick, onTrackMenu, ::trackCollectionMenu)
            }

            HomeSectionType.TOP_ALBUMS -> {
                collectionShelf(
                    id = "top_albums",
                    titleRes = R.string.section_top_albums,
                    collections = uiState.topAlbums,
                    isAlbum = true,
                    onAlbumClick = onAlbumClick,
                    onCollectionMenu = ::collectionMenu,
                )
            }

            HomeSectionType.FAVORITE_ARTIST_ALBUMS -> {
                collectionShelf(
                    id = "favorite_artist_albums",
                    titleRes = R.string.section_from_artists_you_love,
                    collections = uiState.favoriteArtistAlbums,
                    isAlbum = true,
                    onAlbumClick = onAlbumClick,
                    onCollectionMenu = ::collectionMenu,
                )
            }

            HomeSectionType.NEW_RELEASES -> {
                newReleases(uiState.newReleases, downloaded, onSongClick, onAlbumClick, onTrackMenu, ::trackCollectionMenu)
            }

            HomeSectionType.CHARTS -> {
                charts(uiState.trendingSongs, downloaded, onSongClick, onTrackMenu)
            }

            HomeSectionType.POPULAR_ARTISTS -> {
                if (popularArtists.isNotEmpty()) {
                    item(key = "popular_artists") {
                        MusicArtistShelf(
                            title = stringResource(R.string.section_popular_artists),
                            artists = popularArtists,
                            key = { "popular_artists:${it.videoId}" },
                            name = { it.artist },
                            thumbnailUrl = { it.thumbnailUrl },
                            onArtistClick = { onArtistClick(it.channelId) },
                        )
                    }
                }
            }

            HomeSectionType.MIXED_FOR_YOU -> {
                collectionShelf(
                    id = "mixed_for_you",
                    titleRes = R.string.section_mixed_for_you,
                    collections = uiState.featuredPlaylists,
                    isAlbum = false,
                    onAlbumClick = onAlbumClick,
                    onCollectionMenu = ::collectionMenu,
                )
            }

            HomeSectionType.MOODS_AND_GENRES -> {
                if (uiState.moodsAndGenres.isNotEmpty()) {
                    item(key = "moods_and_genres") {
                        MusicMoodsShelf(
                            moods = uiState.moodsAndGenres,
                            onMoodClick = { onMoodsClick(it) },
                            onSeeAll = { onMoodsClick(null) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.homeContinuation != null) {
        item(key = "home_continuation") {
            LaunchedEffect(Unit) { onLoadMore() }
            if (uiState.isMoreLoading) FlowFeedProgress() else Box(modifier = Modifier.height(0.dp))
        }
    }
}

private fun LazyListScope.trackShelf(
    id: String,
    titleRes: Int,
    tracks: List<MusicTrack>,
    downloaded: Set<String>,
    playFrom: String,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = id) {
        MusicTrackCardShelf(
            title = stringResource(titleRes),
            tracks = tracks,
            keyNamespace = id,
            downloadedTrackIds = downloaded,
            onTrackClick = { onSongClick(it, tracks, playFrom) },
            onTrackMenu = onTrackMenu,
        )
    }
}

private fun LazyListScope.collectionShelf(
    id: String,
    titleRes: Int,
    collections: List<MusicPlaylist>,
    isAlbum: Boolean,
    onAlbumClick: (String) -> Unit,
    onCollectionMenu: (MusicPlaylist, Boolean) -> Unit,
) {
    if (collections.isEmpty()) return
    item(key = id) {
        MusicCollectionShelf(
            title = stringResource(titleRes),
            collections = collections,
            keyNamespace = id,
            onCollectionClick = { onAlbumClick(it.id) },
            onCollectionMenu = { onCollectionMenu(it, isAlbum) },
        )
    }
}

private fun LazyListScope.mediaShelf(
    id: String,
    titleRes: Int,
    tracks: List<MusicTrack>,
    downloaded: Set<String>,
    onTrackClick: (MusicTrack) -> Unit,
    onPlayAll: () -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = id) {
        MediaTrackListSection(
            title = stringResource(titleRes),
            tracks = tracks,
            downloadedTrackIds = downloaded,
            onPlayAll = onPlayAll,
            onTrackClick = onTrackClick,
            onTrackMenu = onTrackMenu,
        )
    }
}

private fun LazyListScope.dailyDiscover(
    items: List<DailyDiscoverItem>,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "daily_discover") {
        val tracks = items.map { it.recommendation }
        DailyDiscoverShelf(
            items = items,
            downloadedTrackIds = downloaded,
            action =
                tracks.firstOrNull()?.let { first ->
                    MusicSectionAction.PlayAll { onSongClick(first, tracks, "daily_discover") }
                },
            onItemClick = { onSongClick(it.recommendation, tracks, "daily_discover") },
            onItemMenu = { onTrackMenu(it.recommendation) },
        )
    }
}

private fun LazyListScope.quickPicks(
    tracks: List<MusicTrack>,
    downloaded: Set<String>,
    state: LazyGridState,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = "quick_picks") {
        MusicQuickPicksShelf(
            title = stringResource(R.string.section_quick_picks),
            tracks = tracks,
            downloadedTrackIds = downloaded,
            state = state,
            action =
                tracks.firstOrNull()?.let { first ->
                    MusicSectionAction.PlayAll { onSongClick(first, tracks, "quick_picks") }
                },
            onTrackClick = { onSongClick(it, tracks, "quick_picks") },
            onTrackMenu = onTrackMenu,
        )
    }
}

private fun LazyListScope.charts(
    tracks: List<MusicTrack>,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = "charts") {
        MusicChartsShelf(
            title = stringResource(R.string.trending),
            tracks = tracks,
            downloadedTrackIds = downloaded,
            onTrackClick = { onSongClick(it, tracks, "charts") },
            onTrackMenu = onTrackMenu,
        )
    }
}

private fun LazyListScope.community(
    playlists: List<CommunityMusicPlaylist>,
    downloaded: Set<String>,
    onAlbumClick: (String) -> Unit,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onCollectionMenu: (MusicCollectionActionItem) -> Unit,
) {
    if (playlists.isEmpty()) return
    item(key = "from_the_community") {
        CommunityPlaylistsSection(
            playlists = playlists,
            downloadedTrackIds = downloaded,
            onPlaylistClick = { onAlbumClick(it.playlist.id) },
            onPlaylistAction = { onCollectionMenu(it.playlist.toCollectionActionItem(isAlbum = false)) },
            onTrackClick = { track, tracks -> onSongClick(track, tracks, "from_the_community") },
            onTrackMenu = onTrackMenu,
        )
    }
}

private fun LazyListScope.genres(
    genreTracks: Map<String, List<MusicTrack>>,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    genreTracks.entries.take(3).forEachIndexed { index, (genre, tracks) ->
        item(key = "genre:$index:$genre") {
            MusicTrackCardShelf(
                title = stringResource(R.string.genre_mix_template, genre),
                tracks = tracks,
                keyNamespace = "genre_$genre",
                downloadedTrackIds = downloaded,
                onTrackClick = { onSongClick(it, tracks, MUSIC_GENRE_SOURCE_PREFIX + genre) },
                onTrackMenu = onTrackMenu,
            )
        }
    }
}

private fun LazyListScope.similarTo(
    uiState: MusicUiState,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onCollectionMenu: (MusicTrack) -> Unit,
) {
    val dailyMixes = uiState.dailyMixSections
    (dailyMixes + uiState.similarToSections).forEachIndexed { index, section ->
        item(key = "similar_to:$index:${section.title}") {
            MusicTrackCardShelf(
                title = section.title,
                tracks = section.tracks,
                lane = if (index < dailyMixes.size) MusicLane.Hero else MusicLane.Cards,
                keyNamespace = "similar_${index}_${section.title}",
                subtitle = section.label ?: section.subtitle,
                leading =
                    section.thumbnailUrl?.let { url ->
                        { MusicSeedThumbnail(url = url, isArtist = section.isArtistSeed) }
                    },
                action =
                    section.seedId?.takeIf { it.isNotBlank() }?.let { seedId ->
                        MusicSectionAction.Navigate {
                            if (section.isArtistSeed) {
                                onArtistClick(seedId)
                            } else if (seedId.startsWith(MusicViewModel.DAILY_MIX_ID_PREFIX)) {
                                onAlbumClick(seedId)
                            }
                        }
                    },
                downloadedTrackIds = downloaded,
                onTrackClick = { onSongClick(it, section.tracks, section.title) },
                onTrackMenu = onTrackMenu,
                onCollectionClick = { onAlbumClick(it.videoId) },
                onCollectionMenu = onCollectionMenu,
            )
        }
    }
}

private fun LazyListScope.dynamicHome(
    uiState: MusicUiState,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onCollectionMenu: (MusicTrack) -> Unit,
) {
    uiState.dynamicSections
        .filterNot { it.title.isDuplicateOfADedicatedShelf() }
        .forEachIndexed { index, section ->
            item(key = "dynamic:$index:${section.title}") {
                MusicTrackCardShelf(
                    title = section.title,
                    tracks = section.tracks,
                    keyNamespace = "dynamic_${index}_${section.title}",
                    downloadedTrackIds = downloaded,
                    onTrackClick = {
                        val playFrom =
                            uiState.selectedHomeChip
                                ?.title
                                ?.let { chip -> MUSIC_GENRE_SOURCE_PREFIX + chip }
                                ?: section.title
                        onSongClick(it, section.tracks, playFrom)
                    },
                    onTrackMenu = onTrackMenu,
                    onCollectionClick = { onAlbumClick(it.videoId) },
                    onCollectionMenu = onCollectionMenu,
                )
            }
        }
}

private fun LazyListScope.newReleases(
    tracks: List<MusicTrack>,
    downloaded: Set<String>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onCollectionMenu: (MusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = "new_releases") {
        MusicTrackCardShelf(
            title = stringResource(R.string.section_new_releases),
            tracks = tracks.take(10),
            keyNamespace = "new_releases",
            downloadedTrackIds = downloaded,
            trackSubtitle = { stringResource(R.string.subtitle_single_template, it.artist) },
            onTrackClick = { onSongClick(it, tracks, "new_releases") },
            onTrackMenu = onTrackMenu,
            onCollectionClick = { onAlbumClick(it.videoId) },
            onCollectionMenu = onCollectionMenu,
        )
    }
}

/**
 * The titles InnerTube's dynamic home shares with shelves Flow already renders itself.
 */
private fun String.isDuplicateOfADedicatedShelf(): Boolean =
    listOf(
        "Quick picks",
        "Music videos",
        "Music videos for you",
        "Live performances",
        "Long listens",
        "Mixed for you",
        "Recommended",
        "Listen again",
    ).any { contains(it, ignoreCase = true) }

private fun rotationTitleRes(bucket: MusicTimeBucket): Int =
    when (bucket) {
        MusicTimeBucket.WEEKDAY_MORNING, MusicTimeBucket.WEEKEND_MORNING -> R.string.section_rotation_morning
        MusicTimeBucket.WEEKDAY_AFTERNOON, MusicTimeBucket.WEEKEND_AFTERNOON -> R.string.section_rotation_afternoon
        MusicTimeBucket.WEEKDAY_EVENING, MusicTimeBucket.WEEKEND_EVENING -> R.string.section_rotation_evening
        MusicTimeBucket.WEEKDAY_NIGHT, MusicTimeBucket.WEEKEND_NIGHT -> R.string.section_rotation_night
    }
