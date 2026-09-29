package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.screens.music.collection.toStoredVideo

/** Saves an album or playlist to the library with its tracks, so it plays offline from the library. */
internal suspend fun PlaylistRepository.saveMusicCollection(details: PlaylistDetails) {
    saveExternalMusicPlaylist(
        id = details.id,
        name = details.title,
        description = details.description.orEmpty(),
        thumbnailUrl = details.thumbnailUrl,
    )
    addVideosToPlaylist(details.id, details.tracks.map { it.toStoredVideo() })
}
