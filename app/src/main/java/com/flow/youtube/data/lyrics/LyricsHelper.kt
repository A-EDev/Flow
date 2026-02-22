// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

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
        duration: Int
    ): Pair<List<LyricsEntry>, String>? {
        cache[videoId]?.let { cached ->
            Log.d(TAG, "Returning cached lyrics for $videoId")
            return cached to "Cache"
        }

        val wordSyncResult = fetchWordSyncParallel(videoId, title, artist, duration)
        if (wordSyncResult != null) {
            cache[videoId] = wordSyncResult.first
            Log.d(TAG, "Word-sync lyrics from ${wordSyncResult.second}")
            return wordSyncResult
        }

        val fallbackProviders = listOf(lrcLibProvider, youTubeProvider)
        for (provider in fallbackProviders) {
            try {
                Log.d(TAG, "Fallback: trying ${provider.name}")
                val result = withTimeoutOrNull(FALLBACK_TIMEOUT_MS) {
                    provider.getLyrics(videoId, title, artist, duration)
                } ?: continue

                if (result.isSuccess) {
                    val entries = result.getOrNull()
                    if (!entries.isNullOrEmpty()) {
                        cache[videoId] = entries
                        Log.d(TAG, "Line-sync lyrics from ${provider.name} (${entries.size} lines)")
                        return entries to provider.name
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
        duration: Int
    ): Pair<List<LyricsEntry>, String>? = coroutineScope {
        val providers = buildList {
            if (preferredProvider == PreferredLyricsProvider.SIMPMUSIC) {
                add(simpMusicProvider)
                add(betterLyricsProvider)
            } else {
                add(betterLyricsProvider)
                add(simpMusicProvider)
            }
        }

        data class ProviderResult(val provider: LyricsProvider, val entries: List<LyricsEntry>?)

        val jobs = providers.map { provider ->
            async {
                try {
                    Log.d(TAG, "Parallel fetch: ${provider.name}")
                    val result = withTimeoutOrNull(WORD_SYNC_TIMEOUT_MS) {
                        provider.getLyrics(videoId, title, artist, duration)
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
            return@coroutineScope bestLineLevelResult.entries!! to bestLineLevelResult.provider.name
        }

        null
    }

    private fun hasWordSync(entries: List<LyricsEntry>): Boolean {
        val synced = entries.count { it.words != null && it.words.isNotEmpty() }
        return synced.toFloat() / entries.size.coerceAtLeast(1) > 0.1f
    }

    fun clearCache(videoId: String? = null) {
        if (videoId != null) cache.remove(videoId) else cache.clear()
    }
}
