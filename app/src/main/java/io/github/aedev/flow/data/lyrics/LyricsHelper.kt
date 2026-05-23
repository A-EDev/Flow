// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package io.github.aedev.flow.data.lyrics

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates multiple lyrics providers, ordering them by user preference.
 *
 * Strategy:
 *  1. Run BetterLyrics and SimpMusic **in parallel** (both support word-level sync).
 *     Return whichever comes back first with a valid word-sync result.
 *  2. If neither returns word-sync within their timeout, fall back to LrcLib / YouTube
 *     sequentially for at least line-level synced lyrics.
 *  3. Cache successful results per videoId.
 */
class LyricsHelper(
    val preferredProvider: PreferredLyricsProvider = PreferredLyricsProvider.LRCLIB
) {
    companion object {
        private const val TAG = "LyricsHelper"
        private const val WORD_SYNC_TIMEOUT_MS = 8_000L
        private const val FALLBACK_TIMEOUT_MS = 7_000L
    }

    private val lrcLibProvider = LrcLibLyricsProvider()
    private val youTubeProvider = YouTubeLyricsProvider()
    private val betterLyricsProvider = BetterLyricsProvider()
    private val simpMusicProvider = SimpMusicLyricsProvider()
    private val lyricsPlusProvider = LyricsPlusProvider()

    private val cache = mutableMapOf<String, List<LyricsEntry>>()

    /**
     * Fetch lyrics using a two-phase strategy:
     *
     * Phase 1 — Parallel word-sync: run BetterLyrics + SimpMusic simultaneously.
     *   - Accept the first result that contains word-level timestamps.
     *   - If both return line-level results, keep the best one (most lines) as a fallback.
     *   - Both providers have [WORD_SYNC_TIMEOUT_MS] to respond.
     *
     * Phase 2 — Sequential fallback: only reached if Phase 1 yielded nothing.
     *   - Try LrcLib first (usually fast, good line-sync coverage).
     *   - Then YouTube as a last resort.
     *
     * @return Pair of (entries, providerName), or null if nothing was found.
     */
    suspend fun getLyrics(
        videoId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        context: android.content.Context? = null
    ): Pair<List<LyricsEntry>, String>? {
        cache[videoId]?.let { cached ->
            val normalized = normalizeEntries(cached)
            if (normalized.none { !it.words.isNullOrEmpty() } || hasWordSync(normalized)) {
                Log.d(TAG, "Returning in-memory cached lyrics for $videoId")
                return normalized to "MemoryCache"
            }
            Log.d(TAG, "Ignoring weak in-memory word-sync cache for $videoId")
            cache.remove(videoId)
        }

        val targetContext = context ?: io.github.aedev.flow.FlowApplication.appContext
        var cachedLineFallback: Pair<List<LyricsEntry>, String>? = null
        val diskCached = LyricsCacheManager.getLyrics(targetContext, videoId)
        val normalizedDiskCached = diskCached?.let { normalizeEntries(it) }
        if (!normalizedDiskCached.isNullOrEmpty() && hasWordSync(normalizedDiskCached)) {
            cache[videoId] = normalizedDiskCached
            return normalizedDiskCached to "DiskCache"
        } else if (!diskCached.isNullOrEmpty()) {
            cachedLineFallback = stripWordTimings(normalizedDiskCached.orEmpty()) to "DiskCache"
            Log.d(TAG, "Disk cache has line/plain lyrics only; trying to upgrade to word-sync")
        }

        val cleanedTitle = LyricsUtils.cleanTitle(title)
        val cleanedArtist = LyricsUtils.cleanArtist(artist)
        Log.d(TAG, "Cache miss. Querying network providers: cleanedTitle=\"$cleanedTitle\", cleanedArtist=\"$cleanedArtist\"")

        val wordSyncResult = fetchWordSyncParallel(videoId, cleanedTitle, cleanedArtist, duration, album)
        if (wordSyncResult != null) {
            val normalizedEntries = normalizeEntries(wordSyncResult.first)
            cache[videoId] = normalizedEntries
            Log.d(TAG, "Word-sync lyrics fetched from ${wordSyncResult.second}")
            LyricsCacheManager.saveLyrics(targetContext, videoId, normalizedEntries)
            return normalizedEntries to wordSyncResult.second
        }

        cachedLineFallback?.let {
            cache[videoId] = it.first
            return it
        }

        val fallbackProviders = listOf(lrcLibProvider, youTubeProvider)
        for (provider in fallbackProviders) {
            try {
                Log.d(TAG, "Fallback: trying ${provider.name}")
                val result = withTimeoutOrNull(FALLBACK_TIMEOUT_MS) {
                    provider.getLyrics(videoId, cleanedTitle, cleanedArtist, duration, album)
                } ?: continue

                if (result.isSuccess) {
                    val entries = result.getOrNull()
                    if (!entries.isNullOrEmpty()) {
                        val normalizedEntries = normalizeEntries(entries)
                        cache[videoId] = normalizedEntries
                        Log.d(TAG, "Line-sync lyrics fetched from ${provider.name} (${entries.size} lines)")
                        LyricsCacheManager.saveLyrics(targetContext, videoId, normalizedEntries)
                        return normalizedEntries to provider.name
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback provider ${provider.name} threw: ${e.message}")
            }
        }

        Log.w(TAG, "No lyrics found for $videoId")
        return null
    }

    /**
     * Run BetterLyrics and SimpMusic simultaneously.
     * Returns the first result with word-sync; if neither has word-sync, returns
     * the better of the two line-sync results (or null if both fail).
     */
    private suspend fun fetchWordSyncParallel(
        videoId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null
    ): Pair<List<LyricsEntry>, String>? = coroutineScope {
        val providers = orderedWordSyncProviders()

        data class ProviderResult(val provider: LyricsProvider, val entries: List<LyricsEntry>?)

        val jobs = providers.map { provider ->
            async {
                try {
                    Log.d(TAG, "Parallel fetch: ${provider.name}")
                    val result = withTimeoutOrNull(WORD_SYNC_TIMEOUT_MS) {
                        provider.getLyrics(videoId, title, artist, duration, album)
                    }
                    val entries = result?.getOrNull()
                    ProviderResult(provider, entries?.takeIf { it.isNotEmpty() })
                } catch (e: Exception) {
                    Log.e(TAG, "Parallel provider ${provider.name} threw: ${e.message}")
                    ProviderResult(provider, null)
                }
            }
        }

        val results = jobs.awaitAll()

        val preferred = results.firstOrNull { r ->
            r.entries != null && hasWordSync(r.entries)
        }
        if (preferred != null) {
            Log.d(TAG, "Word-sync from ${preferred.provider.name}")
            return@coroutineScope preferred.entries!! to preferred.provider.name
        }

        val anyWordSync = results.firstOrNull { r ->
            r.entries != null && hasWordSync(r.entries)
        }
        if (anyWordSync != null) {
            Log.d(TAG, "Word-sync from ${anyWordSync.provider.name}")
            return@coroutineScope anyWordSync.entries!! to anyWordSync.provider.name
        }

        val bestLineLevelResult = results
            .filter { it.entries != null }
            .maxByOrNull { it.entries!!.size }
        if (bestLineLevelResult != null) {
            Log.d(TAG, "Line-level fallback from ${bestLineLevelResult.provider.name}")
            return@coroutineScope stripWordTimings(bestLineLevelResult.entries!!) to bestLineLevelResult.provider.name
        }

        null
    }

    private fun orderedWordSyncProviders(): List<LyricsProvider> {
        return buildList {
            when (preferredProvider) {
                PreferredLyricsProvider.SIMPMUSIC -> {
                    add(simpMusicProvider)
                    add(lyricsPlusProvider)
                    add(betterLyricsProvider)
                }
                PreferredLyricsProvider.BETTER_LYRICS -> {
                    add(betterLyricsProvider)
                    add(lyricsPlusProvider)
                    add(simpMusicProvider)
                }
                PreferredLyricsProvider.LYRICS_PLUS -> {
                    add(lyricsPlusProvider)
                    add(simpMusicProvider)
                    add(betterLyricsProvider)
                }
                PreferredLyricsProvider.LRCLIB -> {
                    add(simpMusicProvider)
                    add(lyricsPlusProvider)
                    add(betterLyricsProvider)
                }
            }
        }.distinctBy { it.name }
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
