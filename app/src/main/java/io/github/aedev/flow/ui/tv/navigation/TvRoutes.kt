package io.github.aedev.flow.ui.tv.navigation

import android.net.Uri
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.screens.music.collection.MUSIC_COLLECTION_ARG as COLLECTION_ROUTE_ARG

/** Detail routes layered over the rail's root destinations. */
object TvRoutes {
    const val CHANNEL_ARG = "channelRef"
    const val CHANNEL = "channel?ref={$CHANNEL_ARG}"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "playlist/{$PLAYLIST_ARG}"

    const val MUSIC_COLLECTION_ARG = COLLECTION_ROUTE_ARG
    const val MUSIC_COLLECTION = "musicCollection/{$MUSIC_COLLECTION_ARG}"

    const val MUSIC_ARTIST_ARG = "artistChannelId"
    const val MUSIC_ARTIST = "musicArtist/{$MUSIC_ARTIST_ARG}"

    const val HISTORY = "history"

    const val PLAYLISTS_KIND_ARG = "kind"
    const val PLAYLISTS = "playlists?kind={$PLAYLISTS_KIND_ARG}"

    const val SYNC = "sync"
    const val REMOTE_GUIDE = "remoteGuide"

    /** [channelRef] is a full channel URL (preferred) or a bare channel id. */
    fun channel(channelRef: String): String = "channel?ref=${Uri.encode(channelRef)}"

    /** All playlists, or only the video or music ones. */
    fun playlists(kind: MediaKind?): String = if (kind == null) "playlists" else "playlists?kind=${kind.name}"

    fun playlist(playlistId: String): String = "playlist/${Uri.encode(playlistId)}"

    fun musicCollection(collectionId: String): String = "musicCollection/${Uri.encode(collectionId)}"

    fun musicArtist(channelId: String): String = "musicArtist/${Uri.encode(channelId)}"
}
