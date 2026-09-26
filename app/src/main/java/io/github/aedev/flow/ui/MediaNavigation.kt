package io.github.aedev.flow.ui

import android.net.Uri
import androidx.navigation.NavHostController
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator
import io.github.aedev.flow.utils.YouTubeLink

internal const val MUSIC_ARTIST_ROUTE_PATTERN = "artist/{channelId}"
internal const val MUSIC_ARTIST_ROUTE_ARG = "channelId"
internal const val MUSIC_PLAYLIST_ROUTE_PATTERN = "musicPlaylist/{playlistId}"
internal const val MUSIC_PLAYLIST_ROUTE_ARG = "playlistId"
internal const val MUSIC_PLAYER_ROUTE_PATTERN = "musicPlayer/{videoId}"
internal const val MUSIC_PLAYER_ROUTE_ARG = "videoId"
internal const val EQUALIZER_ROUTE = "equalizer"

internal fun musicArtistRoute(artistId: String): String? = artistId.trim().takeIf(String::isNotEmpty)?.let { "artist/${Uri.encode(it)}" }

/** Plays a song in the music player from its id alone, the way a YouTube Music link names it. */
internal fun musicPlayerRoute(videoId: String): String = "musicPlayer/${Uri.encode(videoId)}"

/** Albums and playlists share one page; InnerTube tells them apart by the browse id itself. */
internal fun musicCollectionRoute(collectionId: String): String? =
    collectionId.trim().takeIf(String::isNotEmpty)?.let { "musicPlaylist/${Uri.encode(it)}" }

/** True when the page on top is already [pattern] for [id], so opening it again would stack a duplicate. */
internal fun isOpenMediaPage(
    currentPattern: String?,
    currentId: String?,
    pattern: String,
    id: String,
): Boolean = currentPattern == pattern && currentId == id.trim()

/**
 * The shell's [MediaNavigator]. [beforeNavigate] moves an expanded player out of the way, so a page
 * opened from inside a player sheet is not hidden behind it.
 */
internal class FlowMediaNavigator(
    private val navController: NavHostController,
    private val beforeNavigate: () -> Unit,
) : MediaNavigator {
    override fun openChannel(channelId: String) {
        if (channelId.isBlank()) return
        beforeNavigate()
        navController.navigateToYoutubeChannel(channelId)
    }

    override fun openArtist(artistId: String) =
        open(MUSIC_ARTIST_ROUTE_PATTERN, MUSIC_ARTIST_ROUTE_ARG, artistId, musicArtistRoute(artistId))

    override fun openAlbum(albumId: String) = openCollection(albumId)

    override fun openMusicPlaylist(playlistId: String) = openCollection(playlistId)

    override fun openEqualizer() {
        beforeNavigate()
        if (navController.currentBackStackEntry?.destination?.route == EQUALIZER_ROUTE) return
        navController.navigate(EQUALIZER_ROUTE)
    }

    override fun openLink(link: YouTubeLink): Boolean {
        val destination = linkDestination(link) ?: return false
        beforeNavigate()
        when (destination) {
            is LinkDestination.Video -> navController.navigateToPlayer(destination.videoId)
            is LinkDestination.Short -> navController.openShorts(ShortsQueueSource.SeededFeed(destination.videoId))
            is LinkDestination.Page -> navController.navigate(destination.route)
        }
        return true
    }

    private fun openCollection(id: String) = open(MUSIC_PLAYLIST_ROUTE_PATTERN, MUSIC_PLAYLIST_ROUTE_ARG, id, musicCollectionRoute(id))

    private fun open(
        pattern: String,
        argName: String,
        id: String,
        route: String?,
    ) {
        if (route == null) return
        beforeNavigate()
        val current = navController.currentBackStackEntry
        if (isOpenMediaPage(current?.destination?.route, current?.arguments?.getString(argName), pattern, id)) return
        navController.navigate(route)
    }
}
