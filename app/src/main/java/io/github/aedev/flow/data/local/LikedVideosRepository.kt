package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val Context.likedVideosDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "liked_videos")

class LikedVideosRepository private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        @Volatile
        @Suppress("ktlint:standard:property-naming")
        private var INSTANCE: LikedVideosRepository? = null

        fun getInstance(context: Context): LikedVideosRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LikedVideosRepository(
                    context.likedVideosDataStore,
                ).also { INSTANCE = it }
            }

        // Keys format: "video_{videoId}" -> JSON string with video info
        private fun videoKey(videoId: String) = stringPreferencesKey("video_$videoId")

        private fun likeStateKey(videoId: String) = stringPreferencesKey("like_state_$videoId")

        private const val LIKED_VIDEOS_ORDER_KEY = "liked_videos_order"
    }

    /**
     * Like a video
     */
    suspend fun likeVideo(videoInfo: LikedVideoInfo) {
        dataStore.edit { preferences ->
            // A like re-applied by sync or a restore carries less than an enriched record already holds.
            val stored = preferences[videoKey(videoInfo.videoId)]?.let(::deserializeVideo)
            preferences[videoKey(videoInfo.videoId)] = serializeVideo(stored?.let(videoInfo::keepingDetailsOf) ?: videoInfo)
            preferences[likeStateKey(videoInfo.videoId)] = "LIKED"

            // Update order list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            val orderList =
                if (currentOrder.isEmpty()) {
                    mutableListOf()
                } else {
                    currentOrder.split(",").toMutableList()
                }

            if (!orderList.contains(videoInfo.videoId)) {
                orderList.add(0, videoInfo.videoId) // Add to front
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }

    /**
     * Dislike a video (removes like if exists)
     */
    suspend fun dislikeVideo(videoId: String) {
        dataStore.edit { preferences ->
            preferences[likeStateKey(videoId)] = "DISLIKED"

            // Remove from liked videos list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }

    /**
     * Get like state for a video (LIKED, DISLIKED, or null)
     */
    fun getLikeState(videoId: String): Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[likeStateKey(videoId)]
        }

    /**
     * Remove like/dislike from a video
     */
    suspend fun removeLikeState(videoId: String) {
        dataStore.edit { preferences ->
            preferences.remove(likeStateKey(videoId))

            // Remove from liked videos list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }

    /** Replaces the stored details of a video that is still liked; its place and like date stay. */
    suspend fun updateDetails(videoInfo: LikedVideoInfo) {
        dataStore.edit { preferences ->
            val key = videoKey(videoInfo.videoId)
            val stored = preferences[key]?.let(::deserializeVideo) ?: return@edit
            preferences[key] = serializeVideo(videoInfo.copy(likedAt = stored.likedAt, isMusic = stored.isMusic))
        }
    }

    /** Unlikes [videoIds] as one change and returns what [restoreLikes] needs to put them back. */
    suspend fun takeLikes(videoIds: Collection<String>): List<LikedVideoInfo> {
        val ids = videoIds.toSet()
        var taken = emptyList<LikedVideoInfo>()
        dataStore.edit { preferences ->
            val order = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)].orEmpty().split(",").filter(String::isNotEmpty)
            taken = order.filter { it in ids }.mapNotNull { id -> preferences[videoKey(id)]?.let(::deserializeVideo) }
            taken.forEach { preferences.remove(likeStateKey(it.videoId)) }
            preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = order.filterNot { it in ids }.joinToString(",")
        }
        return taken
    }

    /** Likes [likes] again, each back in its place by the date it was first liked. */
    suspend fun restoreLikes(likes: List<LikedVideoInfo>) {
        if (likes.isEmpty()) return
        dataStore.edit { preferences ->
            val orderKey = stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)
            val order = preferences[orderKey].orEmpty().split(",").filter(String::isNotEmpty)
            likes.forEach { like ->
                preferences[videoKey(like.videoId)] = serializeVideo(like)
                preferences[likeStateKey(like.videoId)] = "LIKED"
            }
            val likedAt = likes.associate { it.videoId to it.likedAt }
            val merged = (order + likes.map { it.videoId }).distinct()
            val dated = merged.map { id -> id to (likedAt[id] ?: preferences[videoKey(id)]?.let(::deserializeVideo)?.likedAt ?: 0L) }
            preferences[orderKey] = restoredOrder(order, dated).joinToString(",")
        }
    }

    /**
     * Get all liked videos (mixed)
     */
    fun getAllLikedVideos(): Flow<List<LikedVideoInfo>> =
        dataStore.data.map { preferences ->
            val orderString = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (orderString.isEmpty()) {
                emptyList()
            } else {
                val orderList = orderString.split(",")
                orderList.mapNotNull { videoId ->
                    val videoData = preferences[videoKey(videoId)]
                    videoData?.let { deserializeVideo(it) }
                }
            }
        }

    fun getLikedVideosFlow(): Flow<List<LikedVideoInfo>> = getAllLikedVideos().map { list -> list.filter { !it.isMusic } }

    fun getLikedMusicFlow(): Flow<List<LikedVideoInfo>> = getAllLikedVideos().map { list -> list.filter { it.isMusic } }

    private fun serializeVideo(video: LikedVideoInfo): String = LikeJson.encodeToString(LikedVideoInfo.serializer(), video)
}

private val LikeJson =
    Json {
        ignoreUnknownKeys = true
        // likedAt defaults to the current time, so a default must never be left out and re-evaluated on read.
        encodeDefaults = true
    }

/**
 * Reads a stored like. Records are JSON; older ones were `id|title|thumbnail|channel|likedAt|isMusic`,
 * which a title or channel holding a `|` split in the wrong places, so those are read from both ends.
 */
internal fun deserializeVideo(data: String): LikedVideoInfo? {
    if (data.startsWith("{")) return runCatching { LikeJson.decodeFromString(LikedVideoInfo.serializer(), data) }.getOrNull()
    val parts = data.split("|")
    if (parts.size < LEGACY_MIN_PARTS) return null
    val hasMusicFlag = parts.last() == "true" || parts.last() == "false"
    val likedAtIndex = if (hasMusicFlag) parts.lastIndex - 1 else parts.lastIndex
    val likedAt = parts[likedAtIndex].toLongOrNull() ?: return null
    val middle = parts.subList(1, likedAtIndex)
    val thumbnailIndex = middle.indexOfFirst { it.startsWith("http") }.takeIf { it >= 0 } ?: 1.coerceAtMost(middle.lastIndex)
    return LikedVideoInfo(
        videoId = parts[0],
        title = middle.subList(0, thumbnailIndex).joinToString("|"),
        thumbnail = middle.getOrElse(thumbnailIndex) { "" },
        channelName = middle.drop(thumbnailIndex + 1).joinToString("|"),
        likedAt = likedAt,
        isMusic = hasMusicFlag && parts.last().toBoolean(),
    )
}

private const val LEGACY_MIN_PARTS = 5

/** Existing likes keep their order; each restored one goes back before the first like that is older than it. */
internal fun restoredOrder(
    order: List<String>,
    dated: List<Pair<String, Long>>,
): List<String> {
    val present = order.toHashSet()
    val result = order.toMutableList()
    val likedAt = dated.toMap()
    dated.filter { (id, _) -> id !in present }.sortedByDescending { it.second }.forEach { (id, at) ->
        val index = result.indexOfFirst { (likedAt[it] ?: 0L) < at }
        if (index < 0) result.add(id) else result.add(index, id)
    }
    return result
}

@Serializable
data class LikedVideoInfo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val likedAt: Long = System.currentTimeMillis(),
    val isMusic: Boolean = false,
    // Nullable and zero by default: Gson backups written before these existed leave them unset.
    val channelId: String? = null,
    val durationSeconds: Int = 0,
) {
    /** This like, with the channel and length [stored] already knew where this one lacks them. */
    fun keepingDetailsOf(stored: LikedVideoInfo): LikedVideoInfo =
        copy(
            channelId = channelId?.takeIf(String::isNotBlank) ?: stored.channelId,
            durationSeconds = durationSeconds.takeIf { it > 0 } ?: stored.durationSeconds,
        )
}
