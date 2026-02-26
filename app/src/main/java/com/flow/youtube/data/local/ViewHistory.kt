package com.flow.youtube.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Legacy DataStore kept only for one-time migration of existing user data.
 * After migration it is left empty (cleared).
 */
private val Context.viewHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "view_history")

/**
 * Watch-history repository backed by Room SQLite.
*/
class ViewHistory private constructor(private val context: Context) {

    private val dao = AppDatabase.getDatabase(context).watchHistoryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "ViewHistory"

        @Volatile
        private var INSTANCE: ViewHistory? = null

        fun getInstance(context: Context): ViewHistory {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ViewHistory(context.applicationContext).also { instance ->
                    INSTANCE = instance
                    instance.scope.launch { instance.migrateFromDataStoreIfNeeded() }
                }
            }
        }

        private fun positionKey(id: String)    = longPreferencesKey("video_${id}_position")
        private fun durationKey(id: String)    = longPreferencesKey("video_${id}_duration")
        private fun timestampKey(id: String)   = longPreferencesKey("video_${id}_timestamp")
        private fun titleKey(id: String)       = stringPreferencesKey("video_${id}_title")
        private fun thumbnailKey(id: String)   = stringPreferencesKey("video_${id}_thumbnail")
        private fun channelNameKey(id: String) = stringPreferencesKey("video_${id}_channel_name")
        private fun channelIdKey(id: String)   = stringPreferencesKey("video_${id}_channel_id")
        private fun isMusicKey(id: String)     = booleanPreferencesKey("video_${id}_is_music")
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Save / update playback position (called during real playback).
     * REPLACE strategy so progress is always current.
     */
    suspend fun savePlaybackPosition(
        videoId: String,
        position: Long,
        duration: Long,
        title: String = "",
        thumbnailUrl: String = "",
        channelName: String = "",
        channelId: String = "",
        isMusic: Boolean = false
    ) {
        val thumbnail = thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
        dao.upsert(
            WatchHistoryEntity(
                videoId      = videoId,
                position     = position,
                duration     = duration,
                timestamp    = System.currentTimeMillis(),
                title        = title,
                thumbnailUrl = thumbnail,
                channelName  = channelName,
                channelId    = channelId,
                isMusic      = isMusic
            )
        )
    }

    /**
     * Bulk-insert history entries from an import.
     * Uses IGNORE conflict strategy so that real playback progress already in
     * the DB is never overwritten by imported stubs.
     */
    suspend fun bulkSaveHistoryEntries(entries: List<VideoHistoryEntry>) {
        if (entries.isEmpty()) return
        val entities = entries.map { entry ->
            WatchHistoryEntity(
                videoId      = entry.videoId,
                position     = entry.position,
                duration     = entry.duration,
                timestamp    = entry.timestamp,
                title        = entry.title,
                thumbnailUrl = entry.thumbnailUrl.ifEmpty {
                    "https://i.ytimg.com/vi/${entry.videoId}/hqdefault.jpg"
                },
                channelName  = entry.channelName,
                channelId    = entry.channelId,
                isMusic      = entry.isMusic
            )
        }
        dao.insertAll(entities)
    }

    suspend fun clearVideoHistory(videoId: String) {
        dao.deleteEntry(videoId)
    }

    suspend fun clearAllHistory() {
        dao.clearAll()
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    fun getPlaybackPosition(videoId: String): Flow<Long> =
        dao.getEntry(videoId).map { it?.position ?: 0L }

    fun getVideoHistory(videoId: String): Flow<VideoHistoryEntry?> =
        dao.getEntry(videoId).map { it?.toDomain() }

    /** All history, newest first. */
    fun getAllHistory(): Flow<List<VideoHistoryEntry>> =
        dao.getAllHistory().map { list -> list.map { it.toDomain() } }

    /** Video (non-music) history, newest first. */
    fun getVideoHistoryFlow(): Flow<List<VideoHistoryEntry>> =
        dao.getVideoHistory().map { list -> list.map { it.toDomain() } }

    /** Music history, newest first. */
    fun getMusicHistoryFlow(): Flow<List<VideoHistoryEntry>> =
        dao.getMusicHistory().map { list -> list.map { it.toDomain() } }

    /** Efficient count without loading all rows — use this instead of list.size. */
    fun getVideoCount(): Flow<Int> = dao.getVideoCount()


    /**
     * On the first launch after the 10→11 DB migration the Room table is empty
     * but the old DataStore may contain thousands of entries.  Read them all
     * in batches and insert into Room, then clear the DataStore.
     */
    private suspend fun migrateFromDataStoreIfNeeded() {
        try {
            val roomCount = dao.getCount().first()
            if (roomCount > 0) return

            val prefs = context.viewHistoryDataStore.data.first()
            val videoIds = mutableSetOf<String>()
            prefs.asMap().keys.forEach { key ->
                val name = key.name
                if (name.startsWith("video_") && name.endsWith("_position")) {
                    videoIds.add(name.removePrefix("video_").removeSuffix("_position"))
                }
            }
            if (videoIds.isEmpty()) return

            Log.i(TAG, "Migrating ${videoIds.size} watch-history entries from DataStore → Room")

            val BATCH = 500
            val batch = mutableListOf<WatchHistoryEntity>()

            for (videoId in videoIds) {
                val position = prefs[positionKey(videoId)]    ?: continue
                val duration = prefs[durationKey(videoId)]    ?: 0L
                val ts       = prefs[timestampKey(videoId)]   ?: 0L
                val title    = prefs[titleKey(videoId)]       ?: ""
                val thumb    = prefs[thumbnailKey(videoId)]
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val chName   = prefs[channelNameKey(videoId)] ?: ""
                val chId     = prefs[channelIdKey(videoId)]   ?: ""
                val isMusic  = prefs[isMusicKey(videoId)]     ?: false

                batch += WatchHistoryEntity(videoId, position, duration, ts, title, thumb, chName, chId, isMusic)

                if (batch.size >= BATCH) {
                    dao.insertAll(batch)
                    batch.clear()
                    yield()
                }
            }
            if (batch.isNotEmpty()) dao.insertAll(batch)

            context.viewHistoryDataStore.edit { it.clear() }
            Log.i(TAG, "DataStore → Room migration complete (${videoIds.size} entries)")
        } catch (e: Exception) {
            Log.e(TAG, "Migration from DataStore failed", e)
        }
    }
}

data class VideoHistoryEntry(
    val videoId: String,
    val position: Long,
    val duration: Long,   
    val timestamp: Long,    
    val title: String,
    val thumbnailUrl: String,
    val channelName: String = "",
    val channelId: String = "",
    val isMusic: Boolean = false
) {
    val progressPercentage: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100f else 0f
}
