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
    val isLoading: Boolean = true,
    /** Later pages of a YouTube playlist are still arriving. */
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
)
