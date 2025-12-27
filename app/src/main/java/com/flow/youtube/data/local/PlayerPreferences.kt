package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_preferences")

class PlayerPreferences(private val context: Context) {
    
    private object Keys {
        val DEFAULT_QUALITY_WIFI = stringPreferencesKey("default_quality_wifi")
        val DEFAULT_QUALITY_CELLULAR = stringPreferencesKey("default_quality_cellular")
        val BACKGROUND_PLAY_ENABLED = booleanPreferencesKey("background_play_enabled")
        val AUTOPLAY_ENABLED = booleanPreferencesKey("autoplay_enabled")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val TRENDING_REGION = stringPreferencesKey("trending_region")
    }
    
    // Region preference
    val trendingRegion: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.TRENDING_REGION] ?: "US"
        }
    
    suspend fun setTrendingRegion(region: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.TRENDING_REGION] = region
        }
    }
    
    // Quality preferences
    val defaultQualityWifi: Flow<VideoQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            VideoQuality.fromString(preferences[Keys.DEFAULT_QUALITY_WIFI] ?: "1080p")
        }
    
    val defaultQualityCellular: Flow<VideoQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            VideoQuality.fromString(preferences[Keys.DEFAULT_QUALITY_CELLULAR] ?: "480p")
        }
    
    suspend fun setDefaultQualityWifi(quality: VideoQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEFAULT_QUALITY_WIFI] = quality.label
        }
    }
    
    suspend fun setDefaultQualityCellular(quality: VideoQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEFAULT_QUALITY_CELLULAR] = quality.label
        }
    }
    
    // Background play
    val backgroundPlayEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BACKGROUND_PLAY_ENABLED] ?: false
        }
    
    suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BACKGROUND_PLAY_ENABLED] = enabled
        }
    }
    
    // Autoplay
    val autoplayEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.AUTOPLAY_ENABLED] ?: true
        }
    
    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.AUTOPLAY_ENABLED] = enabled
        }
    }
    
    // Subtitles
    val subtitlesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SUBTITLES_ENABLED] ?: false
        }
    
    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBTITLES_ENABLED] = enabled
        }
    }
    
    val preferredSubtitleLanguage: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PREFERRED_SUBTITLE_LANGUAGE] ?: "en"
        }
    
    suspend fun setPreferredSubtitleLanguage(language: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PREFERRED_SUBTITLE_LANGUAGE] = language
        }
    }
    
    // Playback speed
    val playbackSpeed: Flow<Float> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PLAYBACK_SPEED] ?: 1.0f
        }
    
    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_SPEED] = speed
        }
    }
}

enum class VideoQuality(val label: String, val height: Int) {
    Q_144p("144p", 144),
    Q_240p("240p", 240),
    Q_360p("360p", 360),
    Q_480p("480p", 480),
    Q_720p("720p", 720),
    Q_1080p("1080p", 1080),
    Q_1440p("1440p", 1440),
    Q_2160p("2160p", 2160), // 4K
    AUTO("Auto", 0);
    
    companion object {
        fun fromString(label: String): VideoQuality {
            return values().find { it.label == label } ?: Q_720p
        }
        
        fun fromHeight(height: Int): VideoQuality {
            return values()
                .filter { it != AUTO }
                .minByOrNull { kotlin.math.abs(it.height - height) } ?: Q_720p
        }
    }
}
