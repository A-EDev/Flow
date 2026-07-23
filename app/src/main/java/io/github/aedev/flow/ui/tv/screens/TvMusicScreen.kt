package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.MusicItemType
import io.github.aedev.flow.ui.screens.music.MusicPlaylist
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.tv.components.TvArtistCard
import io.github.aedev.flow.ui.tv.components.TvMediaRow
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvMusicCard
import io.github.aedev.flow.ui.tv.components.TvMusicCollectionCard
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvShimmerRow
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * TV music home: mirrors the mobile feed's section order — Listen Again, Daily
 * Discover, Quick Picks, community/album/playlist shelves, similar-to and
 * server-driven dynamic sections, charts, live performances, and music videos.
 */
@Composable
fun TvMusicScreen(
    viewModel: MusicViewModel,
    onTrackClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    val listenAgain = state.listenAgain.songsOnly()
    val dailyDiscover = remember(state.dailyDiscover) {
        state.dailyDiscover.map { it.recommendation }.songsOnly()
    }
    val quickPicks = state.forYouTracks.songsOnly()
    val effectiveQuickPicks = quickPicks.ifEmpty { state.recommendedTracks.songsOnly() }
    // When Quick Picks falls back to Recommended, don't show the same shelf twice.
    val recommended = if (quickPicks.isNotEmpty()) state.recommendedTracks.songsOnly() else emptyList()
    val charts = state.trendingSongs.songsOnly()
    val newReleases = state.newReleases.songsOnly()
    val livePerformances = state.livePerformances.playable()
    val musicVideos = state.musicVideosForYou.ifEmpty { state.musicVideos }.playable()
    val dynamicSections = remember(state.dynamicSections) {
        state.dynamicSections.filter { section ->
            SURFACED_SECTION_TITLES.none { section.title.contains(it, ignoreCase = true) }
        }
    }
    // Same derivation as mobile: one representative track per charting artist.
    val popularArtists = remember(state.trendingSongs, state.newReleases) {
        (state.trendingSongs + state.newReleases)
            .filter { it.channelId.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy(MusicTrack::artist)
            .take(10)
    }

    val hasContent = effectiveQuickPicks.isNotEmpty() || listenAgain.isNotEmpty() ||
        charts.isNotEmpty() || newReleases.isNotEmpty() || dailyDiscover.isNotEmpty() ||
        state.communityPlaylists.isNotEmpty() || state.topAlbums.isNotEmpty() ||
        state.featuredPlaylists.isNotEmpty() || dynamicSections.isNotEmpty()

    TvScreenScaffold(
        title = stringResource(R.string.screen_title_music),
        modifier = modifier,
    ) {
        ProvideTvColumnPivot {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = dimens.overscanVertical),
            ) {
                when {
                    state.isLoading && !hasContent -> item(key = "music-loading") {
                        TvShimmerRow()
                    }
                    state.error != null && !hasContent -> item(key = "music-error") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = dimens.overscanHorizontal)) {
                            TvMessageState(
                                title = stringResource(R.string.tv_error_loading),
                                message = state.error,
                            )
                        }
                    }
                    !hasContent -> item(key = "music-empty") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = dimens.overscanHorizontal)) {
                            TvMessageState(title = stringResource(R.string.tv_music_empty))
                        }
                    }
                    else -> {
                        if (listenAgain.isNotEmpty()) {
                            item(key = "listen-again") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_listen_again),
                                    tracks = listenAgain,
                                    source = "listen_again",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (dailyDiscover.isNotEmpty()) {
                            item(key = "daily-discover") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_daily_discover),
                                    tracks = dailyDiscover,
                                    source = "daily_discover",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (effectiveQuickPicks.isNotEmpty()) {
                            item(key = "quick-picks") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_quick_picks),
                                    tracks = effectiveQuickPicks,
                                    source = "quick_picks",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (state.communityPlaylists.isNotEmpty()) {
                            item(key = "community-playlists") {
                                TvCollectionShelf(
                                    title = stringResource(R.string.section_from_the_community),
                                    playlists = state.communityPlaylists.map { it.playlist },
                                    onOpenCollection = onOpenCollection,
                                )
                            }
                        }
                        if (recommended.isNotEmpty()) {
                            item(key = "recommended") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_recommended),
                                    tracks = recommended,
                                    source = "recommended",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        state.similarToSections.forEachIndexed { index, section ->
                            val tracks = section.tracks.songsOnly()
                            if (tracks.isNotEmpty()) {
                                item(key = "similar-$index") {
                                    TvMusicShelf(
                                        title = section.title,
                                        tracks = tracks,
                                        source = section.title,
                                        onTrackClick = onTrackClick,
                                    )
                                }
                            }
                        }
                        if (charts.isNotEmpty()) {
                            item(key = "charts") {
                                TvMusicShelf(
                                    title = stringResource(R.string.tv_music_charts),
                                    tracks = charts,
                                    source = "charts",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (livePerformances.isNotEmpty()) {
                            item(key = "live-performances") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_live_performances),
                                    tracks = livePerformances,
                                    source = "live_performances",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (musicVideos.isNotEmpty()) {
                            item(key = "music-videos") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_music_videos_for_you),
                                    tracks = musicVideos,
                                    source = "music_videos",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        dynamicSections.forEachIndexed { index, section ->
                            val tracks = section.tracks.playable()
                            if (tracks.isNotEmpty()) {
                                item(key = "dynamic-$index") {
                                    TvMusicShelf(
                                        title = section.title,
                                        tracks = tracks,
                                        source = section.title,
                                        onTrackClick = onTrackClick,
                                    )
                                }
                            }
                        }
                        if (state.topAlbums.isNotEmpty()) {
                            item(key = "top-albums") {
                                TvCollectionShelf(
                                    title = stringResource(R.string.section_top_albums),
                                    playlists = state.topAlbums,
                                    onOpenCollection = onOpenCollection,
                                )
                            }
                        }
                        if (newReleases.isNotEmpty()) {
                            item(key = "new-releases") {
                                TvMusicShelf(
                                    title = stringResource(R.string.section_new_releases),
                                    tracks = newReleases,
                                    source = "new_releases",
                                    onTrackClick = onTrackClick,
                                )
                            }
                        }
                        if (popularArtists.isNotEmpty()) {
                            item(key = "popular-artists") {
                                TvMediaRow(
                                    items = popularArtists,
                                    key = MusicTrack::videoId,
                                    title = stringResource(R.string.section_popular_artists),
                                ) { track ->
                                    TvArtistCard(
                                        name = track.artist,
                                        thumbnailUrl = track.thumbnailUrl,
                                        onClick = { onOpenArtist(track.channelId) },
                                    )
                                }
                            }
                        }
                        if (state.featuredPlaylists.isNotEmpty()) {
                            item(key = "mixed-for-you") {
                                TvCollectionShelf(
                                    title = stringResource(R.string.section_mixed_for_you),
                                    playlists = state.featuredPlaylists,
                                    onOpenCollection = onOpenCollection,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMusicShelf(
    title: String,
    tracks: List<MusicTrack>,
    source: String,
    onTrackClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
) {
    TvMediaRow(
        items = tracks,
        key = MusicTrack::videoId,
        title = title,
    ) { track ->
        TvMusicCard(
            track = track,
            onClick = { onTrackClick(track, tracks, source) },
        )
    }
}

@Composable
private fun TvCollectionShelf(
    title: String,
    playlists: List<MusicPlaylist>,
    onOpenCollection: (String) -> Unit,
) {
    TvMediaRow(
        items = playlists,
        key = MusicPlaylist::id,
        title = title,
    ) { playlist ->
        TvMusicCollectionCard(
            title = playlist.title,
            subtitle = playlist.author,
            thumbnailUrl = playlist.thumbnailUrl,
            onClick = { onOpenCollection(playlist.id) },
        )
    }
}

/** Shelves the TV screen surfaces itself — hidden from the dynamic pass-through. */
private val SURFACED_SECTION_TITLES = listOf(
    "Quick picks",
    "Music videos",
    "Live performances",
    "Long listens",
    "Mixed for you",
    "Recommended",
    "Listen again",
)

private fun List<MusicTrack>.songsOnly(): List<MusicTrack> =
    asSequence()
        .filter { it.itemType == MusicItemType.SONG && it.videoId.isNotBlank() && !it.isVideoSong }
        .distinctBy(MusicTrack::videoId)
        .toList()

/** Playable tracks including video songs (live performances, music videos). */
private fun List<MusicTrack>.playable(): List<MusicTrack> =
    asSequence()
        .filter { it.itemType == MusicItemType.SONG && it.videoId.isNotBlank() }
        .distinctBy(MusicTrack::videoId)
        .toList()
