package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.music.PlaylistRepository
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced Music Recommendation Algorithm (FlowMusicAlgorithm)
 * 
 * This algorithm generates a personalized "For You" music feed based on:
 * 1. Explicit Signals: Liked songs (Favorites)
 * 2. Implicit Signals: Listening history
 * 3. Discovery: Related tracks from YouTube's algorithm
 * 4. Trending: Global top charts for variety
 */
@Singleton
class MusicRecommendationAlgorithm @Inject constructor(
    private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val likedVideosRepository: LikedVideosRepository,
    private val subscriptionRepository: com.flow.youtube.data.local.SubscriptionRepository,
    private val viewHistory: ViewHistory
) {

    companion object {
        private const val TAG = "MusicRecAlgo"
    }

    /**
     * Generate personalized music recommendations
     */
    suspend fun getRecommendations(limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<MusicTrack>()
        val seenVideoIds = mutableSetOf<String>()

        // 1. Gather User Signals
        // Use LikedVideosRepository for favorites (Unified)
        val favorites = likedVideosRepository.getAllLikedVideos().firstOrNull()?.map { 
            MusicTrack(
                videoId = it.videoId,
                title = it.title,
                artist = it.channelName,
                thumbnailUrl = it.thumbnail,
                duration = 0, // Unknown from LikedVideoInfo
                album = ""
            )
        } ?: emptyList()
        
        val musicHistory = playlistRepository.history.firstOrNull() ?: emptyList()
        val subscriptions = subscriptionRepository.getAllSubscriptions().firstOrNull() ?: emptyList()
        
        // We use a set of "Seeds" to generate recommendations
        val seeds = mutableListOf<MusicTrack>()
        
        // Add recent history (implicit interest)
        seeds.addAll(musicHistory.take(5))
        
        // Add recent favorites (explicit interest)
        seeds.addAll(favorites.take(5))
        
        // Add random subscription as seed
        if (subscriptions.isNotEmpty()) {
            val randomSub = subscriptions.random()
            // We need to fetch a track from this artist to use as seed
            try {
                val artistTracks = YouTubeMusicService.searchMusic(randomSub.channelName + " official", 1)
                if (artistTracks.isNotEmpty()) {
                    seeds.add(artistTracks.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get seed from subscription", e)
            }
        }
        
        // Shuffle seeds to provide variety each time
        val activeSeeds = seeds.shuffled().take(4)
        
        Log.d(TAG, "Generating recommendations using ${activeSeeds.size} seeds")

        // 2. Fetch Related Content (Collaborative Filtering via YouTube)
        activeSeeds.forEach { seed ->
            try {
                val related = YouTubeMusicService.getRelatedMusic(seed.videoId, limit = 10)
                candidates.addAll(related)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get related for ${seed.title}", e)
            }
        }

        // 3. Add Trending/New Releases (Discovery)
        // If we don't have enough seeds (new user), we rely more on trending
        if (candidates.size < limit) {
            try {
                val trending = YouTubeMusicService.fetchTrendingMusic(limit = 20)
                candidates.addAll(trending)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get trending", e)
            }
        }

        // 4. Scoring and Ranking
        val scoredTracks = candidates.map { track ->
            var score = 1.0
            
            // Boost if artist is in favorites
            if (favorites.any { it.artist.equals(track.artist, ignoreCase = true) }) {
                score += 0.5
            }
            
            // Boost if artist is followed
            if (subscriptions.any { it.channelName.equals(track.artist, ignoreCase = true) || it.channelId == track.channelId }) {
                score += 0.8
            }
            
            // Boost if artist is in history
            if (musicHistory.any { it.artist.equals(track.artist, ignoreCase = true) }) {
                score += 0.3
            }
            
            // Penalize if already played recently (Variety)
            if (musicHistory.any { it.videoId == track.videoId }) {
                score -= 2.0 // Strongly discourage repeating recent history in recommendations
            }
            
            // Penalize if already in favorites (We want discovery, not just library)
            if (favorites.any { it.videoId == track.videoId }) {
                score -= 0.5
            }

            track to score
        }

        // 5. Filter and Sort
        val finalRecommendations = scoredTracks
            .sortedByDescending { it.second } // Sort by score
            .map { it.first }
            .filter { track ->
                // Deduplicate
                if (seenVideoIds.contains(track.videoId)) {
                    false
                } else {
                    seenVideoIds.add(track.videoId)
                    true
                }
            }
            .take(limit)

        Log.d(TAG, "Generated ${finalRecommendations.size} recommendations")
        return@withContext finalRecommendations
    }
}
