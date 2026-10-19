package io.github.aedev.flow.ui.tv.navigation

import android.net.Uri
import io.github.aedev.flow.innertube.pages.MoodAndGenres
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

    const val ARTIST_ITEMS_CHANNEL_ARG = "channelId"
    const val ARTIST_ITEMS_BROWSE_ARG = "browseId"
    const val ARTIST_ITEMS_PARAMS_ARG = "params"
    const val ARTIST_ITEMS = "artistItems/{$ARTIST_ITEMS_CHANNEL_ARG}/{$ARTIST_ITEMS_BROWSE_ARG}?params={$ARTIST_ITEMS_PARAMS_ARG}"

    const val HISTORY = "history"

    const val DOWNLOADS = "downloads"

    const val PLAYLISTS_KIND_ARG = "kind"
    const val PLAYLISTS = "playlists?kind={$PLAYLISTS_KIND_ARG}"

    const val MUSIC_BROWSE_ID_ARG = "browseId"
    const val MUSIC_BROWSE_PARAMS_ARG = "params"
    const val MUSIC_BROWSE = "youtube_browse/{$MUSIC_BROWSE_ID_ARG}?params={$MUSIC_BROWSE_PARAMS_ARG}"

    const val MOODS = "moodsAndGenres"

    const val DONATIONS = "donations"

    /** [channelRef] is a full channel URL (preferred) or a bare channel id. */
    fun channel(channelRef: String): String = "channel?ref=${Uri.encode(channelRef)}"

    /** All playlists, or only the video or music ones. */
    fun playlists(kind: MediaKind?): String = if (kind == null) "playlists" else "playlists?kind=${kind.name}"

    fun playlist(playlistId: String): String = "playlist/${Uri.encode(playlistId)}"

    fun musicCollection(collectionId: String): String = "musicCollection/${Uri.encode(collectionId)}"

    /** A mood or genre's page, as YouTube Music browses it. */
    fun musicBrowse(item: MoodAndGenres.Item): String =
        "youtube_browse/${item.endpoint.browseId}?params=${Uri.encode(item.endpoint.params.orEmpty())}"

    /** Everything in one of an artist's sections, the page behind a shelf's "See all". */
    fun artistItems(
        channelId: String,
        browseId: String,
        params: String?,
    ): String = "artistItems/${Uri.encode(channelId)}/${Uri.encode(browseId)}" + params?.let { "?params=${Uri.encode(it)}" }.orEmpty()

    fun musicArtist(channelId: String): String = "musicArtist/${Uri.encode(channelId)}"
}
