package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.PlaylistDetails

/** Saves an album or playlist to the library with its tracks, so it plays offline from the library. */
internal suspend fun PlaylistRepository.saveMusicCollection(details: PlaylistDetails) {
    saveExternalMusicPlaylist(
        id = details.id,
        name = details.title,
        description = details.description.orEmpty(),
        thumbnailUrl = details.thumbnailUrl,
    )
    addVideosToPlaylist(
        details.id,
        details.tracks.map { track ->
            Video(
                id = track.videoId,
                title = track.title,
                channelName = track.artist,
                channelId = track.channelId,
                thumbnailUrl = track.thumbnailUrl,
                duration = track.duration,
                viewCount = track.views,
                uploadDate = "",
                isMusic = true,
            )
        },
    )
}
