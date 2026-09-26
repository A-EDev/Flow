package io.github.aedev.flow.utils

private val VIDEO_ID_PATTERNS =
    listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""/live/([A-Za-z0-9_-]{11})"""),
        Regex("""/v/([A-Za-z0-9_-]{11})"""),
    )

private val WATCH_HOSTS =
    listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "piped", "invidious", "yewtu.be")

/** True for a link one of the supported front ends serves a video on. */
fun isWatchUrl(url: String): Boolean {
    val lowered = url.lowercase()
    return WATCH_HOSTS.any { lowered.contains(it) }
}

/** The eleven-character video id in [url], or null when it carries none. */
fun videoIdFromUrl(url: String): String? {
    if (!isWatchUrl(url)) return null
    VIDEO_ID_PATTERNS.forEach { pattern ->
        pattern.find(url)?.let { return it.groupValues[1] }
    }
    return url
        .substringAfterLast('/')
        .substringBefore('?')
        .takeIf { it.length == 11 && it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' } }
}
