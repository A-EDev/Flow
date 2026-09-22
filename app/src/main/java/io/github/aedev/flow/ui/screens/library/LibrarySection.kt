package io.github.aedev.flow.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.aedev.flow.R

internal enum class LibrarySection(
    @StringRes val titleRes: Int,
) {
    HISTORY(R.string.library_history_label),
    PLAYLISTS(R.string.library_playlists_label),
    WATCH_LATER(R.string.library_watch_later_label),
    LIKES(R.string.library_liked_videos_label),
    DOWNLOADS(R.string.library_downloads_label),
    SAVED_SHORTS(R.string.library_saved_shorts_label),
    LOCAL_MEDIA(R.string.library_local_media_label),
    SETTINGS(R.string.settings),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                HISTORY -> Icons.Outlined.History
                PLAYLISTS -> Icons.AutoMirrored.Outlined.PlaylistPlay
                WATCH_LATER -> Icons.Outlined.WatchLater
                LIKES -> Icons.Outlined.ThumbUp
                DOWNLOADS -> Icons.Outlined.Download
                SAVED_SHORTS -> ImageVector.vectorResource(R.drawable.ic_shorts)
                LOCAL_MEDIA -> Icons.Outlined.PermMedia
                SETTINGS -> Icons.Outlined.Settings
            }

    val title: String
        @Composable get() = stringResource(titleRes)
}

/** Null while the counts are still loading, so a row never flashes a wrong "0". */
@Composable
internal fun LibrarySection.subtitle(counts: LibraryCounts?): String? =
    when (this) {
        LibrarySection.LOCAL_MEDIA -> {
            stringResource(R.string.library_local_media_subtitle)
        }

        LibrarySection.SETTINGS -> {
            stringResource(R.string.library_settings_subtitle)
        }

        LibrarySection.HISTORY -> {
            counts?.let { itemsSubtitle(it.history) }
        }

        LibrarySection.PLAYLISTS -> {
            counts?.let {
                pluralStringResource(R.plurals.playlists_count_template, it.playlists, it.playlists)
            }
        }

        LibrarySection.WATCH_LATER -> {
            counts?.let {
                pluralStringResource(R.plurals.videos_count_template, it.watchLater, it.watchLater)
            }
        }

        LibrarySection.LIKES -> {
            counts?.let { itemsSubtitle(it.likes) }
        }

        LibrarySection.SAVED_SHORTS -> {
            counts?.let {
                pluralStringResource(R.plurals.shorts_count_template, it.savedShorts, it.savedShorts)
            }
        }

        LibrarySection.DOWNLOADS -> {
            counts?.let { downloadsSubtitle(it) }
        }
    }

@Composable
private fun itemsSubtitle(count: Int): String = pluralStringResource(R.plurals.library_items_count, count, count)

@Composable
private fun downloadsSubtitle(counts: LibraryCounts): String {
    val videos = counts.downloadedVideos
    val tracks = counts.downloadedTracks
    if (videos == 0 && tracks == 0) return stringResource(R.string.empty_downloads)

    val videoLabel =
        if (videos > 0) pluralStringResource(R.plurals.videos_count_template, videos, videos) else null
    val trackLabel =
        if (tracks > 0) pluralStringResource(R.plurals.songs_count_template, tracks, tracks) else null
    return listOfNotNull(videoLabel, trackLabel).joinToString(stringResource(R.string.list_separator_dot))
}
