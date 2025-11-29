package com.flow.youtube.data.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Service to fetch real-time search suggestions from YouTube/Google
 */
object SearchSuggestionsService {
    
    private const val TAG = "SearchSuggestionsService"
    
    // YouTube suggestions API (same as used by NewPipe)
    private const val YOUTUBE_SUGGESTIONS_URL = "https://suggestqueries-clients6.youtube.com/complete/search"
    
    // Google suggestions API as fallback
    private const val GOOGLE_SUGGESTIONS_URL = "https://suggestqueries.google.com/complete/search"
    
    /**
     * Fetch live search suggestions for a query
     * Returns up to 10 suggestions
     */
    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) {
            return@withContext emptyList()
        }
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$YOUTUBE_SUGGESTIONS_URL?client=youtube&ds=yt&q=$encodedQuery")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val suggestions = parseYouTubeSuggestions(response)
                Log.d(TAG, "Got ${suggestions.size} suggestions for: $query")
                return@withContext suggestions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouTube suggestions", e)
        }
        
        // Fallback to Google suggestions
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$GOOGLE_SUGGESTIONS_URL?client=firefox&q=$encodedQuery")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val suggestions = parseGoogleSuggestions(response)
                Log.d(TAG, "Got ${suggestions.size} Google suggestions for: $query")
                return@withContext suggestions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google suggestions", e)
        }
        
        emptyList()
    }
    
    /**
     * Fetch music-specific search suggestions
     */
    suspend fun getMusicSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) {
            return@withContext emptyList()
        }
        
        try {
            // Add music context to get better music suggestions
            val musicQuery = "$query"
            val encodedQuery = URLEncoder.encode(musicQuery, "UTF-8")
            val url = URL("$YOUTUBE_SUGGESTIONS_URL?client=youtube&ds=yt&q=$encodedQuery")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val suggestions = parseYouTubeSuggestions(response)
                    .filter { suggestion ->
                        // Prioritize music-related suggestions
                        val lower = suggestion.lowercase()
                        lower.contains("song") ||
                        lower.contains("music") ||
                        lower.contains("lyrics") ||
                        lower.contains("audio") ||
                        lower.contains("official") ||
                        !lower.contains("how to") // Filter out tutorials
                    }
                    .take(10)
                
                // If we filtered out too many, just return regular suggestions
                if (suggestions.size < 5) {
                    return@withContext parseYouTubeSuggestions(response).take(10)
                }
                
                Log.d(TAG, "Got ${suggestions.size} music suggestions for: $query")
                return@withContext suggestions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching music suggestions", e)
        }
        
        // Fallback to regular suggestions
        getSuggestions(query)
    }
    
    /**
     * Parse YouTube suggestions response
     * Format: [query, [suggestions], ...]
     */
    private fun parseYouTubeSuggestions(response: String): List<String> {
        return try {
            // Response is JSONP, need to extract JSON
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']') + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                return emptyList()
            }
            
            val jsonString = response.substring(jsonStart, jsonEnd)
            val jsonArray = JSONArray(jsonString)
            
            if (jsonArray.length() > 1) {
                val suggestionsArray = jsonArray.getJSONArray(1)
                val suggestions = mutableListOf<String>()
                
                for (i in 0 until suggestionsArray.length()) {
                    val item = suggestionsArray.get(i)
                    when (item) {
                        is String -> suggestions.add(item)
                        is JSONArray -> {
                            if (item.length() > 0) {
                                suggestions.add(item.getString(0))
                            }
                        }
                    }
                }
                
                suggestions.take(10)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing YouTube suggestions", e)
            emptyList()
        }
    }
    
    /**
     * Parse Google suggestions response
     * Format: [query, [suggestions]]
     */
    private fun parseGoogleSuggestions(response: String): List<String> {
        return try {
            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 1) {
                val suggestionsArray = jsonArray.getJSONArray(1)
                val suggestions = mutableListOf<String>()
                
                for (i in 0 until suggestionsArray.length()) {
                    suggestions.add(suggestionsArray.getString(i))
                }
                
                suggestions.take(10)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google suggestions", e)
            emptyList()
        }
    }
}
