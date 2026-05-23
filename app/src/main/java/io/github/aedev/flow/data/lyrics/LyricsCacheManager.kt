package io.github.aedev.flow.data.lyrics

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object LyricsCacheManager {
    private const val TAG = "LyricsCacheManager"
    private const val LYRICS_DIR = "lyrics"
    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun getCacheFile(context: Context, videoId: String): File {
        val dir = File(context.filesDir, LYRICS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$videoId.json")
    }

    suspend fun saveLyrics(context: Context, videoId: String, entries: List<LyricsEntry>) {
        if (videoId.isBlank() || entries.isEmpty()) return
        
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(context, videoId)
                    val jsonString = json.encodeToString(entries)
                    file.writeText(jsonString, Charsets.UTF_8)
                    Log.d(TAG, "Successfully cached lyrics to disk for videoId: $videoId")
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cache lyrics for $videoId to disk: ${e.message}", e)
                }
            }
        }
    }

    suspend fun getLyrics(context: Context, videoId: String): List<LyricsEntry>? {
        if (videoId.isBlank()) return null
        
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(context, videoId)
                    if (file.exists() && file.length() > 0L) {
                        val jsonString = file.readText(Charsets.UTF_8)
                        val entries = json.decodeFromString<List<LyricsEntry>>(jsonString)
                        Log.d(TAG, "Instant cache hit: loaded lyrics from disk for videoId: $videoId")
                        entries
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read cached lyrics for $videoId: ${e.message}", e)
                    null
                }
            }
        }
    }

    suspend fun evictLyrics(context: Context, videoId: String) {
        if (videoId.isBlank()) return
        
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(context, videoId)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Evicted lyrics cache from disk for videoId: $videoId")
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to evict lyrics cache for $videoId: ${e.message}", e)
                }
            }
        }
    }

    suspend fun clearAllCache(context: Context) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val dir = File(context.filesDir, LYRICS_DIR)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                        Log.d(TAG, "Cleared all local disk lyrics caches successfully")
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear all disk lyrics caches: ${e.message}", e)
                }
            }
        }
    }
}
