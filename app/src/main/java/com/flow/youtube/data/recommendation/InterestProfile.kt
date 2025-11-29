package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.interestDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_interests")

/**
 * Tracks and manages user interest profiles for smarter recommendations.
 * 
 * Learns from:
 * - Video titles watched
 * - Channel names subscribed to
 * - Search queries
 * - Liked video topics
 * - Watch duration patterns
 */
class InterestProfile private constructor(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "InterestProfile"
        
        // DataStore keys
        private val TOPIC_SCORES_KEY = stringPreferencesKey("topic_scores")
        private val CHANNEL_AFFINITY_KEY = stringPreferencesKey("channel_affinity")
        private val KEYWORD_SCORES_KEY = stringPreferencesKey("keyword_scores")
        private val GENRE_PREFERENCES_KEY = stringPreferencesKey("genre_preferences")
        private val CREATOR_TYPES_KEY = stringPreferencesKey("creator_types")
        
        // Decay factor for older interactions
        private const val DECAY_FACTOR = 0.95
        private const val MAX_TOPICS = 100
        private const val MAX_KEYWORDS = 200
        
        // Common genre/category keywords for detection
        val GENRE_KEYWORDS = mapOf(
            "gaming" to listOf("game", "gaming", "gameplay", "playthrough", "walkthrough", "lets play", "stream", "twitch", "esports", "fortnite", "minecraft", "gta", "cod", "valorant", "league", "apex"),
            "music" to listOf("music", "song", "album", "cover", "remix", "official video", "mv", "lyrics", "beat", "instrumental", "concert", "live performance", "playlist"),
            "tech" to listOf("tech", "technology", "review", "unboxing", "iphone", "android", "samsung", "apple", "google", "laptop", "pc", "build", "setup", "gadget", "software"),
            "education" to listOf("tutorial", "how to", "learn", "course", "lesson", "explained", "guide", "tips", "education", "study", "lecture", "class"),
            "entertainment" to listOf("funny", "comedy", "prank", "challenge", "reaction", "vlog", "daily", "lifestyle", "entertainment", "talk show", "interview"),
            "news" to listOf("news", "breaking", "update", "politics", "current events", "analysis", "report", "documentary"),
            "sports" to listOf("sports", "football", "basketball", "soccer", "nba", "nfl", "highlights", "goal", "match", "game", "workout", "fitness", "gym"),
            "food" to listOf("food", "cooking", "recipe", "kitchen", "chef", "eat", "restaurant", "mukbang", "asmr food", "baking"),
            "beauty" to listOf("makeup", "beauty", "skincare", "hair", "fashion", "style", "outfit", "grwm", "routine"),
            "science" to listOf("science", "experiment", "physics", "chemistry", "biology", "space", "nasa", "discovery", "research"),
            "art" to listOf("art", "drawing", "painting", "creative", "design", "animation", "illustration", "digital art", "sketch"),
            "automotive" to listOf("car", "cars", "auto", "vehicle", "driving", "race", "motorsport", "automotive", "engine", "motorcycle"),
            "finance" to listOf("finance", "investing", "stocks", "crypto", "bitcoin", "money", "trading", "business", "entrepreneur", "startup"),
            "travel" to listOf("travel", "trip", "vacation", "destination", "explore", "adventure", "tour", "country", "city"),
            "pets" to listOf("pet", "dog", "cat", "animal", "puppy", "kitten", "cute", "funny animals"),
            "asmr" to listOf("asmr", "relaxing", "sleep", "tingles", "whisper", "triggers"),
            "podcasts" to listOf("podcast", "episode", "talk", "discussion", "conversation", "interview")
        )
        
        // Content format detection
        val FORMAT_KEYWORDS = mapOf(
            "short" to listOf("shorts", "#shorts", "short", "tiktok", "reels", "60 seconds"),
            "long_form" to listOf("full", "complete", "entire", "hour", "documentary", "movie", "film"),
            "series" to listOf("episode", "ep.", "part", "season", "series", "chapter"),
            "live" to listOf("live", "stream", "streaming", "premiere", "live now"),
            "compilation" to listOf("compilation", "best of", "top 10", "top 5", "moments", "highlights")
        )
        
        @Volatile
        private var INSTANCE: InterestProfile? = null
        
        fun getInstance(context: Context): InterestProfile {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InterestProfile(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * User's interest scores by topic/genre
     */
    data class TopicScore(
        val topic: String,
        var score: Double,
        var lastUpdated: Long = System.currentTimeMillis(),
        var interactionCount: Int = 1
    )
    
    /**
     * Channel affinity - how much user likes specific channels
     */
    data class ChannelAffinity(
        val channelId: String,
        val channelName: String,
        var affinityScore: Double,
        var watchCount: Int = 1,
        var likeCount: Int = 0,
        var lastInteraction: Long = System.currentTimeMillis()
    )
    
    /**
     * Extract topics/genres from a video title
     */
    fun extractTopics(title: String, channelName: String = ""): List<String> {
        val topics = mutableSetOf<String>()
        val lowerTitle = title.lowercase()
        val lowerChannel = channelName.lowercase()
        val combined = "$lowerTitle $lowerChannel"
        
        // Check for genre matches
        GENRE_KEYWORDS.forEach { (genre, keywords) ->
            if (keywords.any { keyword -> combined.contains(keyword) }) {
                topics.add(genre)
            }
        }
        
        // Extract significant words (3+ chars, not common words)
        val stopWords = setOf("the", "and", "for", "with", "this", "that", "from", "have", "will", "what", "how", "why", "when", "where", "who", "new", "you", "your", "are", "was", "were", "been", "being", "has", "had", "does", "did", "can", "could", "would", "should", "may", "might", "must", "shall", "into", "onto", "upon", "about", "just", "only", "also", "very", "most", "more", "some", "such", "than", "then", "now", "here", "there", "out", "all", "any", "both", "each", "few", "many", "much", "own", "same", "other")
        
        val words = lowerTitle.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 3 && !stopWords.contains(it) }
            .take(5)
        
        topics.addAll(words)
        
        return topics.toList()
    }
    
    /**
     * Detect content format from title
     */
    fun detectFormat(title: String, duration: Int): String {
        val lowerTitle = title.lowercase()
        
        // Check duration first
        if (duration < 120) return "short" // Under 2 minutes
        if (duration > 1800) return "long_form" // Over 30 minutes
        
        // Check title keywords
        FORMAT_KEYWORDS.forEach { (format, keywords) ->
            if (keywords.any { keyword -> lowerTitle.contains(keyword) }) {
                return format
            }
        }
        
        return "standard"
    }
    
    /**
     * Record a video watch interaction to update interests
     */
    suspend fun recordWatch(
        videoTitle: String,
        channelId: String,
        channelName: String,
        watchDuration: Int,
        totalDuration: Int
    ) {
        val watchRatio = if (totalDuration > 0) watchDuration.toDouble() / totalDuration else 0.5
        val interestBoost = when {
            watchRatio >= 0.8 -> 1.0    // Watched most = high interest
            watchRatio >= 0.5 -> 0.6    // Watched half = moderate interest
            watchRatio >= 0.25 -> 0.3   // Watched quarter = some interest
            else -> 0.1                  // Clicked away = low interest
        }
        
        val topics = extractTopics(videoTitle, channelName)
        updateTopicScores(topics, interestBoost)
        updateChannelAffinity(channelId, channelName, watchInterest = interestBoost)
        
        Log.d(TAG, "Recorded watch: $videoTitle, topics=$topics, boost=$interestBoost")
    }
    
    /**
     * Record a like interaction (strong positive signal)
     */
    suspend fun recordLike(videoTitle: String, channelId: String, channelName: String) {
        val topics = extractTopics(videoTitle, channelName)
        updateTopicScores(topics, boost = 1.5) // Likes are strong signals
        updateChannelAffinity(channelId, channelName, liked = true)
        
        Log.d(TAG, "Recorded like: $videoTitle, topics=$topics")
    }
    
    /**
     * Record a subscription (very strong signal)
     */
    suspend fun recordSubscription(channelId: String, channelName: String) {
        updateChannelAffinity(channelId, channelName, subscribed = true)
        
        // Extract topics from channel name
        val topics = extractTopics(channelName)
        updateTopicScores(topics, boost = 2.0) // Subscriptions are strongest signals
        
        Log.d(TAG, "Recorded subscription: $channelName, topics=$topics")
    }
    
    /**
     * Record a search query
     */
    suspend fun recordSearch(query: String) {
        val topics = extractTopics(query)
        updateTopicScores(topics, boost = 0.8)
        updateKeywordScores(query.lowercase().split(Regex("\\s+")))
        
        Log.d(TAG, "Recorded search: $query, topics=$topics")
    }
    
    /**
     * Update topic scores based on interaction
     */
    private suspend fun updateTopicScores(topics: List<String>, boost: Double) {
        context.interestDataStore.edit { prefs ->
            val json = prefs[TOPIC_SCORES_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, TopicScore>>() {}.type
            val scores: MutableMap<String, TopicScore> = gson.fromJson(json, type) ?: mutableMapOf()
            
            // Apply decay to all existing scores
            val now = System.currentTimeMillis()
            scores.values.forEach { score ->
                val daysSinceUpdate = (now - score.lastUpdated) / (24 * 60 * 60 * 1000.0)
                if (daysSinceUpdate > 0) {
                    score.score *= Math.pow(DECAY_FACTOR, daysSinceUpdate)
                }
            }
            
            // Update scores for current topics
            topics.forEach { topic ->
                val existing = scores[topic]
                if (existing != null) {
                    existing.score = (existing.score + boost).coerceAtMost(100.0)
                    existing.lastUpdated = now
                    existing.interactionCount++
                } else {
                    scores[topic] = TopicScore(topic, boost, now)
                }
            }
            
            // Keep only top topics
            val sorted = scores.entries.sortedByDescending { it.value.score }
            val trimmed = sorted.take(MAX_TOPICS).associate { it.key to it.value }
            
            prefs[TOPIC_SCORES_KEY] = gson.toJson(trimmed)
        }
    }
    
    /**
     * Update keyword scores for search-based recommendations
     */
    private suspend fun updateKeywordScores(keywords: List<String>) {
        context.interestDataStore.edit { prefs ->
            val json = prefs[KEYWORD_SCORES_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, Double>>() {}.type
            val scores: MutableMap<String, Double> = gson.fromJson(json, type) ?: mutableMapOf()
            
            keywords.filter { it.length >= 3 }.forEach { keyword ->
                scores[keyword] = (scores[keyword] ?: 0.0) + 1.0
            }
            
            // Keep only top keywords
            val sorted = scores.entries.sortedByDescending { it.value }
            val trimmed = sorted.take(MAX_KEYWORDS).associate { it.key to it.value }
            
            prefs[KEYWORD_SCORES_KEY] = gson.toJson(trimmed)
        }
    }
    
    /**
     * Update channel affinity score
     */
    private suspend fun updateChannelAffinity(
        channelId: String,
        channelName: String,
        watchInterest: Double = 0.0,
        liked: Boolean = false,
        subscribed: Boolean = false
    ) {
        context.interestDataStore.edit { prefs ->
            val json = prefs[CHANNEL_AFFINITY_KEY] ?: "{}"
            val type = object : TypeToken<MutableMap<String, ChannelAffinity>>() {}.type
            val affinities: MutableMap<String, ChannelAffinity> = gson.fromJson(json, type) ?: mutableMapOf()
            
            val existing = affinities[channelId]
            if (existing != null) {
                existing.affinityScore += watchInterest
                if (liked) {
                    existing.likeCount++
                    existing.affinityScore += 5.0
                }
                if (subscribed) {
                    existing.affinityScore += 20.0
                }
                existing.watchCount++
                existing.lastInteraction = System.currentTimeMillis()
            } else {
                affinities[channelId] = ChannelAffinity(
                    channelId = channelId,
                    channelName = channelName,
                    affinityScore = watchInterest + (if (liked) 5.0 else 0.0) + (if (subscribed) 20.0 else 0.0),
                    likeCount = if (liked) 1 else 0
                )
            }
            
            prefs[CHANNEL_AFFINITY_KEY] = gson.toJson(affinities)
        }
    }
    
    /**
     * Get top topics user is interested in
     */
    suspend fun getTopTopics(limit: Int = 20): List<TopicScore> {
        val prefs = context.interestDataStore.data.first()
        val json = prefs[TOPIC_SCORES_KEY] ?: return emptyList()
        val type = object : TypeToken<Map<String, TopicScore>>() {}.type
        val scores: Map<String, TopicScore> = gson.fromJson(json, type) ?: emptyMap()
        
        return scores.values
            .sortedByDescending { it.score }
            .take(limit)
    }
    
    /**
     * Get top genres/categories
     */
    suspend fun getTopGenres(limit: Int = 5): List<String> {
        val topics = getTopTopics(50)
        val genreScores = mutableMapOf<String, Double>()
        
        topics.forEach { topic ->
            GENRE_KEYWORDS.forEach { (genre, keywords) ->
                if (topic.topic == genre || keywords.any { it.contains(topic.topic) || topic.topic.contains(it) }) {
                    genreScores[genre] = (genreScores[genre] ?: 0.0) + topic.score
                }
            }
        }
        
        return genreScores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }
    
    /**
     * Get channel affinities
     */
    suspend fun getChannelAffinities(): Map<String, ChannelAffinity> {
        val prefs = context.interestDataStore.data.first()
        val json = prefs[CHANNEL_AFFINITY_KEY] ?: return emptyMap()
        val type = object : TypeToken<Map<String, ChannelAffinity>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Get keyword scores for matching
     */
    suspend fun getKeywordScores(): Map<String, Double> {
        val prefs = context.interestDataStore.data.first()
        val json = prefs[KEYWORD_SCORES_KEY] ?: return emptyMap()
        val type = object : TypeToken<Map<String, Double>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Score a video based on user's interest profile
     */
    suspend fun scoreVideoInterest(
        title: String,
        channelId: String,
        channelName: String,
        duration: Int
    ): InterestScore {
        val topics = extractTopics(title, channelName)
        val format = detectFormat(title, duration)
        
        val topicScores = getTopTopics(50).associate { it.topic to it.score }
        val keywords = getKeywordScores()
        val channelAffinities = getChannelAffinities()
        
        var topicMatchScore = 0.0
        var keywordMatchScore = 0.0
        val matchedTopics = mutableListOf<String>()
        
        // Score topic matches
        topics.forEach { topic ->
            topicScores[topic]?.let { score ->
                topicMatchScore += score
                matchedTopics.add(topic)
            }
        }
        
        // Score keyword matches in title
        val lowerTitle = title.lowercase()
        keywords.forEach { (keyword, score) ->
            if (lowerTitle.contains(keyword)) {
                keywordMatchScore += score * 0.5
            }
        }
        
        // Score channel affinity
        val channelScore = channelAffinities[channelId]?.affinityScore ?: 0.0
        
        return InterestScore(
            totalScore = topicMatchScore + keywordMatchScore + channelScore,
            topicScore = topicMatchScore,
            keywordScore = keywordMatchScore,
            channelScore = channelScore,
            matchedTopics = matchedTopics,
            contentFormat = format
        )
    }
    
    /**
     * Generate search queries based on user interests for discovery
     */
    suspend fun generateDiscoveryQueries(count: Int = 5): List<String> {
        val topGenres = getTopGenres(10)
        val topTopics = getTopTopics(20)
        val queries = mutableListOf<String>()
        
        // Combine genres with modifiers for variety
        val modifiers = listOf("best", "new", "top", "2024", "2025", "popular", "trending", "amazing", "must watch")
        
        topGenres.shuffled().take(count).forEach { genre ->
            val modifier = modifiers.random()
            queries.add("$modifier $genre")
        }
        
        // Add some topic-specific queries
        topTopics.shuffled().take(count).forEach { topic ->
            if (topic.topic !in GENRE_KEYWORDS.keys) {
                queries.add(topic.topic)
            }
        }
        
        return queries.shuffled().take(count)
    }
    
    data class InterestScore(
        val totalScore: Double,
        val topicScore: Double,
        val keywordScore: Double,
        val channelScore: Double,
        val matchedTopics: List<String>,
        val contentFormat: String
    )
}
