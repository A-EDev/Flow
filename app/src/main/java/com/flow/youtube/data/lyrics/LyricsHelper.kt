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
    private val preferredProvider: PreferredLyricsProvider = PreferredLyricsProvider.LRCLIB
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
        var preferredProviderFound = false

        for (provider in orderedProviders) {
            try {
                Log.d(TAG, "Trying provider: ${provider.name}")
                val result = provider.getLyrics(videoId, title, artist, duration)

                if (result.isSuccess) {
                    val entries = result.getOrNull()
                    if (!entries.isNullOrEmpty()) {
                        val hasWordSync = entries.any { it.words != null && it.words.isNotEmpty() }
                        Log.d(TAG, "Got lyrics from ${provider.name} (${entries.size} lines, word-sync=$hasWordSync)")

                        val isPreferred = provider == getProviderInstance(preferredProvider)
                        if (isPreferred && hasWordSync) {
                            cache[videoId] = entries
                            return entries to provider.name
                        }

                        if (hasWordSync) {
                            if (bestResult == null || (bestResult?.first?.any { it.words != null } != true)) {
                                bestResult = entries to provider.name
                            }
                        } else {
                            if (bestResult == null || (isPreferred && bestResult?.first?.any { it.words != null } != true)) {
                                bestResult = entries to provider.name
                            }
                        }
                        
                        if (isPreferred) preferredProviderFound = true
                    }
                } else {
                    Log.d(TAG, "Provider ${provider.name} failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Provider ${provider.name} threw exception", e)
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
     * Orders providers based on user preference.
     * The preferred provider goes first, then the remaining in a sensible order.
     * YouTube is always last since it only provides plain text.
     */
    private fun getOrderedProviders(): List<LyricsProvider> {
        val providers = mutableListOf<LyricsProvider>()

        when (preferredProvider) {
            PreferredLyricsProvider.LRCLIB -> providers.add(lrcLibProvider)
            PreferredLyricsProvider.BETTER_LYRICS -> providers.add(betterLyricsProvider)
            PreferredLyricsProvider.SIMPMUSIC -> providers.add(simpMusicProvider)
        }

        val allSyncProviders = listOf(lrcLibProvider, betterLyricsProvider, simpMusicProvider)
        allSyncProviders.forEach { provider ->
            if (provider !in providers) {
                providers.add(provider)
            }
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
