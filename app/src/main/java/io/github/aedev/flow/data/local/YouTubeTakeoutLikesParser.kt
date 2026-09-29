package io.github.aedev.flow.data.local

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream
import java.time.Instant

internal data class TakeoutLike(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val likedAt: Long,
    val isMusic: Boolean,
)

internal data class TakeoutLikes(
    val likes: List<TakeoutLike>,
    val totalFound: Int,
)

@Serializable
private data class MyActivityRecord(
    val header: String? = null,
    val title: String? = null,
    val titleUrl: String? = null,
    val subtitles: List<MyActivitySubtitle>? = null,
    val details: List<JsonElement>? = null,
    val time: String? = null,
)

@Serializable
private data class MyActivitySubtitle(
    val name: String? = null,
    val url: String? = null,
)

private data class LikeDecision(
    val at: Long,
    val like: TakeoutLike?,
)

/** YouTube keeps at most this many videos in Liked videos; more would also bloat the likes store. */
internal const val MAX_IMPORTED_LIKES = 5_000

// My Activity names the action only in the account's language; no field marks a like.
private const val LIKED_PREFIX = "Liked "
private const val DISLIKED_PREFIX = "Disliked "
private const val YOUTUBE_MUSIC_HEADER = "YouTube Music"

private val myActivityJson = Json { ignoreUnknownKeys = true }
private val likedVideoIdPattern = Regex("""[?&]v=([\w-]{11})(?:[&#]|$)""")
private val likedChannelIdPattern = Regex("""/channel/(UC[\w-]{22})""")

/** The YouTube activity file of a Takeout "My Activity" export, e.g. `Takeout/My Activity/YouTube/MyActivity.json`. */
internal fun isMyActivityYouTubeEntry(entryName: String): Boolean {
    val segments = entryName.replace('\\', '/').split('/')
    return segments.size >= 3 &&
        segments.none { it.isEmpty() || it == "." || it == ".." } &&
        segments[segments.lastIndex - 1].equals("YouTube", ignoreCase = true) &&
        segments.last().endsWith(".json", ignoreCase = true)
}

/**
 * Streams a My Activity JSON export and keeps each video whose latest like or dislike is a like,
 * newest first, at most [limit] of them. Ads (records with `details`) are skipped.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun readMyActivityLikes(
    input: InputStream,
    limit: Int = MAX_IMPORTED_LIKES,
): TakeoutLikes {
    val decisions = HashMap<String, LikeDecision>()
    myActivityJson
        .decodeToSequence(input, MyActivityRecord.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
        .forEach { record ->
            if (record.details != null) return@forEach
            val title = record.title ?: return@forEach
            val isLike = title.startsWith(LIKED_PREFIX)
            if (!isLike && !title.startsWith(DISLIKED_PREFIX)) return@forEach
            val videoId =
                likedVideoIdPattern
                    .find(record.titleUrl.orEmpty())
                    ?.groupValues
                    ?.get(1) ?: return@forEach
            val at = record.time?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@forEach
            val previous = decisions[videoId]
            if (previous != null && previous.at >= at) return@forEach
            decisions[videoId] = LikeDecision(at, if (isLike) record.toLike(videoId, title, at) else null)
        }
    val likes = decisions.values.mapNotNull { it.like }.sortedByDescending { it.likedAt }
    return TakeoutLikes(likes.take(limit), likes.size)
}

private fun MyActivityRecord.toLike(
    videoId: String,
    title: String,
    likedAt: Long,
): TakeoutLike? {
    val videoTitle = title.removePrefix(LIKED_PREFIX).trim().takeIf(String::isNotEmpty) ?: return null
    val channel = subtitles.orEmpty().firstOrNull()
    return TakeoutLike(
        videoId = videoId,
        title = videoTitle,
        channelName = channel?.name?.trim().orEmpty(),
        channelId =
            likedChannelIdPattern
                .find(channel?.url.orEmpty())
                ?.groupValues
                ?.get(1)
                .orEmpty(),
        likedAt = likedAt,
        isMusic = header == YOUTUBE_MUSIC_HEADER || titleUrl.orEmpty().contains("music.youtube.com"),
    )
}
