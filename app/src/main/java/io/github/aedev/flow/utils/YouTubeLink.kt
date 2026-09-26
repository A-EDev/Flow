package io.github.aedev.flow.utils

import java.net.URI
import java.net.URLDecoder

/** What a YouTube, YouTube Music or front-end link points at. */
sealed interface YouTubeLink {
    data class Video(
        val id: String,
        val isMusic: Boolean,
    ) : YouTubeLink

    data class Short(
        val id: String,
    ) : YouTubeLink

    data class Playlist(
        val id: String,
        val isMusic: Boolean,
    ) : YouTubeLink

    data class Album(
        val browseId: String,
    ) : YouTubeLink

    data class Channel(
        val id: String,
        val isMusic: Boolean,
    ) : YouTubeLink

    /** An `@handle`, kept with its `@`. */
    data class ChannelHandle(
        val handle: String,
    ) : YouTubeLink

    /** A `/c/name` or `/user/name` channel link; [kind] is `c` or `user`. */
    data class LegacyChannel(
        val kind: String,
        val name: String,
    ) : YouTubeLink {
        val url: String get() = "https://www.youtube.com/$kind/$name"
    }

    data class Search(
        val query: String,
    ) : YouTubeLink
}

private val URL_IN_TEXT = Regex("""(?i)\bhttps?://\S+""")
private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")
private val CHANNEL_ID = Regex("""UC[A-Za-z0-9_-]{22}""")
private val PLAYLIST_ID = Regex("""[A-Za-z0-9_-]{2,}""")
private val ALBUM_ID = Regex("""MPREb_[A-Za-z0-9_-]+""")
private val HANDLE = Regex("""@[\p{L}\p{N}._-]{3,}""")
private val CHANNEL_NAME = Regex("""[\p{L}\p{N}._-]+""")
private const val TRAILING_PUNCTUATION = ".,;:!?)]}>\"'"

private val YOUTUBE_DOMAINS = listOf("youtube.com", "youtube-nocookie.com")
private val FRONT_END_DOMAINS = listOf("piped.video", "yewtu.be")
private val MUSIC_HOSTS = setOf("music.youtube.com", "m.music.youtube.com")
private val VIDEO_PATHS = setOf("live", "embed", "v", "e")

/**
 * The first YouTube link in [text], which may be a bare link or a share message around one.
 * Anything the parser does not recognise is null: an unknown path is never guessed to be a video.
 */
fun parseYouTubeLink(text: String): YouTubeLink? {
    val uri = firstUri(text.trim()) ?: return null
    val host = uri.host?.lowercase() ?: return null
    val segments =
        uri.path
            .orEmpty()
            .split('/')
            .filter(String::isNotEmpty)
    return when {
        host.isOrIsUnder("youtu.be") -> segments.firstOrNull()?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic = false) }
        YOUTUBE_DOMAINS.any(host::isOrIsUnder) -> pathLink(segments, queryOf(uri), isMusic = host in MUSIC_HOSTS)
        FRONT_END_DOMAINS.any(host::isOrIsUnder) -> pathLink(segments, queryOf(uri), isMusic = false)
        else -> null
    }
}

private fun firstUri(text: String): URI? {
    val candidate =
        URL_IN_TEXT.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }
            ?: text.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }?.let { "https://$it" }
            ?: return null
    return runCatching { URI(candidate) }.getOrNull()
}

private fun String.isOrIsUnder(domain: String): Boolean = this == domain || endsWith(".$domain")

private fun queryOf(uri: URI): Map<String, String> =
    uri.rawQuery
        .orEmpty()
        .split('&')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isEmpty()) null else key to runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
        }.toMap()

private fun pathLink(
    segments: List<String>,
    query: Map<String, String>,
    isMusic: Boolean,
): YouTubeLink? {
    val first = segments.firstOrNull() ?: return null
    val second = segments.getOrNull(1)
    return when {
        first == "watch" -> {
            query["v"]?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic) }
                ?: query["list"]?.let { playlist(it, isMusic) }
        }

        first == "playlist" -> {
            query["list"]?.let { playlist(it, isMusic) }
        }

        first == "shorts" -> {
            second?.takeIf(VIDEO_ID::matches)?.let(YouTubeLink::Short)
        }

        first in VIDEO_PATHS -> {
            second?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic) }
        }

        first == "channel" -> {
            second?.takeIf(CHANNEL_ID::matches)?.let { YouTubeLink.Channel(it, isMusic) }
        }

        first == "browse" -> {
            second?.let { browseLink(it, isMusic) }
        }

        first == "c" || first == "user" -> {
            second?.takeIf(CHANNEL_NAME::matches)?.let { YouTubeLink.LegacyChannel(first, it) }
        }

        first.startsWith("@") -> {
            first.takeIf(HANDLE::matches)?.let(YouTubeLink::ChannelHandle)
        }

        first == "results" -> {
            query["search_query"]?.trim()?.takeIf(String::isNotEmpty)?.let(YouTubeLink::Search)
        }

        else -> {
            null
        }
    }
}

private fun browseLink(
    browseId: String,
    isMusic: Boolean,
): YouTubeLink? =
    when {
        ALBUM_ID.matches(browseId) -> YouTubeLink.Album(browseId)
        CHANNEL_ID.matches(browseId) -> YouTubeLink.Channel(browseId, isMusic)
        browseId.startsWith("VL") -> playlist(browseId.removePrefix("VL"), isMusic)
        else -> null
    }

/** Album playlists (`OLAK5uy_`) are music wherever they are linked from. */
private fun playlist(
    id: String,
    isMusic: Boolean,
): YouTubeLink? = id.takeIf(PLAYLIST_ID::matches)?.let { YouTubeLink.Playlist(it, isMusic || it.startsWith("OLAK5uy_")) }
