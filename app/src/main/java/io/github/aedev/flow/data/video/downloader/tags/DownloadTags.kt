package io.github.aedev.flow.data.video.downloader.tags

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadKind { VIDEO, MUSIC, SHORT }

/** Everything Flow embeds into a downloaded file so it can be identified and re-imported later. */
@Serializable
data class DownloadTags(
    val schema: Int = SCHEMA_VERSION,
    val kind: DownloadKind,
    val videoId: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val channelId: String? = null,
    val channelName: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val playlistId: String? = null,
    val releaseDate: String? = null,
    val description: String? = null,
    val sourceUrl: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val thumbnailUrl: String? = null,
    val lyrics: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

private val TopicSuffix = Regex("""(^|\s)-\s*Topic$""")

/**
 * The artist line shown by players: the distinct credited artists, or the uploading channel with
 * YouTube's auto-generated " - Topic" suffix removed when no artist is credited.
 */
fun DownloadTags.displayArtist(): String? {
    val credited =
        artists
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    if (credited.isNotEmpty()) return credited.joinToString(", ")
    return channelName
        ?.trim()
        ?.replace(TopicSuffix, "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
