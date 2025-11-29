package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import com.flow.youtube.data.model.Video
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Flow Recommendation Algorithm V2 - Professional-Grade Recommendations
 * 
 * Multi-signal scoring system:
 * 
 * 1. INTEREST MATCHING (0-100 points)
 *    - Topic/genre alignment with user profile
 *    - Keyword match scoring
 *    - Channel affinity scoring
 * 
 * 2. SOURCE TRUST (0-80 points)  
 *    - Subscription: +60
 *    - Related to liked: +50
 *    - Related to watched: +40
 *    - Search-based: +30
 *    - Discovery: +20
 *
 * 3. FRESHNESS (0-30 points)
 *    - Newer content gets boosted
 *    - Decays exponentially with age
 *
 * 4. ENGAGEMENT PREDICTION (0-40 points)
 *    - View count popularity signal
 *    - Duration preference matching
 *    - Content format preference
 *
 * 5. DIVERSITY INJECTION
 *    - Channel spread ensures variety
 *    - Genre mixing for discovery
 *    - Prevents filter bubbles
 *
 * 6. RECENCY BOOST (0-20 points)
 *    - Videos related to very recent activity get boosted
 */
object FlowAlgorithmV2 {
    
    private const val TAG = "FlowAlgorithmV2"
    
    // =====================
    // SCORING WEIGHTS
    // =====================
    
    // Source trust scores
    private const val SOURCE_SUBSCRIPTION = 60
    private const val SOURCE_RELATED_LIKED = 50
    private const val SOURCE_RELATED_WATCHED_24H = 45
    private const val SOURCE_RELATED_WATCHED_7D = 35
    private const val SOURCE_RELATED_WATCHED_OLD = 25
    private const val SOURCE_SEARCH_INTEREST = 30
    private const val SOURCE_DISCOVERY = 20
    
    // Interest matching max
    private const val INTEREST_MAX_SCORE = 100
    private const val TOPIC_MATCH_MULTIPLIER = 3.0
    private const val KEYWORD_MATCH_MULTIPLIER = 1.5
    private const val CHANNEL_AFFINITY_MULTIPLIER = 2.0
    
    // Freshness
    private const val FRESHNESS_MAX_SCORE = 30
    
    // Engagement prediction
    private const val ENGAGEMENT_MAX_SCORE = 40
    private const val VIEW_COUNT_LOG_BASE = 10.0
    private const val OPTIMAL_DURATION_BONUS = 15 // Videos in preferred length range
    private const val FORMAT_PREFERENCE_BONUS = 10
    
    // Diversity
    private const val CHANNEL_REPEAT_PENALTY = -25
    private const val GENRE_REPEAT_PENALTY = -10
    private const val MAX_SAME_CHANNEL_IN_TOP = 3
    private const val MAX_SAME_GENRE_CONSECUTIVE = 4
    
    // Recency activity boost
    private const val RECENCY_24H_BOOST = 20
    private const val RECENCY_3D_BOOST = 10
    
    // Time constants
    private const val HOURS_24_MS = 24 * 60 * 60 * 1000L
    private const val DAYS_3_MS = 3 * 24 * 60 * 60 * 1000L
    private const val DAYS_7_MS = 7 * 24 * 60 * 60 * 1000L
    
    /**
     * Enhanced candidate with all scoring metadata
     */
    data class EnhancedCandidate(
        val video: Video,
        val source: VideoSource,
        val sourceScore: Int,
        val interestScore: Double,
        val freshnessScore: Int,
        val engagementScore: Int,
        val diversityPenalty: Int,
        val recencyBoost: Int,
        val matchedTopics: List<String>,
        val contentFormat: String,
        val relatedToVideoId: String? = null,
        val relatedToTimestamp: Long? = null
    ) {
        val totalScore: Int
            get() = sourceScore + interestScore.toInt() + freshnessScore + 
                    engagementScore + diversityPenalty + recencyBoost
        
        fun toScoredVideo(): ScoredVideo {
            val reasons = mutableListOf<String>()
            if (sourceScore >= SOURCE_SUBSCRIPTION) reasons.add("subscribed")
            if (sourceScore >= SOURCE_RELATED_LIKED) reasons.add("related_liked")
            if (interestScore > 30) reasons.add("high_interest")
            if (freshnessScore > 20) reasons.add("fresh")
            if (engagementScore > 25) reasons.add("engaging")
            if (matchedTopics.isNotEmpty()) reasons.add("topics:${matchedTopics.take(3).joinToString(",")}")
            
            return ScoredVideo(
                video = video,
                source = source,
                baseScore = sourceScore,
                flowScore = totalScore,
                relatedToVideoId = relatedToVideoId,
                relatedToWatchTimestamp = relatedToTimestamp,
                scoreReasons = reasons
            )
        }
    }
    
    /**
     * Score and rank videos with the enhanced algorithm
     */
    suspend fun scoreAndRank(
        context: Context,
        candidates: List<ScoredVideo>,
        subscriptionChannelIds: Set<String>,
        recentWatchHistory: Map<String, Long>,
        likedVideoIds: Set<String>,
        searchInterestVideoIds: Set<String>,
        currentTime: Long = System.currentTimeMillis()
    ): List<ScoredVideo> {
        
        if (candidates.isEmpty()) return emptyList()
        
        Log.d(TAG, "Enhanced scoring ${candidates.size} candidates")
        
        val interestProfile = InterestProfile.getInstance(context)
        val channelAffinities = interestProfile.getChannelAffinities()
        val topGenres = interestProfile.getTopGenres(10)
        
        // Track channel and genre counts for diversity
        val channelCounts = mutableMapOf<String, Int>()
        val genreCounts = mutableMapOf<String, Int>()
        
        // Score each candidate
        val enhanced = candidates.map { candidate ->
            // 1. SOURCE TRUST SCORE
            val sourceScore = calculateSourceScore(
                candidate = candidate,
                subscriptionChannelIds = subscriptionChannelIds,
                likedVideoIds = likedVideoIds,
                recentWatchHistory = recentWatchHistory,
                currentTime = currentTime
            )
            
            // 2. INTEREST MATCHING
            val interestResult = interestProfile.scoreVideoInterest(
                title = candidate.video.title,
                channelId = candidate.video.channelId,
                channelName = candidate.video.channelName,
                duration = candidate.video.duration
            )
            val interestScore = calculateInterestScore(interestResult)
            
            // 3. FRESHNESS
            val freshnessScore = calculateFreshnessScore(candidate.video.uploadDate)
            
            // 4. ENGAGEMENT PREDICTION
            val engagementScore = calculateEngagementScore(
                viewCount = candidate.video.viewCount,
                duration = candidate.video.duration,
                format = interestResult.contentFormat,
                topGenres = topGenres
            )
            
            // 5. DIVERSITY PENALTY
            val channelId = candidate.video.channelId
            val currentChannelCount = channelCounts.getOrDefault(channelId, 0)
            channelCounts[channelId] = currentChannelCount + 1
            
            val videoGenres = interestResult.matchedTopics.filter { it in InterestProfile.GENRE_KEYWORDS.keys }
            videoGenres.forEach { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
            }
            
            val diversityPenalty = calculateDiversityPenalty(
                channelCount = currentChannelCount,
                genreCounts = videoGenres.mapNotNull { genreCounts[it]?.minus(1) }.maxOrNull() ?: 0
            )
            
            // 6. RECENCY BOOST
            val recencyBoost = calculateRecencyBoost(
                candidate = candidate,
                recentWatchHistory = recentWatchHistory,
                currentTime = currentTime
            )
            
            EnhancedCandidate(
                video = candidate.video,
                source = candidate.source,
                sourceScore = sourceScore,
                interestScore = interestScore,
                freshnessScore = freshnessScore,
                engagementScore = engagementScore,
                diversityPenalty = diversityPenalty,
                recencyBoost = recencyBoost,
                matchedTopics = interestResult.matchedTopics,
                contentFormat = interestResult.contentFormat,
                relatedToVideoId = candidate.relatedToVideoId,
                relatedToTimestamp = candidate.relatedToWatchTimestamp
            )
        }
        
        // Sort by total score
        val sorted = enhanced.sortedByDescending { it.totalScore }
        
        // Apply diversity-aware reranking
        val reranked = applyDiversityReranking(sorted)
        
        Log.d(TAG, "Scoring complete. Top 5 scores: ${reranked.take(5).map { it.totalScore }}")
        
        return reranked.map { it.toScoredVideo() }
    }
    
    /**
     * Calculate source trust score
     */
    private fun calculateSourceScore(
        candidate: ScoredVideo,
        subscriptionChannelIds: Set<String>,
        likedVideoIds: Set<String>,
        recentWatchHistory: Map<String, Long>,
        currentTime: Long
    ): Int {
        var score = 0
        
        // Subscription bonus
        if (subscriptionChannelIds.contains(candidate.video.channelId)) {
            score += SOURCE_SUBSCRIPTION
        }
        
        // Source-based scoring
        when (candidate.source) {
            VideoSource.SUBSCRIPTION -> score += 10 // Already from subscription
            VideoSource.RELATED_TO_WATCHED -> {
                val watchTime = candidate.relatedToWatchTimestamp ?: 0L
                val timeSince = currentTime - watchTime
                score += when {
                    timeSince < HOURS_24_MS -> SOURCE_RELATED_WATCHED_24H
                    timeSince < DAYS_7_MS -> SOURCE_RELATED_WATCHED_7D
                    else -> SOURCE_RELATED_WATCHED_OLD
                }
                
                // Extra boost if related to liked video
                if (candidate.relatedToVideoId != null && likedVideoIds.contains(candidate.relatedToVideoId)) {
                    score += 15
                }
            }
            VideoSource.SEARCH_INTEREST -> score += SOURCE_SEARCH_INTEREST
            VideoSource.TRENDING -> score += SOURCE_DISCOVERY
        }
        
        return score
    }
    
    /**
     * Calculate interest match score from profile
     */
    private fun calculateInterestScore(interestResult: InterestProfile.InterestScore): Double {
        val topicContribution = (interestResult.topicScore * TOPIC_MATCH_MULTIPLIER).coerceAtMost(50.0)
        val keywordContribution = (interestResult.keywordScore * KEYWORD_MATCH_MULTIPLIER).coerceAtMost(25.0)
        val channelContribution = (interestResult.channelScore * CHANNEL_AFFINITY_MULTIPLIER).coerceAtMost(25.0)
        
        return (topicContribution + keywordContribution + channelContribution).coerceAtMost(INTEREST_MAX_SCORE.toDouble())
    }
    
    /**
     * Calculate freshness score based on upload date
     */
    private fun calculateFreshnessScore(uploadDate: String): Int {
        val lowerDate = uploadDate.lowercase()
        
        return when {
            lowerDate.contains("minute") || lowerDate.contains("hour") -> FRESHNESS_MAX_SCORE
            lowerDate.contains("day") -> {
                val days = extractNumber(lowerDate)
                when {
                    days <= 1 -> 25
                    days <= 3 -> 20
                    days <= 7 -> 15
                    days <= 14 -> 10
                    else -> 5
                }
            }
            lowerDate.contains("week") -> {
                val weeks = extractNumber(lowerDate)
                when {
                    weeks <= 1 -> 12
                    weeks <= 2 -> 8
                    weeks <= 4 -> 4
                    else -> 0
                }
            }
            lowerDate.contains("month") -> {
                val months = extractNumber(lowerDate)
                if (months <= 1) 3 else 0
            }
            else -> 0
        }
    }
    
    /**
     * Calculate engagement prediction score
     */
    private fun calculateEngagementScore(
        viewCount: Long,
        duration: Int,
        format: String,
        topGenres: List<String>
    ): Int {
        var score = 0
        
        // View count popularity (logarithmic to not over-favor viral content)
        if (viewCount > 0) {
            val logViews = ln(viewCount.toDouble()) / ln(VIEW_COUNT_LOG_BASE)
            score += (logViews * 2).toInt().coerceAtMost(20)
        }
        
        // Duration preference (most people prefer 5-20 minute videos)
        val optimalDuration = duration in 300..1200 // 5-20 minutes
        if (optimalDuration) {
            score += OPTIMAL_DURATION_BONUS
        } else if (duration in 120..1800) { // 2-30 minutes still good
            score += OPTIMAL_DURATION_BONUS / 2
        }
        
        // Format preference (boost shorts if user watches them, etc.)
        // For now, give slight boost to standard and long-form (more substantive)
        when (format) {
            "standard" -> score += 5
            "long_form" -> score += FORMAT_PREFERENCE_BONUS
            "series" -> score += 8
        }
        
        return score.coerceAtMost(ENGAGEMENT_MAX_SCORE)
    }
    
    /**
     * Calculate diversity penalty for repeated channels/genres
     */
    private fun calculateDiversityPenalty(channelCount: Int, genreCounts: Int): Int {
        var penalty = 0
        
        if (channelCount >= MAX_SAME_CHANNEL_IN_TOP) {
            penalty += CHANNEL_REPEAT_PENALTY * (channelCount - MAX_SAME_CHANNEL_IN_TOP + 1)
        }
        
        if (genreCounts >= MAX_SAME_GENRE_CONSECUTIVE) {
            penalty += GENRE_REPEAT_PENALTY
        }
        
        return penalty
    }
    
    /**
     * Calculate recency boost for videos related to very recent activity
     */
    private fun calculateRecencyBoost(
        candidate: ScoredVideo,
        recentWatchHistory: Map<String, Long>,
        currentTime: Long
    ): Int {
        if (candidate.relatedToVideoId == null || candidate.relatedToWatchTimestamp == null) {
            return 0
        }
        
        val timeSince = currentTime - candidate.relatedToWatchTimestamp
        return when {
            timeSince < HOURS_24_MS -> RECENCY_24H_BOOST
            timeSince < DAYS_3_MS -> RECENCY_3D_BOOST
            else -> 0
        }
    }
    
    /**
     * Apply diversity-aware reranking to prevent same channel/genre clustering
     */
    private fun applyDiversityReranking(sorted: List<EnhancedCandidate>): List<EnhancedCandidate> {
        if (sorted.size <= 10) return sorted
        
        val result = mutableListOf<EnhancedCandidate>()
        val remaining = sorted.toMutableList()
        val recentChannels = mutableListOf<String>()
        val recentGenres = mutableListOf<String>()
        
        while (remaining.isNotEmpty() && result.size < sorted.size) {
            // Find best candidate that doesn't violate diversity rules too much
            val candidate = remaining.firstOrNull { candidate ->
                val channelOk = recentChannels.takeLast(3).count { it == candidate.video.channelId } < 2
                val genresOk = candidate.matchedTopics.none { genre ->
                    recentGenres.takeLast(4).count { it == genre } >= 3
                }
                channelOk && genresOk
            } ?: remaining.first()
            
            result.add(candidate)
            remaining.remove(candidate)
            recentChannels.add(candidate.video.channelId)
            recentGenres.addAll(candidate.matchedTopics.filter { it in InterestProfile.GENRE_KEYWORDS.keys })
        }
        
        return result
    }
    
    /**
     * Merge and deduplicate from multiple sources with source tracking
     */
    fun mergeAndDeduplicate(
        subscriptionVideos: List<Video>,
        relatedVideos: List<RelatedVideoInfo>,
        searchInterestVideos: List<Video>,
        discoveryVideos: List<Video>,
        watchedVideoIds: Set<String>
    ): List<ScoredVideo> {
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<ScoredVideo>()
        
        // Subscription videos (highest priority)
        subscriptionVideos.forEach { video ->
            if (video.id !in seenIds && video.id !in watchedVideoIds) {
                seenIds.add(video.id)
                result.add(ScoredVideo(
                    video = video,
                    source = VideoSource.SUBSCRIPTION,
                    baseScore = SOURCE_SUBSCRIPTION
                ))
            }
        }
        
        // Related videos
        relatedVideos.forEach { related ->
            if (related.video.id !in seenIds && related.video.id !in watchedVideoIds) {
                seenIds.add(related.video.id)
                result.add(ScoredVideo(
                    video = related.video,
                    source = VideoSource.RELATED_TO_WATCHED,
                    relatedToVideoId = related.sourceVideoId,
                    relatedToWatchTimestamp = related.watchTimestamp
                ))
            }
        }
        
        // Search interest videos
        searchInterestVideos.forEach { video ->
            if (video.id !in seenIds && video.id !in watchedVideoIds) {
                seenIds.add(video.id)
                result.add(ScoredVideo(
                    video = video,
                    source = VideoSource.SEARCH_INTEREST
                ))
            }
        }
        
        // Discovery videos
        discoveryVideos.forEach { video ->
            if (video.id !in seenIds && video.id !in watchedVideoIds) {
                seenIds.add(video.id)
                result.add(ScoredVideo(
                    video = video,
                    source = VideoSource.TRENDING
                ))
            }
        }
        
        Log.d(TAG, "Merged ${result.size} unique videos")
        return result
    }
    
    /**
     * Light shuffle maintaining rough score order
     */
    fun lightShuffle(videos: List<ScoredVideo>, bucketSize: Int = 6): List<ScoredVideo> {
        if (videos.size <= bucketSize) return videos
        
        val result = mutableListOf<ScoredVideo>()
        
        // Keep top items more stable, increase shuffle in lower ranks
        videos.chunked(bucketSize).forEachIndexed { index, bucket ->
            val shuffleIntensity = (0.2 + index * 0.1).coerceAtMost(0.6)
            val shuffleCount = (bucket.size * shuffleIntensity).toInt()
            
            if (shuffleCount > 0 && bucket.size > 1) {
                val shuffled = bucket.toMutableList()
                repeat(shuffleCount) {
                    val i = Random.nextInt(shuffled.size)
                    val j = Random.nextInt(shuffled.size)
                    val temp = shuffled[i]
                    shuffled[i] = shuffled[j]
                    shuffled[j] = temp
                }
                result.addAll(shuffled)
            } else {
                result.addAll(bucket)
            }
        }
        
        return result
    }
    
    private fun extractNumber(text: String): Int {
        return text.split(" ").firstOrNull { it.toIntOrNull() != null }?.toInt() ?: 1
    }
}
