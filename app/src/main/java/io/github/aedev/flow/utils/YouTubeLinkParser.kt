package io.github.aedev.flow.utils

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A single video resolved from a shared/copied link.
 *
 * [startPositionMs] is the timestamp the link asked to start at, when it carried one. YouTube
 * share sheets send "watch from here" links, and starting at 0:00 when the user said 4:12 is
 * the one case where following the link literally is not what they wanted.
 */
data class ParsedVideoLink(
    val videoId: String,
    val isShort: Boolean,
    val startPositionMs: Long? = null,
)

/**
 * Resolves a single video id out of a link, or out of the share text YouTube puts on the
 * clipboard ("Check out X: https://youtu.be/…").
 *
 * Strict on purpose: a clipboard holds all kinds of text, so anything that is not a
 * single-video link (playlists, channels, searches, plain prose) has to return null rather
 * than hand the player a bogus id such as "playlist".
 */
object YouTubeLinkParser {
    private const val VIDEO_ID_LENGTH = 11
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{$VIDEO_ID_LENGTH}$")
    private val DURATION_REGEX = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$")
    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_URL_PUNCTUATION = ".,;:!?)]}>\"'"

    private val YOUTUBE_HOSTS =
        setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "m.music.youtube.com",
        )
    private val SHORT_LINK_HOSTS =
        setOf(
            "youtu.be",
            "www.youtu.be",
        )
    private val FRONTEND_HOSTS =
        setOf(
            "piped.video",
            "www.piped.video",
        )
    private val VIDEO_PATH_PREFIXES =
        mapOf(
            "shorts" to true,
            "live" to false,
            "embed" to false,
            "v" to false,
        )

    /** @return the video this text points at, or null when it does not point at one video. */
    fun parseVideoLink(text: String?): ParsedVideoLink? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.findAll(text).firstNotNullOfOrNull { match ->
            parseUrl(match.value.trimEnd { it in TRAILING_URL_PUNCTUATION })
        }
    }

    private fun parseUrl(url: String): ParsedVideoLink? {
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null

        val authority = withoutScheme.substringBefore('/')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        val queryAndFragment = path.substringAfter('?', missingDelimiterValue = "")
        val query = queryAndFragment.substringBefore('#')
        val fragment = path.substringAfter('#', missingDelimiterValue = "")
        val pathSegments = segments(path.substringBefore('?').substringBefore('#'))

        if (host in SHORT_LINK_HOSTS) {
            return videoLink(pathSegments.firstOrNull(), isShort = false, query = query, fragment = fragment)
        }
        if (host !in YOUTUBE_HOSTS && host !in FRONTEND_HOSTS) return null

        val firstSegment = pathSegments.firstOrNull()?.lowercase()

        if (firstSegment == "watch") {
            return videoLink(queryParameter(query, "v"), isShort = false, query = query, fragment = fragment)
        }
        if (firstSegment != null && firstSegment in VIDEO_PATH_PREFIXES) {
            return videoLink(
                candidate = pathSegments.getOrNull(1),
                isShort = VIDEO_PATH_PREFIXES.getValue(firstSegment),
                query = query,
                fragment = fragment,
            )
        }
        return null
    }

    private fun videoLink(
        candidate: String?,
        isShort: Boolean,
        query: String = "",
        fragment: String = "",
    ): ParsedVideoLink? {
        val videoId = candidate?.takeIf { VIDEO_ID_REGEX.matches(it) } ?: return null
        return ParsedVideoLink(
            videoId = videoId,
            isShort = isShort,
            startPositionMs = parseStartPosition(query, fragment),
        )
    }

    /**
     * Reads the timestamp a share link was cut at.
     *
     * YouTube uses `t`, and an embed cut with the legacy editor uses `start`; both accept a bare
     * number of seconds or a `1h2m3s` form. An unparseable value is dropped rather than guessed at,
     * so a link with junk in `t` simply starts at the beginning instead of seeking nowhere.
     */
    private fun parseStartPosition(
        query: String,
        fragment: String,
    ): Long? =
        timestampParameter(query, "t")
            ?: timestampParameter(query, "start")
            ?: timestampParameter(fragment, "t")
            ?: timestampParameter(fragment, "start")

    private fun timestampParameter(
        parameters: String,
        name: String,
    ): Long? = queryParameters(parameters, name).firstNotNullOfOrNull(::parseTimestamp)

    internal fun parseTimestamp(raw: String): Long? {
        val value = raw.trim().lowercase()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { seconds ->
            return seconds
                .takeIf { it >= 0L && it <= Long.MAX_VALUE / 1_000L }
                ?.times(1_000L)
        }
        val match = DURATION_REGEX.matchEntire(value) ?: return null
        val (hours, minutes, seconds) = match.destructured
        val hoursValue = hours.toLongOrNull() ?: if (hours.isEmpty()) 0L else return null
        val minutesValue = minutes.toLongOrNull() ?: if (minutes.isEmpty()) 0L else return null
        val secondsValue = seconds.toLongOrNull() ?: if (seconds.isEmpty()) 0L else return null
        return runCatching {
            val hoursMs = Math.multiplyExact(hoursValue, 3_600_000L)
            val minutesMs = Math.multiplyExact(minutesValue, 60_000L)
            val secondsMs = Math.multiplyExact(secondsValue, 1_000L)
            Math.addExact(Math.addExact(hoursMs, minutesMs), secondsMs)
        }.getOrNull()
    }

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun queryParameter(
        query: String,
        name: String,
    ): String? = queryParameters(query, name).firstOrNull()

    private fun queryParameters(
        query: String,
        name: String,
    ): List<String> =
        query
            .split('&')
            .mapNotNull { parameter ->
                if (!parameter.substringBefore('=').equals(name, ignoreCase = true)) return@mapNotNull null
                val value = parameter.substringAfter('=', missingDelimiterValue = "")
                value.takeIf(String::isNotEmpty)?.let { encoded ->
                    runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrDefault(encoded)
                }
            }
}
