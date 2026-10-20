package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.Video

/** The videos a walk collected, and whether it reached the playlist's last page. */
internal data class PlaylistWalk(
    val videos: List<Video>,
    val finished: Boolean,
)

/**
 * Reads the pages after a playlist's first while YouTube sends a token it has not sent before.
 * A page can parse to nothing and still point further, so only the token ends the walk. A failed
 * page, a repeated token, the [maxPages] budget or [keepGoing] turning false leave it unfinished,
 * and an unfinished list must never be cached or synced as the whole playlist.
 */
internal suspend fun walkPlaylistPages(
    firstVideos: List<Video>,
    firstToken: String?,
    maxPages: Int,
    nextPage: suspend (continuation: String) -> Pair<List<Video>, String?>?,
    keepGoing: (videos: List<Video>) -> Boolean = { true },
    onPage: (videos: List<Video>) -> Unit = {},
): PlaylistWalk {
    var videos = firstVideos.distinctBy { it.id }
    var token = firstToken
    val seen = HashSet<String>()
    var pages = 1
    while (token != null) {
        if (!keepGoing(videos) || pages >= maxPages || !seen.add(token)) return PlaylistWalk(videos, finished = false)
        val (more, next) = nextPage(token) ?: return PlaylistWalk(videos, finished = false)
        videos = (videos + more).distinctBy { it.id }
        onPage(videos)
        token = next
        pages++
    }
    return PlaylistWalk(videos, finished = true)
}
