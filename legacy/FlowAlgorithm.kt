package com.flow.youtube.data.recommendation

import android.util.Log
import com.flow.youtube.data.model.Video
import kotlin.random.Random

/**
 * The "Flow" Recommendation Algorithm (Local FYP)
 * 
 * Scores and ranks videos based on:
 * - Subscription source (+50 points)
 * - Related to recently watched videos (within 24h: +40 points, 1-7 days: +25 points, older: +10 points)
 * - Related to frequent search terms (+30 points)
 * - Channel frequency penalty (prevents spam from single channel)
 * - Freshness bonus (newer videos score higher)
 */
object FlowAlgorithm {
    
    private const val TAG = "FlowAlgorithm"
    
    // Scoring constants
    private const val SUBSCRIPTION_BONUS = 50
    private const val RELATED_24H_BONUS = 40
    private const val RELATED_7D_BONUS = 25
    private const val RELATED_OLDER_BONUS = 10
    private const val SEARCH_INTEREST_BONUS = 30
    private const val FRESHNESS_BONUS_MAX = 20
    private const val CHANNEL_PENALTY_THRESHOLD = 2
    private const val CHANNEL_PENALTY = -20
    
    // Time constants
    private const val HOURS_24_MS = 24 * 60 * 60 * 1000L
    private const val DAYS_7_MS = 7 * 24 * 60 * 60 * 1000L
    
    /**
     * Score and rank videos using the Flow algorithm
     * 
     * @param candidates All candidate videos from various sources
     * @param subscriptionChannelIds Set of channel IDs the user is subscribed to
     * @param recentWatchHistory Map of video IDs to their watch timestamp
     * @param searchInterestVideoIds Set of video IDs related to user's search interests
     * @param currentTime Current timestamp for freshness calculation
     */
    fun scoreAndRank(
        candidates: List<ScoredVideo>,
        subscriptionChannelIds: Set<String>,
        recentWatchHistory: Map<String, Long>, // videoId -> watchTimestamp
        searchInterestVideoIds: Set<String>,
        currentTime: Long = System.currentTimeMillis()
    ): List<ScoredVideo> {
        
        if (candidates.isEmpty()) return emptyList()
        
        Log.d(TAG, "Scoring ${candidates.size} candidates")
        Log.d(TAG, "Subscription channels: ${subscriptionChannelIds.size}")
        Log.d(TAG, "Recent watch history: ${recentWatchHistory.size}")
        Log.d(TAG, "Search interest videos: ${searchInterestVideoIds.size}")
        
        // Track channel counts for penalty
        val channelCounts = mutableMapOf<String, Int>()
        
        // Score each video
        val scoredVideos = candidates.map { candidate ->
            var score = candidate.baseScore
            val reasons = mutableListOf<String>()
            
            // 1. Subscription bonus
            if (subscriptionChannelIds.contains(candidate.video.channelId)) {
                score += SUBSCRIPTION_BONUS
                reasons.add("subscribed")
            }
            
            // 2. Related to watched video bonus (based on source)
            if (candidate.source == VideoSource.RELATED_TO_WATCHED) {
                val watchTime = candidate.relatedToWatchTimestamp
                if (watchTime != null) {
                    val timeSinceWatch = currentTime - watchTime
                    when {
                        timeSinceWatch < HOURS_24_MS -> {
                            score += RELATED_24H_BONUS
                            reasons.add("related_24h")
                        }
                        timeSinceWatch < DAYS_7_MS -> {
                            score += RELATED_7D_BONUS
                            reasons.add("related_7d")
                        }
                        else -> {
                            score += RELATED_OLDER_BONUS
                            reasons.add("related_older")
                        }
                    }
                }
            }
            
            // 3. Search interest bonus
            if (searchInterestVideoIds.contains(candidate.video.id) || 
                candidate.source == VideoSource.SEARCH_INTEREST) {
                score += SEARCH_INTEREST_BONUS
                reasons.add("search_interest")
            }
            
            // 4. Freshness bonus (based on upload date if parseable)
            val freshnessBonus = calculateFreshnessBonus(candidate.video.uploadDate, currentTime)
            score += freshnessBonus
            if (freshnessBonus > 0) reasons.add("fresh")
            
            // 5. Channel frequency penalty
            val channelId = candidate.video.channelId
            val currentCount = channelCounts.getOrDefault(channelId, 0)
            channelCounts[channelId] = currentCount + 1
            
            if (currentCount >= CHANNEL_PENALTY_THRESHOLD) {
                score += CHANNEL_PENALTY
                reasons.add("channel_penalty")
            }
            
            candidate.copy(
                flowScore = score,
                scoreReasons = reasons
            )
        }
        
        // Sort by score (descending), with randomization for same scores
        val sorted = scoredVideos.sortedWith(
            compareByDescending<ScoredVideo> { it.flowScore }
                .thenBy { Random.nextInt() } // Random tiebreaker for variety
        )
        
        Log.d(TAG, "Scoring complete. Top score: ${sorted.firstOrNull()?.flowScore}")
        
        return sorted
    }
    
    /**
     * Merge and deduplicate videos from multiple sources
     */
    fun mergeAndDeduplicate(
        subscriptionVideos: List<Video>,
        relatedVideos: List<RelatedVideoInfo>,
        searchInterestVideos: List<Video>,
        watchedVideoIds: Set<String> // Videos user already watched - exclude from feed
    ): List<ScoredVideo> {
        
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<ScoredVideo>()
        
        // Add subscription videos (highest priority source)
        subscriptionVideos.forEach { video ->
            if (!seenIds.contains(video.id) && !watchedVideoIds.contains(video.id)) {
                seenIds.add(video.id)
                result.add(ScoredVideo(
                    video = video,
                    source = VideoSource.SUBSCRIPTION,
                    baseScore = SUBSCRIPTION_BONUS
                ))
            }
        }
        
        // Add related videos
        relatedVideos.forEach { related ->
            if (!seenIds.contains(related.video.id) && !watchedVideoIds.contains(related.video.id)) {
                seenIds.add(related.video.id)
                result.add(ScoredVideo(
                    video = related.video,
                    source = VideoSource.RELATED_TO_WATCHED,
                    relatedToVideoId = related.sourceVideoId,
                    relatedToWatchTimestamp = related.watchTimestamp
                ))
            }
        }
        
        // Add search interest videos
        searchInterestVideos.forEach { video ->
            if (!seenIds.contains(video.id) && !watchedVideoIds.contains(video.id)) {
                seenIds.add(video.id)
                result.add(ScoredVideo(
                    video = video,
                    source = VideoSource.SEARCH_INTEREST
                ))
            }
        }
        
        Log.d(TAG, "Merged ${result.size} unique videos (excluded ${watchedVideoIds.size} already watched)")
        
        return result
    }
    
    /**
     * Light shuffle to add variety while keeping top items roughly in place
     */
    fun lightShuffle(videos: List<ScoredVideo>, bucketSize: Int = 5): List<ScoredVideo> {
        if (videos.size <= bucketSize) return videos.shuffled()
        
        val result = mutableListOf<ScoredVideo>()
        
        // Process in buckets and shuffle within each bucket
        videos.chunked(bucketSize).forEach { bucket ->
            result.addAll(bucket.shuffled())
        }
        
        return result
    }
    
    /**
     * Calculate freshness bonus based on upload date
     * Newer videos get higher bonus (max 20 points for videos < 1 day old)
     */
    private fun calculateFreshnessBonus(uploadDate: String, currentTime: Long): Int {
        // Try to parse relative dates like "2 hours ago", "3 days ago", etc.
        val lowerDate = uploadDate.lowercase()
        
        return when {
            lowerDate.contains("hour") || lowerDate.contains("minute") -> FRESHNESS_BONUS_MAX
            lowerDate.contains("day") -> {
                val days = extractNumber(lowerDate)
                when {
                    days <= 1 -> 15
                    days <= 3 -> 10
                    days <= 7 -> 5
                    else -> 0
                }
            }
            lowerDate.contains("week") -> {
                val weeks = extractNumber(lowerDate)
                if (weeks <= 1) 3 else 0
            }
            else -> 0
        }
    }
    
    private fun extractNumber(text: String): Int {
        return text.split(" ").firstOrNull { it.toIntOrNull() != null }?.toInt() ?: 1
    }
}

/**
 * Represents a video with its Flow score and metadata
 */
data class ScoredVideo(
    val video: Video,
    val source: VideoSource,
    val baseScore: Int = 0,
    val flowScore: Int = 0,
    val relatedToVideoId: String? = null,
    val relatedToWatchTimestamp: Long? = null,
    val scoreReasons: List<String> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
)

/**
 * Where a video recommendation came from
 */
enum class VideoSource {
    SUBSCRIPTION,      // From subscribed channels
    RELATED_TO_WATCHED, // Related to a video user watched
    SEARCH_INTEREST,   // Related to user's search terms
    TRENDING           // From trending (fallback)
}

/**
 * Related video with source information
 */
data class RelatedVideoInfo(
    val video: Video,
    val sourceVideoId: String,
    val watchTimestamp: Long
)
