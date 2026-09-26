package io.github.aedev.flow.ui

import android.content.Intent
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.utils.YouTubeLink

/** Where a YouTube link opened from another app, or pasted into search, takes the user. */
internal sealed interface LinkDestination {
    data class Video(
        val videoId: String,
    ) : LinkDestination

    data class Short(
        val videoId: String,
    ) : LinkDestination

    data class Page(
        val route: String,
    ) : LinkDestination
}

/** The link text of a VIEW or shared-text intent, or null for every other intent. */
internal fun linkTextOf(intent: Intent): String? =
    when (intent.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> intent.takeIf { it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }

/**
 * Null for a link Flow has no page for: a search, and a mix, which YouTube builds around a video
 * and cannot be browsed on its own.
 */
internal fun linkDestination(link: YouTubeLink): LinkDestination? =
    when {
        link is YouTubeLink.Video && !link.isMusic -> LinkDestination.Video(link.id)
        link is YouTubeLink.Short -> LinkDestination.Short(link.id)
        else -> pageRoute(link)?.let(LinkDestination::Page)
    }

private fun pageRoute(link: YouTubeLink): String? =
    when (link) {
        is YouTubeLink.Video -> musicPlayerRoute(link.id)
        is YouTubeLink.Short, is YouTubeLink.Search -> null
        is YouTubeLink.Playlist -> playlistRoute(link)
        is YouTubeLink.Album -> musicCollectionRoute(link.browseId)
        is YouTubeLink.Channel -> if (link.isMusic) musicArtistRoute(link.id) else youtubeChannelRoute(link.id)
        is YouTubeLink.ChannelHandle -> youtubeChannelRoute(link.handle)
        is YouTubeLink.LegacyChannel -> youtubeChannelRoute(link.url)
    }

private fun playlistRoute(link: YouTubeLink.Playlist): String? =
    when {
        link.id == "LL" -> "playlist/${PlaylistRepository.LIKED_VIDEOS_ID}"
        link.id == "WL" -> "playlist/${PlaylistRepository.WATCH_LATER_ID}"
        link.id == "LM" -> musicCollectionRoute(PlaylistRepository.LIKED_MUSIC_ID)
        link.id.startsWith("RD") && !link.id.startsWith("RDCLAK") -> null
        link.isMusic -> musicCollectionRoute(link.id)
        else -> "playlist/${link.id}"
    }
