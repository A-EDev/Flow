package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.musicArtistKey

private val BRACKETED = Regex("""\s*[\(\[][^\)\]]*[\)\]]""")
private val TRAILING_QUALIFIER = Regex("""\s+-\s+.*$""")
private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")
private const val DURATION_BUCKET_SEC = 10

fun canonicalTrackKey(
    artistKey: String,
    title: String,
    durationSec: Int,
): String {
    val normalizedTitle =
        title
            .replace(BRACKETED, "")
            .replace(TRAILING_QUALIFIER, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .lowercase()
    return "${artistKey.lowercase()}|$normalizedTitle|${durationSec / DURATION_BUCKET_SEC}"
}

fun MusicTrack.canonicalKey(): String {
    val primary = artists.firstOrNull()
    val artistKey = musicArtistKey(primary?.id ?: channelId.takeIf { it.isNotBlank() }, primary?.name ?: artist)
    return canonicalTrackKey(artistKey, title, duration)
}
