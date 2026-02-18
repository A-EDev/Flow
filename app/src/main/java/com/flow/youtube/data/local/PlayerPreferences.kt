package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")        
        val AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
        val MANUAL_PIP_BUTTON_ENABLED = booleanPreferencesKey("manual_pip_button_enabled")
        
        // Buffer settings
        val MIN_BUFFER_MS = intPreferencesKey("min_buffer_ms")
        val MAX_BUFFER_MS = intPreferencesKey("max_buffer_ms")
        val BUFFER_FOR_PLAYBACK_MS = intPreferencesKey("buffer_for_playback_ms")
        val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
        
        // Buffer profiles
        val BUFFER_PROFILE = stringPreferencesKey("buffer_profile")
        
        // Download settings
        val DOWNLOAD_THREADS = intPreferencesKey("download_threads")
        val PARALLEL_DOWNLOAD_ENABLED = booleanPreferencesKey("parallel_download_enabled")
        val DOWNLOAD_OVER_WIFI_ONLY = booleanPreferencesKey("download_over_wifi_only")
        val DEFAULT_DOWNLOAD_QUALITY = stringPreferencesKey("default_download_quality")
        val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
        val SURFACE_READY_TIMEOUT_MS = longPreferencesKey("surface_ready_timeout_ms")
        
        // Audio track preference
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")

        // Shorts quality preferences
        val SHORTS_QUALITY_WIFI = stringPreferencesKey("shorts_quality_wifi")
        val SHORTS_QUALITY_CELLULAR = stringPreferencesKey("shorts_quality_cellular")
        
        // UI preferences
        val GRID_ITEM_SIZE = stringPreferencesKey("grid_item_size")
        val SLIDER_STYLE = stringPreferencesKey("slider_style")
        val SQUIGGLY_SLIDER_ENABLED = booleanPreferencesKey("squiggly_slider_enabled")
        val SHORTS_SHELF_ENABLED = booleanPreferencesKey("shorts_shelf_enabled")
        val HOME_SHORTS_SHELF_ENABLED = booleanPreferencesKey("home_shorts_shelf_enabled")
        val SHORTS_NAVIGATION_ENABLED = booleanPreferencesKey("shorts_navigation_enabled")
        val PREFERRED_LYRICS_PROVIDER = stringPreferencesKey("preferred_lyrics_provider")
        val SWIPE_GESTURES_ENABLED = booleanPreferencesKey("swipe_gestures_enabled")

        // SponsorBlock per-category action keys
        val SB_ACTION_SPONSOR = stringPreferencesKey("sb_action_sponsor")
        val SB_ACTION_INTRO = stringPreferencesKey("sb_action_intro")
        val SB_ACTION_OUTRO = stringPreferencesKey("sb_action_outro")
        val SB_ACTION_SELFPROMO = stringPreferencesKey("sb_action_selfpromo")
        val SB_ACTION_INTERACTION = stringPreferencesKey("sb_action_interaction")
        val SB_ACTION_MUSIC_OFFTOPIC = stringPreferencesKey("sb_action_music_offtopic")
        val SB_ACTION_FILLER = stringPreferencesKey("sb_action_filler")
        val SB_ACTION_PREVIEW = stringPreferencesKey("sb_action_preview")
        val SB_ACTION_EXCLUSIVE_ACCESS = stringPreferencesKey("sb_action_exclusive_access")

        // SponsorBlock per-category color keys
        val SB_COLOR_SPONSOR = intPreferencesKey("sb_color_sponsor")
        val SB_COLOR_INTRO = intPreferencesKey("sb_color_intro")
        val SB_COLOR_OUTRO = intPreferencesKey("sb_color_outro")
        val SB_COLOR_SELFPROMO = intPreferencesKey("sb_color_selfpromo")
        val SB_COLOR_INTERACTION = intPreferencesKey("sb_color_interaction")
        val SB_COLOR_MUSIC_OFFTOPIC = intPreferencesKey("sb_color_music_offtopic")
        val SB_COLOR_FILLER = intPreferencesKey("sb_color_filler")
        val SB_COLOR_PREVIEW = intPreferencesKey("sb_color_preview")
        val SB_COLOR_EXCLUSIVE_ACCESS = intPreferencesKey("sb_color_exclusive_access")

        // SponsorBlock submit
        val SB_SUBMIT_ENABLED = booleanPreferencesKey("sb_submit_enabled")
        val SB_USER_ID = stringPreferencesKey("sb_user_id")
    }
    
    // Grid item size preference
    val gridItemSize: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.GRID_ITEM_SIZE] ?: "BIG"
        }
    
    suspend fun setGridItemSize(size: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.GRID_ITEM_SIZE] = size
        }
    }

    // Swipe gestures (brightness/volume) enabled preference
    val swipeGesturesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SWIPE_GESTURES_ENABLED] ?: true
        }

    suspend fun setSwipeGesturesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SWIPE_GESTURES_ENABLED] = enabled
        }
    }

    // SponsorBlock per-category action preferences
    fun sbActionForCategory(category: String): Flow<SponsorBlockAction> {
        val key = when (category) {
            "sponsor" -> Keys.SB_ACTION_SPONSOR
            "intro" -> Keys.SB_ACTION_INTRO
            "outro" -> Keys.SB_ACTION_OUTRO
            "selfpromo" -> Keys.SB_ACTION_SELFPROMO
            "interaction" -> Keys.SB_ACTION_INTERACTION
            "music_offtopic" -> Keys.SB_ACTION_MUSIC_OFFTOPIC
            "filler" -> Keys.SB_ACTION_FILLER
            "preview" -> Keys.SB_ACTION_PREVIEW
            "exclusive_access" -> Keys.SB_ACTION_EXCLUSIVE_ACCESS
            else -> Keys.SB_ACTION_SPONSOR
        }
        return context.playerPreferencesDataStore.data.map { preferences ->
            SponsorBlockAction.fromString(preferences[key] ?: SponsorBlockAction.SKIP.name)
        }
    }

    suspend fun setSbActionForCategory(category: String, action: SponsorBlockAction) {
        val key = when (category) {
            "sponsor" -> Keys.SB_ACTION_SPONSOR
            "intro" -> Keys.SB_ACTION_INTRO
            "outro" -> Keys.SB_ACTION_OUTRO
            "selfpromo" -> Keys.SB_ACTION_SELFPROMO
            "interaction" -> Keys.SB_ACTION_INTERACTION
            "music_offtopic" -> Keys.SB_ACTION_MUSIC_OFFTOPIC
            "filler" -> Keys.SB_ACTION_FILLER
            "preview" -> Keys.SB_ACTION_PREVIEW
            "exclusive_access" -> Keys.SB_ACTION_EXCLUSIVE_ACCESS
            else -> Keys.SB_ACTION_SPONSOR
        }
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[key] = action.name
        }
    }

    // SponsorBlock per-category color preferences (stored as ARGB Int)
    fun sbColorForCategory(category: String): Flow<Int?> {
        val key = when (category) {
            "sponsor" -> Keys.SB_COLOR_SPONSOR
            "intro" -> Keys.SB_COLOR_INTRO
            "outro" -> Keys.SB_COLOR_OUTRO
            "selfpromo" -> Keys.SB_COLOR_SELFPROMO
            "interaction" -> Keys.SB_COLOR_INTERACTION
            "music_offtopic" -> Keys.SB_COLOR_MUSIC_OFFTOPIC
            "filler" -> Keys.SB_COLOR_FILLER
            "preview" -> Keys.SB_COLOR_PREVIEW
            "exclusive_access" -> Keys.SB_COLOR_EXCLUSIVE_ACCESS
            else -> Keys.SB_COLOR_SPONSOR
        }
        return context.playerPreferencesDataStore.data.map { prefs -> prefs[key] }
    }

    suspend fun setSbColorForCategory(category: String, colorArgb: Int?) {
        val key = when (category) {
            "sponsor" -> Keys.SB_COLOR_SPONSOR
            "intro" -> Keys.SB_COLOR_INTRO
            "outro" -> Keys.SB_COLOR_OUTRO
            "selfpromo" -> Keys.SB_COLOR_SELFPROMO
            "interaction" -> Keys.SB_COLOR_INTERACTION
            "music_offtopic" -> Keys.SB_COLOR_MUSIC_OFFTOPIC
            "filler" -> Keys.SB_COLOR_FILLER
            "preview" -> Keys.SB_COLOR_PREVIEW
            "exclusive_access" -> Keys.SB_COLOR_EXCLUSIVE_ACCESS
            else -> Keys.SB_COLOR_SPONSOR
        }
        context.playerPreferencesDataStore.edit { prefs ->
            if (colorArgb != null) prefs[key] = colorArgb else prefs.remove(key)
        }
    }

    // Flow for reading the stored SB User ID (may be null)
    val sbUserId: Flow<String?> = context.playerPreferencesDataStore.data
        .map { prefs -> prefs[Keys.SB_USER_ID]?.takeIf { it.isNotBlank() } }

    suspend fun setSbUserId(id: String) {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs[Keys.SB_USER_ID] = id
        }
    }

    val sbSubmitEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SB_SUBMIT_ENABLED] ?: false }

    suspend fun setSbSubmitEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SB_SUBMIT_ENABLED] = enabled
        }
    }

    /** Returns the stored SponsorBlock user ID, generating a new UUID if not set. */
    suspend fun getOrCreateSbUserId(): String {
        val prefs = context.playerPreferencesDataStore.data.first()
        val existing = prefs[Keys.SB_USER_ID]
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString().replace("-", "")
        context.playerPreferencesDataStore.edit { it[Keys.SB_USER_ID] = newId }
        return newId
    }

    // Slider Style preference
    val sliderStyle: Flow<SliderStyle> = context.playerPreferencesDataStore.data
        .map { preferences ->
            SliderStyle.valueOf(preferences[Keys.SLIDER_STYLE] ?: SliderStyle.DEFAULT.name)
        }

    suspend fun setSliderStyle(style: SliderStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SLIDER_STYLE] = style.name
        }
    }

    val squigglySliderEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SQUIGGLY_SLIDER_ENABLED] ?: false
        }

    suspend fun setSquigglySliderEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SQUIGGLY_SLIDER_ENABLED] = enabled
        }
    }

    // Shorts shelf enabled preference
    val shortsShelfEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHORTS_SHELF_ENABLED] ?: true
        }

    suspend fun setShortsShelfEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_SHELF_ENABLED] = enabled
        }
    }

    // Home Shorts shelf enabled preference
    val homeShortsShelfEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.HOME_SHORTS_SHELF_ENABLED] ?: true
        }

    suspend fun setHomeShortsShelfEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.HOME_SHORTS_SHELF_ENABLED] = enabled
        }
    }

    // Shorts navigation enabled preference
    val shortsNavigationEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHORTS_NAVIGATION_ENABLED] ?: true
        }

    suspend fun setShortsNavigationEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_NAVIGATION_ENABLED] = enabled
        }
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

    // Shorts quality preferences (default to 720p WiFi, 480p Cellular)
    val shortsQualityWifi: Flow<VideoQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            VideoQuality.fromString(preferences[Keys.SHORTS_QUALITY_WIFI] ?: "720p")
        }

    val shortsQualityCellular: Flow<VideoQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            VideoQuality.fromString(preferences[Keys.SHORTS_QUALITY_CELLULAR] ?: "480p")
        }

    suspend fun setShortsQualityWifi(quality: VideoQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_QUALITY_WIFI] = quality.label
        }
    }

    suspend fun setShortsQualityCellular(quality: VideoQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_QUALITY_CELLULAR] = quality.label
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

    // SponsorBlock
    val sponsorBlockEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SPONSOR_BLOCK_ENABLED] ?: false
        }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SPONSOR_BLOCK_ENABLED] = enabled
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
    
    // Audio Language Preference
    val preferredAudioLanguage: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PREFERRED_AUDIO_LANGUAGE] ?: "original" // Default to original/native
        }
    
    suspend fun setPreferredAudioLanguage(language: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PREFERRED_AUDIO_LANGUAGE] = language
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
            false // Forced OFF to prevent whole-app PiP bug
            // preferences[Keys.AUTO_PIP_ENABLED] ?: false // Default OFF
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

    // Buffer Preferences - Optimized for fast startup while maintaining stability
    // These are the defaults that balance quick playback start with smooth streaming
    val minBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MIN_BUFFER_MS] ?: 15_000 // 15s - reduced from 25s for faster start
        }

    suspend fun setMinBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MIN_BUFFER_MS] = ms
        }
    }

    val maxBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MAX_BUFFER_MS] ?: 50_000 // 50s - reduced from 80s, still plenty for seeking
        }

    suspend fun setMaxBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MAX_BUFFER_MS] = ms
        }
    }

    val bufferForPlaybackMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] ?: 1_000 // 1s - start playback ASAP (from 1.5s)
        }

    suspend fun setBufferForPlaybackMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] = ms
        }
    }
    
    val bufferForPlaybackAfterRebufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] ?: 2_500 // 2.5s - reduced from 4s for faster resume
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

    
    // Download Preferences
    val downloadThreads: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DOWNLOAD_THREADS] ?: 3
        }

    suspend fun setDownloadThreads(threads: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_THREADS] = threads
        }
    }

    val parallelDownloadEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PARALLEL_DOWNLOAD_ENABLED] ?: true
        }

    suspend fun setParallelDownloadEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PARALLEL_DOWNLOAD_ENABLED] = enabled
        }
    }

    val downloadOverWifiOnly: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DOWNLOAD_OVER_WIFI_ONLY] ?: false
        }

    suspend fun setDownloadOverWifiOnly(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_OVER_WIFI_ONLY] = enabled
        }
    }

    val defaultDownloadQuality: Flow<VideoQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            VideoQuality.fromString(preferences[Keys.DEFAULT_DOWNLOAD_QUALITY] ?: "720p")
        }

    suspend fun setDefaultDownloadQuality(quality: VideoQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEFAULT_DOWNLOAD_QUALITY] = quality.label
        }
    }

    /** Custom download directory path (null = default Movies/Flow or Music/Flow) */
    val downloadLocation: Flow<String?> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DOWNLOAD_LOCATION]
        }

    suspend fun setDownloadLocation(path: String?) {
        context.playerPreferencesDataStore.edit { preferences ->
            if (path != null) {
                preferences[Keys.DOWNLOAD_LOCATION] = path
            } else {
                preferences.remove(Keys.DOWNLOAD_LOCATION)
            }
        }
    }

    // Surface timeout
    val surfaceReadyTimeoutMs: Flow<Long> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SURFACE_READY_TIMEOUT_MS] ?: 1500L // Default 1.5s
        }

    suspend fun setSurfaceReadyTimeoutMs(timeoutMs: Long) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SURFACE_READY_TIMEOUT_MS] = timeoutMs
        }
    }

    // Lyrics Provider preference
    val preferredLyricsProvider: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PREFERRED_LYRICS_PROVIDER] ?: "LRCLIB"
        }

    suspend fun setPreferredLyricsProvider(provider: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PREFERRED_LYRICS_PROVIDER] = provider
        }
    }
}

/** Action to take when a SponsorBlock segment is encountered. */
enum class SponsorBlockAction(val displayName: String) {
    SKIP("Skip"),
    MUTE("Mute"),
    SHOW_TOAST("Notify only"),
    IGNORE("Ignore");

    companion object {
        fun fromString(name: String): SponsorBlockAction =
            values().find { it.name == name } ?: SKIP
    }
}

enum class BufferProfile(
    val label: String,
    val minBuffer: Int,
    val maxBuffer: Int,
    val playbackBuffer: Int,
    val rebufferBuffer: Int
) {
    // Fast Start: Prioritize quick playback start over buffer stability
    AGGRESSIVE("Fast Start", 10_000, 30_000, 500, 1_500),      
    // Balanced: Good default for most connections
    STABLE("Balanced", 15_000, 50_000, 1_000, 2_500),        
    // Data Saver: Minimize data usage with smaller buffers
    DATASAVER("Data Saver", 12_000, 25_000, 1_500, 3_000),                   
    // Custom: User-defined values
    CUSTOM("Custom", -1, -1, -1, -1);                                    

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

enum class SliderStyle {
    DEFAULT,
    METROLIST,      
    METROLIST_SLIM, 
    SQUIGGLY,
    SLIM         
}


