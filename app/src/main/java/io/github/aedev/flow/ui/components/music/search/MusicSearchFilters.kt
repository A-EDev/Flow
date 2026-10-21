package io.github.aedev.flow.ui.components.music.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.YouTube.SearchFilter

/** Every filter the music search can show, in the order used when YouTube names none. */
internal val DefaultMusicSearchFilters =
    listOf(
        SearchFilter.FILTER_SONG,
        SearchFilter.FILTER_VIDEO,
        SearchFilter.FILTER_ALBUM,
        SearchFilter.FILTER_ARTIST,
        SearchFilter.FILTER_COMMUNITY_PLAYLIST,
        SearchFilter.FILTER_FEATURED_PLAYLIST,
        SearchFilter.FILTER_PODCAST,
        SearchFilter.FILTER_EPISODE,
        SearchFilter.FILTER_PROFILE,
    )

/**
 * The chips for one search: those YouTube offers for the query, in its order, so a kind with no
 * results gets no chip. A chip's params differ from Flow's in their tail, but the head names the
 * kind, so they are matched on that. With no chip bar in the answer, every filter shows.
 */
internal fun musicSearchFilters(offeredParams: List<String>): List<SearchFilter> =
    offeredParams
        .mapNotNull { param -> DefaultMusicSearchFilters.firstOrNull { it.value.take(KIND_LENGTH) == param.take(KIND_LENGTH) } }
        .distinct()
        .ifEmpty { DefaultMusicSearchFilters }

private const val KIND_LENGTH = 11

@Composable
internal fun SearchFilter.label(): String =
    stringResource(
        when (this) {
            SearchFilter.FILTER_SONG -> R.string.filter_songs
            SearchFilter.FILTER_VIDEO -> R.string.tab_videos
            SearchFilter.FILTER_ALBUM -> R.string.filter_albums
            SearchFilter.FILTER_ARTIST -> R.string.filter_artists
            SearchFilter.FILTER_FEATURED_PLAYLIST -> R.string.filter_featured_playlists
            SearchFilter.FILTER_PODCAST -> R.string.filter_podcasts
            SearchFilter.FILTER_EPISODE -> R.string.filter_episodes
            SearchFilter.FILTER_PROFILE -> R.string.filter_profiles
            else -> R.string.filter_community_playlists
        },
    )
