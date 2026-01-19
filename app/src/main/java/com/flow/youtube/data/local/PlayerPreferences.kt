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
        val SKIP_SILENCE_ENABLED = booleanPreferencesKey("skip_silence_enabled")
        val AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
        val MANUAL_PIP_BUTTON_ENABLED = booleanPreferencesKey("manual_pip_button_enabled")
        
        // Buffer settings
        val MIN_BUFFER_MS = intPreferencesKey("min_buffer_ms")
        val MAX_BUFFER_MS = intPreferencesKey("max_buffer_ms")
        val BUFFER_FOR_PLAYBACK_MS = intPreferencesKey("buffer_for_playback_ms")
        val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
        
        // Profiles
        val BUFFER_PROFILE = stringPreferencesKey("buffer_profile")
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

    // Skip Silence
    val skipSilenceEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SKIP_SILENCE_ENABLED] ?: false
        }

    suspend fun setSkipSilenceEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SKIP_SILENCE_ENABLED] = enabled
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

    // PiP Preferences
    val autoPipEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.AUTO_PIP_ENABLED] ?: false // Default OFF
        }

    suspend fun setAutoPipEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.AUTO_PIP_ENABLED] = enabled
        }
    }

    val manualPipButtonEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MANUAL_PIP_BUTTON_ENABLED] ?: true // Default ON
        }

    suspend fun setManualPipButtonEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MANUAL_PIP_BUTTON_ENABLED] = enabled
        }
    }

    // Buffer Preferences
    val minBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MIN_BUFFER_MS] ?: 30000 // Default 30s
        }

    suspend fun setMinBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MIN_BUFFER_MS] = ms
        }
    }

    val maxBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MAX_BUFFER_MS] ?: 100000 // Default 100s
        }

    suspend fun setMaxBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MAX_BUFFER_MS] = ms
        }
    }

    val bufferForPlaybackMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] ?: 1000 // Default 1s
        }

    suspend fun setBufferForPlaybackMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] = ms
        }
    }
    
    val bufferForPlaybackAfterRebufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] ?: 2500 // Default 2.5s
        }

    suspend fun setBufferForPlaybackAfterRebufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] = ms
        }
    }

    val bufferProfile: Flow<BufferProfile> = context.playerPreferencesDataStore.data
        .map { preferences ->
            BufferProfile.fromString(preferences[Keys.BUFFER_PROFILE] ?: "STABLE")
        }

    suspend fun setBufferProfile(profile: BufferProfile) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BUFFER_PROFILE] = profile.name
            
            // If not custom, apply the profile values immediately
            if (profile != BufferProfile.CUSTOM) {
                preferences[Keys.MIN_BUFFER_MS] = profile.minBuffer
                preferences[Keys.MAX_BUFFER_MS] = profile.maxBuffer
                preferences[Keys.BUFFER_FOR_PLAYBACK_MS] = profile.playbackBuffer
                preferences[Keys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] = profile.rebufferBuffer
            }
        }
    }
}

enum class BufferProfile(
    val label: String,
    val minBuffer: Int,
    val maxBuffer: Int,
    val playbackBuffer: Int,
    val rebufferBuffer: Int
) {
    AGGRESSIVE("Aggressive (Fast Start)", 15000, 40000, 1000, 2000),      // Fast start, lower redundancy
    STABLE("Stable (Anti-Throttling)", 30000, 60000, 2500, 5000),        // Goldilocks / Recommended
    DATASAVER("Data Saver", 15000, 30000, 2500, 5000),                   // Low RAM/Cache usage
    CUSTOM("Custom", -1, -1, -1, -1);                                    // User defined

    companion object {
        fun fromString(name: String): BufferProfile = values().find { it.name == name } ?: STABLE
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
