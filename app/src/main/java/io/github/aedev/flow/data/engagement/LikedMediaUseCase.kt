package io.github.aedev.flow.data.engagement

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.model.toMusicTrack
import javax.inject.Inject
import io.github.aedev.flow.data.music.PlaylistRepository as MusicLibrary

/**
 * Likes and unlikes from the Liked videos and Liked music pages. A liked song is also kept in the
 * music library's favorites, which the music recommendations read, so both change together.
 */
class LikedMediaUseCase
    @Inject
    constructor(
        private val likes: LikedVideosRepository,
        private val musicLibrary: MusicLibrary,
    ) {
        /** Unlikes [videoIds]; the result is what [restore] needs to undo it. */
        suspend fun unlike(videoIds: Collection<String>): List<LikedVideoInfo> {
            val taken = likes.takeLikes(videoIds)
            taken.filter { it.isMusic }.forEach { musicLibrary.removeFromFavorites(it.videoId) }
            return taken
        }

        suspend fun restore(taken: List<LikedVideoInfo>) {
            likes.restoreLikes(taken)
            taken.filter { it.isMusic }.forEach { musicLibrary.addToFavorites(it.toMusicTrack()) }
        }
    }
