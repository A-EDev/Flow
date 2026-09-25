package io.github.aedev.flow.ui.screens.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.engagement.LikedMediaUseCase
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.toMusicTrack
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.github.aedev.flow.data.music.PlaylistRepository as MusicLibrary

private const val SHARING_TIMEOUT_MS = 5_000L

/** The built-in Liked music page: the songs you liked, newest first, read from the device alone. */
@HiltViewModel
class LikedMusicViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        likes: LikedVideosRepository,
        musicLibrary: MusicLibrary,
        private val likedMedia: LikedMediaUseCase,
    ) : ViewModel() {
        private val title = context.getString(R.string.liked_music_playlist)
        private val author = context.getString(R.string.playlist_type_builtin)

        val details: StateFlow<PlaylistDetails?> =
            combine(likes.getLikedMusicFlow(), musicLibrary.favorites) { liked, favorites ->
                val tracks = likedMusicTracks(liked, favorites)
                PlaylistDetails(
                    id = PlaylistRepository.LIKED_MUSIC_ID,
                    title = title,
                    thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl.orEmpty(),
                    author = author,
                    trackCount = tracks.size,
                    tracks = tracks,
                )
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), null)

        /** Unlikes [track]; the result is what an Undo needs to like it again. */
        suspend fun unlike(track: MusicTrack): List<LikedVideoInfo> = likedMedia.unlike(listOf(track.videoId))
    }

/**
 * Liked songs in the order they were liked. A song also kept in the music favorites comes with the
 * album, artists and length saved there; any other is built from what its like recorded.
 */
internal fun likedMusicTracks(
    liked: List<LikedVideoInfo>,
    favorites: List<MusicTrack>,
): List<MusicTrack> {
    val saved = favorites.associateBy { it.videoId }
    return liked.map { saved[it.videoId] ?: it.toMusicTrack() }
}
