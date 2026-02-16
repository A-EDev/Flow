// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

import android.util.Log

/**
 * Orchestrates multiple lyrics providers, ordering them by user preference.
 * Tries each provider in order and returns the first successful result.
 * Caches structured LyricsEntry results per media item.
 */
class LyricsHelper(
    val preferredProvider: PreferredLyricsProvider = PreferredLyricsProvider.LRCLIB
) {
    companion object {
        private const val TAG = "LyricsHelper"
    }

    private val lrcLibProvider = LrcLibLyricsProvider()
    private val youTubeProvider = YouTubeLyricsProvider()
    private val betterLyricsProvider = BetterLyricsProvider()
    private val simpMusicProvider = SimpMusicLyricsProvider()

    private val cache = mutableMapOf<String, List<LyricsEntry>>()

    /**
     * Fetch lyrics using the preferred provider order.
     * Falls back through all providers until one succeeds.
     *
     * @return Pair of (structured lyrics entries with word timestamps, provider name)
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

        val orderedProviders = getOrderedProviders()
        var bestResult: Pair<List<LyricsEntry>, String>? = null
        var bestHasWordSync = false

        for (provider in orderedProviders) {
            try {
                Log.d(TAG, "Trying provider: ${provider.name}")
                val result = provider.getLyrics(videoId, title, artist, duration)

                if (result.isSuccess) {
                    val entries = result.getOrNull()
                    if (!entries.isNullOrEmpty()) {
                        val syncedLinesCount = entries.count { it.words != null && it.words.isNotEmpty() }
                        val totalLines = entries.size
                        val wordSyncRatio = if (totalLines > 0) syncedLinesCount.toFloat() / totalLines else 0f
                        
                        val hasWordSync = wordSyncRatio > 0.1f
                        
                        Log.d(TAG, "Got lyrics from ${provider.name} (${entries.size} lines, synced=$syncedLinesCount, ratio=$wordSyncRatio, hasWordSync=$hasWordSync)")

                        if (hasWordSync) {
                            if (!bestHasWordSync || provider == getProviderInstance(preferredProvider)) {
                                bestResult = entries to provider.name
                                bestHasWordSync = true
                                if (provider == getProviderInstance(preferredProvider)) {
                                    cache[videoId] = entries
                                    return entries to provider.name
                                }
                            }
                        } else if (bestResult == null) {
                            bestResult = entries to provider.name
                        }
                    }
                } else {
                    Log.w(TAG, "Provider ${provider.name} failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Provider ${provider.name} threw exception: ${e.message}")
            }
        }

        if (bestResult != null && !bestHasWordSync) {
            Log.d(TAG, "Only line-level lyrics found. Retrying word-sync providers once...")
            for (provider in listOf(betterLyricsProvider, simpMusicProvider)) {
                try {
                    kotlinx.coroutines.delay(500) 
                    val result = provider.getLyrics(videoId, title, artist, duration)
                    if (result.isSuccess) {
                        val entries = result.getOrNull()
                        if (!entries.isNullOrEmpty()) {
                            val syncedCount = entries.count { it.words != null && it.words.isNotEmpty() }
                            if (syncedCount > 0) {
                                Log.d(TAG, "Retry: Got word-sync lyrics from ${provider.name} ($syncedCount synced lines)")
                                bestResult = entries to provider.name
                                bestHasWordSync = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Retry: Provider ${provider.name} failed again: ${e.message}")
                }
            }
        }

        return bestResult?.also { 
            cache[videoId] = it.first
            Log.d(TAG, "Returning best lyrics from ${it.second}") 
        }
    }

    private fun getProviderInstance(type: PreferredLyricsProvider): LyricsProvider? {
        return when(type) {
            PreferredLyricsProvider.LRCLIB -> lrcLibProvider
            PreferredLyricsProvider.BETTER_LYRICS -> betterLyricsProvider
            PreferredLyricsProvider.SIMPMUSIC -> simpMusicProvider
            else -> null
        }
    }

    /**
     * Orders providers to prioritize word-sync-capable providers.
     * BetterLyrics and SimpMusic support word-by-word timestamps, so they
     * are always tried before LRCLIB (line-level only). The user's preferred
     * provider controls which word-sync provider is tried first.
     * YouTube is always last since it only provides plain text.
     */
    private fun getOrderedProviders(): List<LyricsProvider> {
        val providers = mutableListOf<LyricsProvider>()

        val wordSyncProviders = listOf(betterLyricsProvider, simpMusicProvider)
        val preferredInstance = getProviderInstance(preferredProvider)
        
        if (preferredInstance != null && preferredInstance in wordSyncProviders) {
            providers.add(preferredInstance)
        }
        
        wordSyncProviders.forEach { provider ->
            if (provider !in providers) providers.add(provider)
        }
        
        if (lrcLibProvider !in providers) {
            providers.add(lrcLibProvider)
        }

        providers.add(youTubeProvider)

        return providers
    }

    /**
     * Clear the lyrics cache for a specific video or all videos.
     */
    fun clearCache(videoId: String? = null) {
        if (videoId != null) {
            cache.remove(videoId)
        } else {
            cache.clear()
        }
    }
}
