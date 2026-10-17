package io.github.aedev.flow.ui.components.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.aedev.flow.R

/** Every Library section, each of which the viewer can hide. The names are stored, so they must never be renamed. */
internal enum class LibrarySection(
    @StringRes val titleRes: Int,
) {
    HISTORY(R.string.library_history_label),
    PLAYLISTS(R.string.library_playlists_label),
    VIDEO_PLAYLISTS(R.string.library_video_playlists),
    MUSIC_PLAYLISTS(R.string.library_music_playlists),
    WATCH_LATER(R.string.library_watch_later_label),
    LIKED_VIDEOS(R.string.liked_videos_playlist),
    LIKED_MUSIC(R.string.liked_music_playlist),
    DOWNLOADS(R.string.library_downloads_label),
    SAVED_SHORTS(R.string.library_saved_shorts_label),
    LOCAL_MEDIA(R.string.library_local_media_label),
    NOTES(R.string.notes_title),
    SETTINGS(R.string.settings),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                HISTORY -> Icons.Outlined.History
                PLAYLISTS, VIDEO_PLAYLISTS -> Icons.AutoMirrored.Outlined.PlaylistPlay
                MUSIC_PLAYLISTS -> Icons.Outlined.LibraryMusic
                WATCH_LATER -> Icons.Outlined.WatchLater
                LIKED_VIDEOS -> Icons.Outlined.ThumbUp
                LIKED_MUSIC -> Icons.Outlined.FavoriteBorder
                DOWNLOADS -> Icons.Outlined.Download
                SAVED_SHORTS -> ImageVector.vectorResource(R.drawable.ic_shorts)
                LOCAL_MEDIA -> Icons.Outlined.PermMedia
                NOTES -> Icons.Outlined.StickyNote2
                SETTINGS -> Icons.Outlined.Settings
            }

    val title: String
        @Composable get() = stringResource(titleRes)

    companion object {
        /** The sections under "Your content" in the order Library lays them out. */
        fun content(separatePlaylistKinds: Boolean): List<LibrarySection> =
            buildList {
                add(HISTORY)
                if (separatePlaylistKinds) addAll(listOf(VIDEO_PLAYLISTS, MUSIC_PLAYLISTS)) else add(PLAYLISTS)
                addAll(listOf(WATCH_LATER, LIKED_VIDEOS, LIKED_MUSIC, DOWNLOADS, SAVED_SHORTS))
            }

        val settingsAndData: List<LibrarySection> = listOf(LOCAL_MEDIA, NOTES, SETTINGS)

        fun fromStored(names: Set<String>): Set<LibrarySection> = entries.filterTo(HashSet()) { it.name in names }
    }
}
