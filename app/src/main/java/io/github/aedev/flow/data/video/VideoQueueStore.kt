package io.github.aedev.flow.data.video

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.videoQueueStore by safePreferencesDataStore(name = "video_queue")
private val QueueKey = stringPreferencesKey("queue_json")
private val QueueJson = Json { ignoreUnknownKeys = true }

/** A video queue as it was left: its videos, the one playing and the title it was started under. */
data class SavedVideoQueue(
    val videos: List<Video>,
    val index: Int,
    val title: String?,
)

@Serializable
internal data class StoredQueue(
    val items: List<StoredQueueItem>,
    val index: Int,
    val title: String? = null,
)

@Serializable
internal data class StoredQueueItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val isMusic: Boolean = false,
    val isShort: Boolean = false,
)

/** Keeps the video queue across process death, the way the music player keeps its own. */
@Singleton
class VideoQueueStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun save(
            videos: List<Video>,
            index: Int,
            title: String?,
        ) {
            context.videoQueueStore.edit { prefs ->
                if (videos.size < 2 || index !in videos.indices) {
                    prefs.remove(QueueKey)
                } else {
                    prefs[QueueKey] =
                        QueueJson.encodeToString(StoredQueue.serializer(), StoredQueue(videos.map { it.toStored() }, index, title))
                }
            }
        }

        suspend fun load(): SavedVideoQueue? {
            val raw = context.videoQueueStore.data.first()[QueueKey] ?: return null
            val stored = runCatching { QueueJson.decodeFromString(StoredQueue.serializer(), raw) }.getOrNull() ?: return null
            return SavedVideoQueue(stored.items.map { it.toVideo() }, stored.index, stored.title).takeIf { it.index in it.videos.indices }
        }
    }

private fun Video.toStored() = StoredQueueItem(id, title, channelName, channelId, thumbnailUrl, duration, isMusic, isShort)

private fun StoredQueueItem.toVideo() =
    Video(
        id = id,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        isMusic = isMusic,
        isShort = isShort,
    )
