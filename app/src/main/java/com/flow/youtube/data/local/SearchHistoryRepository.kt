package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

data class SearchHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: SearchType = SearchType.TEXT
)

enum class SearchType {
    TEXT, VOICE, SUGGESTION
}

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType = SuggestionType.VIDEO
)

enum class SuggestionType {
    VIDEO, CHANNEL, PLAYLIST, TRENDING
}

data class SearchFilter(
    val contentType: ContentType = ContentType.ALL,
    val uploadDate: UploadDate = UploadDate.ANY,
    val duration: Duration = Duration.ANY,
    val sortBy: SortBy = SortBy.RELEVANCE,
    val features: Set<Feature> = emptySet()
)

enum class ContentType {
    ALL, VIDEOS, CHANNELS, PLAYLISTS
}

enum class UploadDate {
    ANY, LAST_HOUR, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
}

enum class Duration {
    ANY, UNDER_4_MINUTES, FOUR_TO_20_MINUTES, OVER_20_MINUTES
}

enum class SortBy {
    RELEVANCE, UPLOAD_DATE, VIEW_COUNT, RATING
}

enum class Feature {
    FOUR_K, HD, SUBTITLES, CREATIVE_COMMONS, THREE_D, LIVE, PURCHASED, THREE_SIXTY
}

class SearchHistoryRepository(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
        private val SEARCH_HISTORY_ENABLED_KEY = booleanPreferencesKey("search_history_enabled")
        private val SEARCH_SUGGESTIONS_ENABLED_KEY = booleanPreferencesKey("search_suggestions_enabled")
        private val MAX_HISTORY_SIZE_KEY = intPreferencesKey("max_history_size")
        private val AUTO_DELETE_HISTORY_KEY = booleanPreferencesKey("auto_delete_history")
        private val HISTORY_RETENTION_DAYS_KEY = intPreferencesKey("history_retention_days")
        
        private const val DEFAULT_MAX_HISTORY_SIZE = 50
        private const val DEFAULT_RETENTION_DAYS = 90
    }
    
    // Save search query
    suspend fun saveSearchQuery(query: String, type: SearchType = SearchType.TEXT) {
        if (!isSearchHistoryEnabled()) return
        if (query.isBlank()) return
        
        context.searchDataStore.edit { preferences ->
            val currentHistory = getSearchHistoryList(preferences)
            
            // Remove duplicate if exists
            val filteredHistory = currentHistory.filter { it.query != query }
            
            // Add new item at the beginning
            val newItem = SearchHistoryItem(
                query = query,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            val updatedHistory = listOf(newItem) + filteredHistory
            
            // Trim to max size
            val maxSize = preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE
            val trimmedHistory = updatedHistory.take(maxSize)
            
            // Save
            preferences[SEARCH_HISTORY_KEY] = gson.toJson(trimmedHistory)
        }
    }
    
    // Get search history as Flow
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryItem>> {
        return context.searchDataStore.data.map { preferences ->
            if (preferences[SEARCH_HISTORY_ENABLED_KEY] != false) {
                val history = getSearchHistoryList(preferences)
                filterExpiredHistory(history, preferences)
            } else {
                emptyList()
            }
        }
    }
    
    // Get recent searches (limit)
    suspend fun getRecentSearches(limit: Int = 10): List<SearchHistoryItem> {
        return getSearchHistoryFlow().first().take(limit)
    }
    
    // Delete specific search item
    suspend fun deleteSearchItem(itemId: String) {
        context.searchDataStore.edit { preferences ->
            val currentHistory = getSearchHistoryList(preferences)
            val updatedHistory = currentHistory.filter { it.id != itemId }
            preferences[SEARCH_HISTORY_KEY] = gson.toJson(updatedHistory)
        }
    }
    
    // Clear all search history
    suspend fun clearSearchHistory() {
        context.searchDataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_KEY] = gson.toJson(emptyList<SearchHistoryItem>())
        }
    }
    
    // Settings: Enable/disable search history
    suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_ENABLED_KEY] = enabled
        }
    }
    
    fun isSearchHistoryEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[SEARCH_HISTORY_ENABLED_KEY] ?: true
        }
    }
    
    suspend fun isSearchHistoryEnabled(): Boolean {
        return isSearchHistoryEnabledFlow().first()
    }
    
    // Settings: Enable/disable search suggestions
    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] = enabled
        }
    }
    
    fun isSearchSuggestionsEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[SEARCH_SUGGESTIONS_ENABLED_KEY] ?: true
        }
    }
    
    suspend fun isSearchSuggestionsEnabled(): Boolean {
        return isSearchSuggestionsEnabledFlow().first()
    }
    
    // Settings: Max history size
    suspend fun setMaxHistorySize(size: Int) {
        context.searchDataStore.edit { preferences ->
            preferences[MAX_HISTORY_SIZE_KEY] = size
            
            // Trim existing history if needed
            val currentHistory = getSearchHistoryList(preferences)
            if (currentHistory.size > size) {
                val trimmedHistory = currentHistory.take(size)
                preferences[SEARCH_HISTORY_KEY] = gson.toJson(trimmedHistory)
            }
        }
    }
    
    fun getMaxHistorySizeFlow(): Flow<Int> {
        return context.searchDataStore.data.map { preferences ->
            preferences[MAX_HISTORY_SIZE_KEY] ?: DEFAULT_MAX_HISTORY_SIZE
        }
    }
    
    // Settings: Auto-delete history
    suspend fun setAutoDeleteHistory(enabled: Boolean) {
        context.searchDataStore.edit { preferences ->
            preferences[AUTO_DELETE_HISTORY_KEY] = enabled
        }
    }
    
    fun isAutoDeleteHistoryEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { preferences ->
            preferences[AUTO_DELETE_HISTORY_KEY] ?: false
        }
    }
    
    // Settings: History retention days
    suspend fun setHistoryRetentionDays(days: Int) {
        context.searchDataStore.edit { preferences ->
            preferences[HISTORY_RETENTION_DAYS_KEY] = days
        }
    }
    
    fun getHistoryRetentionDaysFlow(): Flow<Int> {
        return context.searchDataStore.data.map { preferences ->
            preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
        }
    }
    
    // Helper: Parse JSON to list
    private fun getSearchHistoryList(preferences: Preferences): List<SearchHistoryItem> {
        val json = preferences[SEARCH_HISTORY_KEY] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Helper: Filter expired history
    private fun filterExpiredHistory(
        history: List<SearchHistoryItem>,
        preferences: Preferences
    ): List<SearchHistoryItem> {
        val autoDelete = preferences[AUTO_DELETE_HISTORY_KEY] ?: false
        if (!autoDelete) return history
        
        val retentionDays = preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        
        return history.filter { it.timestamp >= cutoffTime }
    }
    
    // Get search suggestions from YouTube API (now handled by YouTubeRepository)
    // This method is kept for backward compatibility but deprecated
    @Deprecated("Use YouTubeRepository.getSearchSuggestions() instead")
    fun getSearchSuggestions(query: String): List<SearchSuggestion> {
        if (query.isBlank()) return emptyList()
        
        // Return empty list - actual suggestions should come from YouTubeRepository
        return emptyList()
    }
}
