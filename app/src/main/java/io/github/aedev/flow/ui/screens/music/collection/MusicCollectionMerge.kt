package io.github.aedev.flow.ui.screens.music.collection

import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.music.model.MusicTrack

internal suspend fun createMusicPlaylist(
    playlists: PlaylistRepository,
    name: String,
    description: String,
    nowMs: Long = System.currentTimeMillis(),
): PlaylistInfo? =
    runCatching {
        val playlistId = nowMs.toString()
        playlists.createPlaylist(playlistId, name, description, isPrivate = false, isMusic = true)
        playlists.getPlaylistInfo(playlistId)
            ?: PlaylistInfo(
                id = playlistId,
                name = name,
                description = description,
                videoCount = 0,
                thumbnailUrl = "",
                isPrivate = false,
                createdAt = nowMs,
            )
    }.getOrNull()

internal suspend fun copyTracksToPlaylist(
    playlists: PlaylistRepository,
    target: PlaylistInfo,
    tracks: List<MusicTrack>,
): CollectionMessage? {
    if (tracks.isEmpty()) return null
    val added = runCatching { playlists.addVideosToPlaylist(target.id, tracks.map { it.toStoredVideo() }) }.isSuccess
    return if (added) {
        CollectionMessage(
            pluralRes = R.plurals.merge_playlist_success,
            count = tracks.size,
            args = listOf(tracks.size, target.name),
        )
    } else {
        CollectionMessage(stringRes = R.string.toast_failed_to_merge_playlist)
    }
}
