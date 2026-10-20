package io.github.aedev.flow.data.local

/** The share of a playlist's videos that must be known songs for the playlist to count as music. */
private const val MUSIC_SHARE = 0.6

/**
 * Takeout doesn't say which playlists were made in YouTube Music, so a playlist counts as music when
 * most of its videos are songs the same export marks as music: library songs, YouTube Music watches
 * and Liked music.
 */
internal fun isTakeoutMusicPlaylist(
    videoIds: List<String>,
    knownMusic: Set<String>,
): Boolean {
    val unique = videoIds.distinct()
    if (unique.isEmpty()) return false
    return unique.count { it in knownMusic } >= unique.size * MUSIC_SHARE
}
