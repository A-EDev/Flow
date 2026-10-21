package io.github.aedev.flow.ui.screens.playlists

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo

data class PlaylistUiMessage(
    @param:StringRes val stringRes: Int = 0,
    @param:PluralsRes val pluralRes: Int = 0,
    val count: Int = 0,
    val args: List<Any> = emptyList(),
    val undo: QuickActionUndo? = null,
)

data class PlaylistDetailUiState(
    val playlistName: String = "",
    val ownerName: String? = null,
    val description: String = "",
    val isPrivate: Boolean = false,
    val videos: List<Video> = emptyList(),
    val thumbnailUrl: String = "",
    val isLocalPlaylist: Boolean = false,
    val isSaved: Boolean = false,
    val isWatchLater: Boolean = false,
    /** Liked videos: read from the likes, where removing a video unlikes it. */
    val isLikes: Boolean = false,
    val isCourse: Boolean = false,
    /** The library a stored playlist sits in, so it can move to the other; null where it can't move. */
    val isInMusicLibrary: Boolean? = null,
    /** Read through YouTube Music, so saving it puts it in the music library. */
    val isFromMusic: Boolean = false,
    val isLoading: Boolean = true,
    /** Later pages of a YouTube playlist are still arriving. */
    val isLoadingMore: Boolean = false,
    /** Every page arrived and YouTube's count is higher: it left out unavailable videos. */
    val hidesUnavailable: Boolean = false,
    val errorMessage: String? = null,
)
