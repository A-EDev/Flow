package io.github.aedev.flow.ui.tv.navigation

import android.net.Uri

/** Detail routes layered over the top-level [TvDestination] tabs. */
object TvRoutes {
    const val CHANNEL_ARG = "channelRef"
    const val CHANNEL = "channel/{$CHANNEL_ARG}"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "playlist/{$PLAYLIST_ARG}"

    const val MUSIC_COLLECTION_ARG = "collectionId"
    const val MUSIC_COLLECTION = "musicCollection/{$MUSIC_COLLECTION_ARG}"

    const val MUSIC_ARTIST_ARG = "artistChannelId"
    const val MUSIC_ARTIST = "musicArtist/{$MUSIC_ARTIST_ARG}"

    const val SYNC = "sync"

    /** [channelRef] is a full channel URL (preferred) or a bare channel id. */
    fun channel(channelRef: String): String = "channel/${Uri.encode(channelRef)}"

    fun playlist(playlistId: String): String = "playlist/${Uri.encode(playlistId)}"

    fun musicCollection(collectionId: String): String =
        "musicCollection/${Uri.encode(collectionId)}"

    fun musicArtist(channelId: String): String = "musicArtist/${Uri.encode(channelId)}"
}
