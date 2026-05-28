//==================================================================================================
//This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
//==================================================================================================

package io.github.aedev.flow.data.lyrics

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class LyricsHelper(
    private val context: Context
) {
    companion object {
        private const val TAG = "LyricsHelper"
        private const val PER_PROVIDER_TIMEOUT_MS = 8_000L
        private const val MAX_TOTAL_TIMEOUT_MS = 25_000L
        private const val PROVIDER_COOLDOWN_MS = 10 * 60 * 1000L
    }

    private val registry = LyricsProviderRegistry.default()
    private val playerPreferences = PlayerPreferences(context)
    private val cache = mutableMapOf<String, List<LyricsEntry>>()

    private val providerCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun forceRefresh(videoId: String, ctx: Context? = null) {
        cache.remove(videoId)
        try {
            LyricsCacheManager.evictLyrics(ctx ?: context, videoId)
        } catch (e: Exception) {
            Log.w(TAG, "Disk cache evict failed: ${e.message}")
        }
        providerCooldowns.clear()
        Log.d(TAG, "Force refresh requested for $videoId — caches and cooldowns cleared")
    }

    suspend fun getLyrics(
        videoId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        ctx: Context? = null
    ): Pair<List<LyricsEntry>, String>? {
        val targetContext = ctx ?: context

        cache[videoId]?.let { cached ->
            val normalized = normalizeEntries(cached)
            if (normalized.none { !it.words.isNullOrEmpty() } || hasWordSync(normalized)) {
                Log.d(TAG, "Returning in-memory cached lyrics for $videoId")
                return normalized to "MemoryCache"
            }
            Log.d(TAG, "Ignoring weak in-memory word-sync cache for $videoId")
            cache.remove(videoId)
        }

        var cachedLineFallback: Pair<List<LyricsEntry>, String>? = null
        val diskCached = LyricsCacheManager.getLyrics(targetContext, videoId)
        val normalizedDiskCached = diskCached?.let { normalizeEntries(it.sorted()) }
        if (!normalizedDiskCached.isNullOrEmpty() && hasWordSync(normalizedDiskCached)
            && hasReasonableTimestamps(normalizedDiskCached, duration)) {
            cache[videoId] = normalizedDiskCached
            return normalizedDiskCached to "DiskCache"
        } else if (!normalizedDiskCached.isNullOrEmpty() && hasReasonableTimestamps(normalizedDiskCached, duration)) {
            cachedLineFallback = stripWordTimings(normalizedDiskCached) to "DiskCache"
            Log.d(TAG, "Disk cache has line/plain lyrics only; trying to upgrade to word-sync")
        } else if (!diskCached.isNullOrEmpty()) {
            Log.d(TAG, "Disk cache has invalid timestamps for $videoId, discarding")
            LyricsCacheManager.evictLyrics(targetContext, videoId)
        }

        val cleanedTitle = LyricsUtils.cleanTitle(title)
        val cleanedArtist = LyricsUtils.cleanArtist(artist)
        Log.d(TAG, "Cache miss. Querying providers: cleanedTitle=\"$cleanedTitle\", cleanedArtist=\"$cleanedArtist\"")

        val orderString = playerPreferences.lyricsProviderOrder.first()
        val enabledStates = playerPreferences.allLyricsProviderEnabledStates().first()
        val orderedProviders = registry.getOrderedProviders(orderString)
            .filter { enabledStates[it.name] != false }

        Log.d(TAG, "Enabled providers in order: ${orderedProviders.joinToString { it.name }}")

        val now = System.currentTimeMillis()

        var unsyncedFallback: Pair<List<LyricsEntry>, String>? = null

        val syncedResult = withTimeoutOrNull(MAX_TOTAL_TIMEOUT_MS) {
            for (provider in orderedProviders) {
                val cooldownUntil = providerCooldowns[provider.name]
                if (cooldownUntil != null && cooldownUntil > now) {
                    Log.d(TAG, "Skipping ${provider.name} (auth cooldown for ${(cooldownUntil - now) / 1000}s)")
                    continue
                }

                Log.d(TAG, "Trying provider: ${provider.name}")
                val providerResult = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(videoId, cleanedTitle, cleanedArtist, duration, album)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name} threw: ${e.message}")
                    null
                }

                if (providerResult != null && providerResult.isSuccess) {
                    var entries = providerResult.getOrNull()
                    if (!entries.isNullOrEmpty()) {
                        entries = LyricsUtils.filterCreditLines(normalizeEntries(entries.sorted()))
                        if (entries.isNotEmpty() && hasReasonableTimestamps(entries, duration)) {
                            if (entriesAreSynced(entries)) {
                                Log.d(TAG, "Got ${entries.size} SYNCED lines from ${provider.name} — using these")
                                return@withTimeoutOrNull entries to provider.name
                            } else if (unsyncedFallback == null) {
                                Log.d(TAG, "${provider.name} returned ${entries.size} UNSYNCED lines — saving as fallback, continuing for synced")
                                unsyncedFallback = entries to provider.name
                            } else {
                                Log.d(TAG, "${provider.name} returned unsynced; already have a fallback, continuing for synced")
                            }
                        } else if (entries.isNotEmpty()) {
                            Log.w(TAG, "${provider.name} returned lyrics with unreasonable timestamps, skipping")
                        }
                    }
                } else {
                    val errorMsg = providerResult?.exceptionOrNull()?.message ?: "timeout or exception"
                    if (isAuthFailure(errorMsg)) {
                        providerCooldowns[provider.name] = now + PROVIDER_COOLDOWN_MS
                        Log.w(TAG, "${provider.name} auth failure ($errorMsg) — cooling down for 10 min")
                    } else {
                        Log.w(TAG, "${provider.name} failed: $errorMsg")
                    }
                }
            }
            null
        }

        if (syncedResult != null) {
            cache[videoId] = syncedResult.first
            try {
                LyricsCacheManager.saveLyrics(targetContext, videoId, syncedResult.first)
            } catch (e: Exception) {
                Log.w(TAG, "Disk cache save failed: ${e.message}")
            }
            return syncedResult
        }

        unsyncedFallback?.let { fallback ->
            Log.d(TAG, "No synced lyrics across providers — falling back to unsynced from ${fallback.second}")
            cache[videoId] = fallback.first
            try {
                LyricsCacheManager.saveLyrics(targetContext, videoId, fallback.first)
            } catch (e: Exception) {
                Log.w(TAG, "Disk cache save failed: ${e.message}")
            }
            return fallback
        }

        cachedLineFallback?.let {
            cache[videoId] = it.first
            return it
        }

        Log.w(TAG, "No lyrics found for $videoId")
        return null
    }

    private fun hasWordSync(entries: List<LyricsEntry>): Boolean {
        val wordLines = entries.filter { !it.words.isNullOrEmpty() }
        if (wordLines.isEmpty()) return false

        val lineCount = entries.size.coerceAtLeast(1)
        val ratio = wordLines.size.toFloat() / lineCount
        if (lineCount > 5 && wordLines.size < 3) return false
        if (lineCount > 10 && ratio < 0.25f) return false

        val validTimingLines = wordLines.count { entry ->
            val words = entry.words.orEmpty().sortedBy { it.startTime }
            words.isNotEmpty() &&
                words.last().endTime > words.first().startTime &&
                words.zipWithNext().all { (current, next) ->
                    current.endTime >= current.startTime && next.startTime >= current.startTime
                }
        }
        if (validTimingLines < minOf(2, wordLines.size)) return false

        val firstWordMs = wordLines.minOf { it.words!!.first().startTime }
        val lastWordMs = wordLines.maxOf { it.words!!.last().endTime }
        if (lineCount > 5 && lastWordMs - firstWordMs < 10_000L) return false

        return true
    }

    private fun hasReasonableTimestamps(entries: List<LyricsEntry>, durationSec: Int): Boolean {
        val timed = entries.filter { it.time > 0 }
        if (timed.size < 2) return true
        val firstMs = timed.first().time
        val lastMs = timed.last().time
        if (lastMs - firstMs < 10_000L) return false
        if (durationSec > 0 && firstMs > durationSec * 1000L + 10_000L) return false
        return true
    }

    private fun isAuthFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains(" 401") || message.contains(" 403") ||
            message.contains("Unauthorized", ignoreCase = true) ||
            message.contains("Forbidden", ignoreCase = true)
    }

    fun entriesAreSynced(entries: List<LyricsEntry>): Boolean {
        if (entries.size < 2) return false
        val main = entries.filter { !it.isBackground }
        val list = if (main.size >= 2) main else entries
        val times = list.map { it.time }.distinct()
        if (times.size < 2) return false
        val firstPositive = times.firstOrNull { it > 0L } ?: return false
        val maxTime = list.maxOf { it.time }
        if (maxTime - firstPositive < 5_000L) return false
        val hasWordTimings = entries.any { !it.words.isNullOrEmpty() }
        if (hasWordTimings) return true
        val distinctTimedLines = list.count { it.time > 0L }
        return distinctTimedLines >= (list.size * 0.5).toInt().coerceAtLeast(2)
    }

    private fun stripWordTimings(entries: List<LyricsEntry>): List<LyricsEntry> {
        return entries.map { entry -> entry.copy(words = null) }
    }

    private fun normalizeEntries(entries: List<LyricsEntry>): List<LyricsEntry> {
        return entries.map { entry ->
            entry.copy(
                text = LyricsUtils.decodeHtmlEntities(entry.text),
                words = entry.words?.map { word ->
                    word.copy(text = LyricsUtils.decodeHtmlEntities(word.text))
                }
            )
        }
    }

    fun clearCache(videoId: String? = null) {
        if (videoId != null) cache.remove(videoId) else cache.clear()
    }
}
