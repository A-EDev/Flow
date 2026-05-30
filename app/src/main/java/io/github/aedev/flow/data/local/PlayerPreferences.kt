package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.aedev.flow.network.AppProxyConfig
import io.github.aedev.flow.network.AppProxyType
import io.github.aedev.flow.ui.components.SubtitleStyle
import io.github.aedev.flow.utils.DateContextMode
import io.github.aedev.flow.utils.DateDisplayMode
import io.github.aedev.flow.utils.DateFormatStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_preferences")

const val DEEP_FLOW_NEVER_EXPIRES_HOURS = 0
const val DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP = 48
const val MAX_FULLSCREEN_SEEKBAR_PADDING_DP = 120
val DEFAULT_NAV_TAB_ORDER = listOf(0, 1, 2, 3, 4, 5, 6)

class PlayerPreferences(context: Context) {
    private val context: Context = context.applicationContext
    
    private object Keys {
        val DEFAULT_QUALITY_WIFI = stringPreferencesKey("default_quality_wifi")
        val DEFAULT_QUALITY_CELLULAR = stringPreferencesKey("default_quality_cellular")
        val BACKGROUND_PLAY_ENABLED = booleanPreferencesKey("background_play_enabled")
        val AUTOPLAY_ENABLED = booleanPreferencesKey("autoplay_enabled")
        val VIDEO_LOOP_ENABLED = booleanPreferencesKey("video_loop_enabled")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
        val SUBTITLE_FONT_SIZE = floatPreferencesKey("subtitle_font_size")
        val SUBTITLE_TEXT_COLOR = intPreferencesKey("subtitle_text_color")
        val SUBTITLE_BACKGROUND_COLOR = intPreferencesKey("subtitle_background_color")
        val SUBTITLE_BOLD = booleanPreferencesKey("subtitle_bold")
        val SUBTITLE_BOTTOM_PADDING = floatPreferencesKey("subtitle_bottom_padding")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val TRENDING_REGION = stringPreferencesKey("trending_region")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SKIP_SILENCE_ENABLED = booleanPreferencesKey("skip_silence_enabled")        
        val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")        
        val AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
        val MANUAL_PIP_BUTTON_ENABLED = booleanPreferencesKey("manual_pip_button_enabled")
        val STABLE_VOLUME_ENABLED = booleanPreferencesKey("stable_volume_enabled")
        
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
        val MUSIC_DOWNLOAD_LOCATION = stringPreferencesKey("music_download_location")

        // Download dialog style + remembered last-used download options (compact dialog)
        val DOWNLOAD_DIALOG_STYLE = stringPreferencesKey("download_dialog_style")
        val LAST_DOWNLOAD_TYPE = stringPreferencesKey("last_download_type")
        val LAST_DOWNLOAD_HEIGHT = intPreferencesKey("last_download_height")
        val LAST_DOWNLOAD_CODEC = stringPreferencesKey("last_download_codec")
        val LAST_DOWNLOAD_AUDIO_LABEL = stringPreferencesKey("last_download_audio_label")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val SURFACE_READY_TIMEOUT_MS = longPreferencesKey("surface_ready_timeout_ms")
        
        // Audio track preference
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
        val MUSIC_AUDIO_QUALITY = stringPreferencesKey("music_audio_quality")

        // Shorts quality preferences
        val SHORTS_QUALITY_WIFI = stringPreferencesKey("shorts_quality_wifi")
        val SHORTS_QUALITY_CELLULAR = stringPreferencesKey("shorts_quality_cellular")
        
        // UI preferences
        val GRID_ITEM_SIZE = stringPreferencesKey("grid_item_size")
        val SLIDER_STYLE = stringPreferencesKey("slider_style")
        val MUSIC_PLAYER_BACKGROUND_STYLE = stringPreferencesKey("music_player_background_style")
        val SQUIGGLY_SLIDER_ENABLED = booleanPreferencesKey("squiggly_slider_enabled")
        val SHORTS_SHELF_ENABLED = booleanPreferencesKey("shorts_shelf_enabled")
        val HOME_SHORTS_SHELF_ENABLED = booleanPreferencesKey("home_shorts_shelf_enabled")
        val SHORTS_NAVIGATION_ENABLED = booleanPreferencesKey("shorts_navigation_enabled")
        val MUSIC_NAVIGATION_ENABLED = booleanPreferencesKey("music_navigation_enabled")
        val SEARCH_NAV_TAB_ENABLED = booleanPreferencesKey("search_nav_tab_enabled")
        val CATEGORIES_NAV_TAB_ENABLED = booleanPreferencesKey("categories_nav_tab_enabled")
        val PREFERRED_LYRICS_PROVIDER = stringPreferencesKey("preferred_lyrics_provider")
        val LYRICS_PROVIDER_ORDER = stringPreferencesKey("lyrics_provider_order")
        val LYRICS_PROVIDER_ENABLED_BETTERLYRICS = booleanPreferencesKey("lyrics_provider_enabled_betterlyrics")
        val LYRICS_PROVIDER_ENABLED_SIMPMUSIC = booleanPreferencesKey("lyrics_provider_enabled_simpmusic")
        val LYRICS_PROVIDER_ENABLED_LYRICSPLUS = booleanPreferencesKey("lyrics_provider_enabled_lyricsplus")
        val LYRICS_PROVIDER_ENABLED_LRCLIB = booleanPreferencesKey("lyrics_provider_enabled_lrclib")
        val LYRICS_PROVIDER_ENABLED_YOUTUBE = booleanPreferencesKey("lyrics_provider_enabled_youtube")
        val LYRICS_PROVIDER_ENABLED_KUGOU = booleanPreferencesKey("lyrics_provider_enabled_kugou")
        val LYRICS_PROVIDER_ENABLED_PAXSENIX = booleanPreferencesKey("lyrics_provider_enabled_paxsenix")
        val LYRICS_PROVIDER_ENABLED_YOUTUBESUBTITLE = booleanPreferencesKey("lyrics_provider_enabled_youtubesubtitle")
        val SWIPE_GESTURES_ENABLED = booleanPreferencesKey("swipe_gestures_enabled")
        val BRIGHTNESS_SWIPE_GESTURES_ENABLED = booleanPreferencesKey("brightness_swipe_gestures_enabled")
        val REMEMBER_BRIGHTNESS_ENABLED = booleanPreferencesKey("remember_brightness_enabled")
        val REMEMBERED_BRIGHTNESS_LEVEL = floatPreferencesKey("remembered_brightness_level")
        val VOLUME_SWIPE_GESTURES_ENABLED = booleanPreferencesKey("volume_swipe_gestures_enabled")
        val CONTINUE_WATCHING_ENABLED = booleanPreferencesKey("continue_watching_enabled")
        val SHOW_RELATED_VIDEOS = booleanPreferencesKey("show_related_videos")
        val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
        val HOME_VIEW_MODE = stringPreferencesKey("home_view_mode")
        val HOME_FEED_ENABLED = booleanPreferencesKey("home_feed_enabled")
        val RELATED_CARD_STYLE = stringPreferencesKey("related_card_style")

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

        // DeArrow
        val DEARROW_ENABLED = booleanPreferencesKey("dearrow_enabled")
        val DEARROW_BADGE_ENABLED = booleanPreferencesKey("dearrow_badge_enabled")

        // Notification preferences
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIF_NEW_VIDEOS_ENABLED = booleanPreferencesKey("notif_new_videos_enabled")
        val NOTIF_DOWNLOADS_ENABLED = booleanPreferencesKey("notif_downloads_enabled")
        val NOTIF_REMINDERS_ENABLED = booleanPreferencesKey("notif_reminders_enabled")
        val NOTIF_UPDATES_ENABLED = booleanPreferencesKey("notif_updates_enabled")
        val NOTIF_GENERAL_ENABLED = booleanPreferencesKey("notif_general_enabled")
        
        // Overlay Controls preferences
        val OVERLAY_CAST_ENABLED = booleanPreferencesKey("overlay_cast_enabled")
        val OVERLAY_CC_ENABLED = booleanPreferencesKey("overlay_cc_enabled")
        val OVERLAY_PIP_ENABLED = booleanPreferencesKey("overlay_pip_enabled")
        val OVERLAY_AUTOPLAY_ENABLED = booleanPreferencesKey("overlay_autoplay_enabled")
        val OVERLAY_SLEEPTIMER_ENABLED = booleanPreferencesKey("overlay_sleeptimer_enabled")
        val OVERLAY_LOCK_MODE_ENABLED = booleanPreferencesKey("overlay_lock_mode_enabled")
        val OVERLAY_SPEED_INDICATOR_ENABLED = booleanPreferencesKey("overlay_speed_indicator_enabled")
        
        // Fullscreen Player
        val SHOW_FULLSCREEN_TITLE = booleanPreferencesKey("show_fullscreen_title")
        val ADAPTIVE_PLAYER_SIZE_ENABLED = booleanPreferencesKey("adaptive_player_size_enabled")
        val FULLSCREEN_SEEKBAR_PADDING_MODE = stringPreferencesKey("fullscreen_seekbar_padding_mode")
        val FULLSCREEN_SEEKBAR_CUSTOM_PADDING_DP = intPreferencesKey("fullscreen_seekbar_custom_padding_dp")
        
        // Mini Player Customizations
        val MINI_PLAYER_SCALE = floatPreferencesKey("mini_player_scale")
        val MINI_PLAYER_SHOW_SKIP_CONTROLS = booleanPreferencesKey("mini_player_show_skip_controls")
        val MINI_PLAYER_SHOW_NEXT_PREV_CONTROLS = booleanPreferencesKey("mini_player_show_next_prev_controls")
        val MINI_PLAYER_CONTINUE_WATCHING_ENABLED = booleanPreferencesKey("mini_player_continue_watching_enabled")

        // Audio focus during calls
        val PLAY_DURING_CALLS = booleanPreferencesKey("play_during_calls")

        // Subscriptions feed view mode
        val SUBS_FULL_WIDTH_VIEW = booleanPreferencesKey("subs_full_width_view")
        val SUBS_SELECTED_GROUP = stringPreferencesKey("subs_selected_group")
        val SUBS_REFRESH_ON_STARTUP = booleanPreferencesKey("subs_refresh_on_startup")
        val SUBS_LAST_REFRESH_TIME = longPreferencesKey("subs_last_refresh_time")
        val SUBS_LAST_REFRESHED_COUNT = intPreferencesKey("subs_last_refreshed_count")

        // Navigation tab preferences
        val NAV_TAB_ORDER = stringPreferencesKey("nav_tab_order")
        val DEFAULT_NAV_TAB_INDEX = intPreferencesKey("default_nav_tab_index")

        // Remember playback speed
        val REMEMBER_PLAYBACK_SPEED = booleanPreferencesKey("remember_playback_speed")

        // Subscription check interval
        val SUBSCRIPTION_CHECK_INTERVAL_MINUTES = intPreferencesKey("subscription_check_interval_minutes")

        // Custom playback speeds
        val CUSTOM_SPEEDS_ENABLED = booleanPreferencesKey("custom_speeds_enabled")
        val CUSTOM_SPEED_PRESETS = stringPreferencesKey("custom_speed_presets")
        val SPEED_SLIDER_ENABLED = booleanPreferencesKey("speed_slider_enabled")

        // Content filtering
        val HIDE_WATCHED_VIDEOS = booleanPreferencesKey("hide_watched_videos")
        val DISABLE_SHORTS_PLAYER = booleanPreferencesKey("disable_shorts_player")

        // Shorts background playback
        val SHORTS_BACKGROUND_PLAY = booleanPreferencesKey("shorts_background_play")

        // Shorts playback mode: "loop" (default), "auto_next", or "auto_interval"
        val SHORTS_PLAYBACK_MODE = stringPreferencesKey("shorts_playback_mode")
        val SHORTS_AUTO_SCROLL_SECONDS = intPreferencesKey("shorts_auto_scroll_seconds")

        // Cache size
        val MEDIA_CACHE_SIZE_MB = intPreferencesKey("media_cache_size_mb")

        // Explore screen quick region picker
        val SHOW_REGION_PICKER_IN_EXPLORE = booleanPreferencesKey("show_region_picker_in_explore")

        // App icon — stores the component suffix of the currently selected launcher icon
        val APP_ICON_SUFFIX = stringPreferencesKey("app_icon_suffix")
        val PLAYLIST_SORT_ORDER = stringPreferencesKey("playlist_sort_order")

        // Video title display — max lines in the player info section (0 = no limit)
        val VIDEO_TITLE_MAX_LINES = intPreferencesKey("video_title_max_lines")

        // Screen-level view mode toggles
        val SEARCH_IS_GRID_MODE = booleanPreferencesKey("search_is_grid_mode")
        val CHANNEL_IS_GRID_VIEW = booleanPreferencesKey("channel_is_grid_view")
        val CATEGORIES_IS_LIST_VIEW = booleanPreferencesKey("categories_is_list_view")

        // Video card inline like/dislike action buttons
        val VIDEO_CARD_ACTIONS_ENABLED = booleanPreferencesKey("video_card_actions_enabled")

        // Video card mark-as-watched quick actions
        val VIDEO_CARD_MARK_WATCHED_ENABLED = booleanPreferencesKey("video_card_mark_watched_enabled")

        // Show app logo icon in home screen top bar
        val SHOW_APP_LOGO_ICON = booleanPreferencesKey("show_app_logo_icon")

        // Player comments preview
        val COMMENTS_ENABLED = booleanPreferencesKey("comments_enabled")
        val COMMENTS_PREVIEW_ENABLED = booleanPreferencesKey("comments_preview_enabled")

        val SUBSCRIPTION_SHOW_VIDEOS = booleanPreferencesKey("subscription_show_videos")
        val SUBSCRIPTION_SHOW_SHORTS = booleanPreferencesKey("subscription_show_shorts")
        val SUBSCRIPTION_SHOW_LIVE = booleanPreferencesKey("subscription_show_live")
        val SUBSCRIPTION_SHORTS_EXCLUDED_CHANNELS = stringSetPreferencesKey("subscription_shorts_excluded_channels")
        val UPCOMING_VIDEO_REMINDER_IDS = stringSetPreferencesKey("upcoming_video_reminder_ids")

        // Deep Flow (Incognito / No-Engine) mode
        val DEEP_FLOW_ACTIVE = booleanPreferencesKey("deep_flow_active")
        val DEEP_FLOW_ACTIVATED_AT = longPreferencesKey("deep_flow_activated_at")
        val DEEP_FLOW_EXPIRE_HOURS = intPreferencesKey("deep_flow_expire_hours")

        // Home subscription feed rotation cursor
        val HOME_SUBS_ROTATION_CURSOR = intPreferencesKey("home_subs_rotation_cursor")

        // Auto-backup settings
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_FOLDER_URI = stringPreferencesKey("auto_backup_folder_uri")
        val AUTO_BACKUP_TYPE = stringPreferencesKey("auto_backup_type")

        // Return YouTube Dislikes
        val RYTD_ENABLED = booleanPreferencesKey("rytd_enabled")

        // Volume boost: opt-in, default off 
        val ALLOW_VOLUME_BOOST = booleanPreferencesKey("allow_volume_boost")

        // Shorts playback speed: remembered across sessions
        val SHORTS_PLAYBACK_SPEED = floatPreferencesKey("shorts_playback_speed")

        // Date & time display
        val DATE_DISPLAY_MODE = stringPreferencesKey("date_display_mode")
        val DATE_FORMAT_STYLE = stringPreferencesKey("date_format_style")
        val DATE_MODE_LISTS = stringPreferencesKey("date_mode_lists")
        val DATE_MODE_WATCH = stringPreferencesKey("date_mode_watch")
        val DATE_MODE_DESCRIPTION = stringPreferencesKey("date_mode_description")
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

    // ── Volume boost (#491): opt-in, default OFF ──
    val allowVolumeBoost: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.ALLOW_VOLUME_BOOST] ?: false
        }

    suspend fun setAllowVolumeBoost(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.ALLOW_VOLUME_BOOST] = enabled
        }
    }

    // ── Shorts playback speed (#496): remembered across sessions ──
    val shortsPlaybackSpeed: Flow<Float> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHORTS_PLAYBACK_SPEED] ?: 1.0f
        }

    suspend fun setShortsPlaybackSpeed(speed: Float) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_PLAYBACK_SPEED] = speed
        }
    }

    // ── Date & time display──
    val dateDisplayMode: Flow<DateDisplayMode> = context.playerPreferencesDataStore.data
        .map { preferences -> DateDisplayMode.fromString(preferences[Keys.DATE_DISPLAY_MODE]) }

    suspend fun setDateDisplayMode(mode: DateDisplayMode) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DATE_DISPLAY_MODE] = mode.name
        }
    }

    val dateFormatStyle: Flow<DateFormatStyle> = context.playerPreferencesDataStore.data
        .map { preferences -> DateFormatStyle.fromString(preferences[Keys.DATE_FORMAT_STYLE]) }

    suspend fun setDateFormatStyle(style: DateFormatStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DATE_FORMAT_STYLE] = style.name
        }
    }

    val dateModeLists: Flow<DateContextMode> = context.playerPreferencesDataStore.data
        .map { DateContextMode.fromString(it[Keys.DATE_MODE_LISTS]) }
    val dateModeWatch: Flow<DateContextMode> = context.playerPreferencesDataStore.data
        .map { DateContextMode.fromString(it[Keys.DATE_MODE_WATCH]) }
    val dateModeDescription: Flow<DateContextMode> = context.playerPreferencesDataStore.data
        .map { DateContextMode.fromString(it[Keys.DATE_MODE_DESCRIPTION]) }

    suspend fun setDateModeLists(mode: DateContextMode) {
        context.playerPreferencesDataStore.edit { it[Keys.DATE_MODE_LISTS] = mode.name }
    }
    suspend fun setDateModeWatch(mode: DateContextMode) {
        context.playerPreferencesDataStore.edit { it[Keys.DATE_MODE_WATCH] = mode.name }
    }
    suspend fun setDateModeDescription(mode: DateContextMode) {
        context.playerPreferencesDataStore.edit { it[Keys.DATE_MODE_DESCRIPTION] = mode.name }
    }

    // Swipe gestures (brightness/volume) enabled preference
    val swipeGesturesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SWIPE_GESTURES_ENABLED] ?: true
        }

    val brightnessSwipeGesturesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BRIGHTNESS_SWIPE_GESTURES_ENABLED]
                ?: preferences[Keys.SWIPE_GESTURES_ENABLED]
                ?: true
        }

    val rememberBrightnessEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.REMEMBER_BRIGHTNESS_ENABLED] ?: false
        }

    val rememberedBrightnessLevel: Flow<Float> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.REMEMBERED_BRIGHTNESS_LEVEL] ?: -1f
        }

    val volumeSwipeGesturesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.VOLUME_SWIPE_GESTURES_ENABLED]
                ?: preferences[Keys.SWIPE_GESTURES_ENABLED]
                ?: true
        }

    suspend fun setSwipeGesturesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SWIPE_GESTURES_ENABLED] = enabled
        }
    }

    suspend fun setBrightnessSwipeGesturesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BRIGHTNESS_SWIPE_GESTURES_ENABLED] = enabled
        }
    }

    suspend fun setRememberBrightnessEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.REMEMBER_BRIGHTNESS_ENABLED] = enabled
        }
    }

    suspend fun setRememberedBrightnessLevel(level: Float) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.REMEMBERED_BRIGHTNESS_LEVEL] = if (level < 0f) -1f else level.coerceIn(0f, 1f)
        }
    }

    suspend fun setVolumeSwipeGesturesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.VOLUME_SWIPE_GESTURES_ENABLED] = enabled
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

    val musicPlayerBackgroundStyle: Flow<MusicPlayerBackgroundStyle> = context.playerPreferencesDataStore.data
        .map { preferences ->
            runCatching {
                MusicPlayerBackgroundStyle.valueOf(
                    preferences[Keys.MUSIC_PLAYER_BACKGROUND_STYLE]
                        ?: MusicPlayerBackgroundStyle.BLUR_GRADIENT.name
                )
            }.getOrDefault(MusicPlayerBackgroundStyle.BLUR_GRADIENT)
        }

    suspend fun setMusicPlayerBackgroundStyle(style: MusicPlayerBackgroundStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MUSIC_PLAYER_BACKGROUND_STYLE] = style.name
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

    // Music navigation enabled preference
    val musicNavigationEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MUSIC_NAVIGATION_ENABLED] ?: true
        }

    suspend fun setMusicNavigationEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MUSIC_NAVIGATION_ENABLED] = enabled
        }
    }

    // Search nav tab enabled preference
    val searchNavigationEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SEARCH_NAV_TAB_ENABLED] ?: false
        }

    suspend fun setSearchNavigationEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SEARCH_NAV_TAB_ENABLED] = enabled
        }
    }

    // Categories nav tab enabled preference
    val categoriesNavigationEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.CATEGORIES_NAV_TAB_ENABLED] ?: false
        }

    suspend fun setCategoriesNavigationEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CATEGORIES_NAV_TAB_ENABLED] = enabled
        }
    }

    // Continue Watching shelf enabled preference
    val continueWatchingEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.CONTINUE_WATCHING_ENABLED] ?: true
        }

    suspend fun setContinueWatchingEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CONTINUE_WATCHING_ENABLED] = enabled
        }
    }

    // Show related videos preference
    val showRelatedVideos: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHOW_RELATED_VIDEOS] ?: true
        }

    suspend fun setShowRelatedVideos(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHOW_RELATED_VIDEOS] = enabled
        }
    }

    // Double-tap seek duration preference (default 10 seconds)
    val doubleTapSeekSeconds: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DOUBLE_TAP_SEEK_SECONDS] ?: 10
        }

    suspend fun setDoubleTapSeekSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DOUBLE_TAP_SEEK_SECONDS] = seconds
        }
    }

    // Home view mode preference
    val homeViewMode: Flow<HomeViewMode> = context.playerPreferencesDataStore.data
        .map { preferences ->
            HomeViewMode.valueOf(preferences[Keys.HOME_VIEW_MODE] ?: HomeViewMode.GRID.name)
        }

    suspend fun setHomeViewMode(mode: HomeViewMode) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.HOME_VIEW_MODE] = mode.name
        }
    }

    // Home feed enabled preference
    val homeFeedEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.HOME_FEED_ENABLED] ?: true
        }

    suspend fun setHomeFeedEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.HOME_FEED_ENABLED] = enabled
        }
    }

    // Home subscription rotation cursor
    val homeSubsRotationCursor: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.HOME_SUBS_ROTATION_CURSOR] ?: 0
        }

    suspend fun setHomeSubsRotationCursor(cursor: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.HOME_SUBS_ROTATION_CURSOR] = cursor.coerceAtLeast(0)
        }
    }

    // Related video card style preference (tablet/player panel)
    val playerRelatedCardStyle: Flow<PlayerRelatedCardStyle> = context.playerPreferencesDataStore.data
        .map { preferences ->
            try {
                PlayerRelatedCardStyle.valueOf(preferences[Keys.RELATED_CARD_STYLE] ?: PlayerRelatedCardStyle.FULL_WIDTH.name)
            } catch (_: IllegalArgumentException) {
                PlayerRelatedCardStyle.FULL_WIDTH
            }
        }

    suspend fun setPlayerRelatedCardStyle(style: PlayerRelatedCardStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.RELATED_CARD_STYLE] = style.name
        }
    }
    val trendingRegion: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.TRENDING_REGION] ?: "US"
        }
    
    suspend fun setTrendingRegion(region: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.TRENDING_REGION] = region
        }
    }

    val appLanguage: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.APP_LANGUAGE] ?: "system"
        }

    suspend fun setAppLanguage(languageTag: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.APP_LANGUAGE] = languageTag
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

    val musicAudioQuality: Flow<MusicAudioQuality> = context.playerPreferencesDataStore.data
        .map { preferences ->
            MusicAudioQuality.fromString(preferences[Keys.MUSIC_AUDIO_QUALITY] ?: MusicAudioQuality.AUTO.label)
        }

    suspend fun setMusicAudioQuality(quality: MusicAudioQuality) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MUSIC_AUDIO_QUALITY] = quality.label
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

    // Video Loop
    val videoLoopEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.VIDEO_LOOP_ENABLED] ?: false
        }
    
    suspend fun setVideoLoopEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.VIDEO_LOOP_ENABLED] = enabled
            if (enabled) {
                preferences[Keys.AUTOPLAY_ENABLED] = false
            }
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

    // Stable Volume
    val stableVolumeEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.STABLE_VOLUME_ENABLED] ?: false
        }

    suspend fun setStableVolumeEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.STABLE_VOLUME_ENABLED] = enabled
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

    // DeArrow
    val deArrowEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DEARROW_ENABLED] ?: false
        }

    suspend fun setDeArrowEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEARROW_ENABLED] = enabled
        }
    }

    val deArrowBadgeEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.DEARROW_BADGE_ENABLED] ?: false }

    suspend fun setDeArrowBadgeEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEARROW_BADGE_ENABLED] = enabled
        }
    }

    // ========== NOTIFICATION PREFERENCES ==========

    val notificationsEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val notifNewVideosEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIF_NEW_VIDEOS_ENABLED] ?: true }

    suspend fun setNotifNewVideosEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIF_NEW_VIDEOS_ENABLED] = enabled
        }
    }

    val notifDownloadsEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIF_DOWNLOADS_ENABLED] ?: true }

    suspend fun setNotifDownloadsEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIF_DOWNLOADS_ENABLED] = enabled
        }
    }

    val notifRemindersEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIF_REMINDERS_ENABLED] ?: true }

    suspend fun setNotifRemindersEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIF_REMINDERS_ENABLED] = enabled
        }
    }

    val notifUpdatesEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIF_UPDATES_ENABLED] ?: true }

    suspend fun setNotifUpdatesEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIF_UPDATES_ENABLED] = enabled
        }
    }

    val notifGeneralEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.NOTIF_GENERAL_ENABLED] ?: true }

    suspend fun setNotifGeneralEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NOTIF_GENERAL_ENABLED] = enabled
        }
    }
    
    // ========== OVERLAY CONTROLS PREFERENCES ==========

    val overlayCastEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_CAST_ENABLED] ?: true }

    suspend fun setOverlayCastEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_CAST_ENABLED] = enabled
        }
    }
    
    val overlayCcEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_CC_ENABLED] ?: false }

    suspend fun setOverlayCcEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_CC_ENABLED] = enabled
        }
    }
    
    val overlayPipEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_PIP_ENABLED] ?: false }

    suspend fun setOverlayPipEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_PIP_ENABLED] = enabled
        }
    }

    val overlayAutoplayEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_AUTOPLAY_ENABLED] ?: false }

    suspend fun setOverlayAutoplayEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_AUTOPLAY_ENABLED] = enabled
        }
    }
    
    val overlaySleepTimerEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_SLEEPTIMER_ENABLED] ?: true }

    suspend fun setOverlaySleepTimerEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_SLEEPTIMER_ENABLED] = enabled
        }
    }

    val overlayLockModeEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_LOCK_MODE_ENABLED] ?: false }

    suspend fun setOverlayLockModeEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_LOCK_MODE_ENABLED] = enabled
        }
    }

    val overlaySpeedIndicatorEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.OVERLAY_SPEED_INDICATOR_ENABLED] ?: false }

    suspend fun setOverlaySpeedIndicatorEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_SPEED_INDICATOR_ENABLED] = enabled
        }
    }

    //  FULLSCREEN PLAYER PREFERENCES
    val showFullscreenTitle: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SHOW_FULLSCREEN_TITLE] ?: false }

    suspend fun setShowFullscreenTitle(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHOW_FULLSCREEN_TITLE] = enabled
        }
    }

    val adaptivePlayerSizeEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.ADAPTIVE_PLAYER_SIZE_ENABLED] ?: true }

    suspend fun setAdaptivePlayerSizeEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.ADAPTIVE_PLAYER_SIZE_ENABLED] = enabled
        }
    }

    val fullscreenSeekbarPaddingMode: Flow<FullscreenSeekbarPaddingMode> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.FULLSCREEN_SEEKBAR_PADDING_MODE]
                ?.let { storedMode -> runCatching { FullscreenSeekbarPaddingMode.valueOf(storedMode) }.getOrNull() }
                ?: FullscreenSeekbarPaddingMode.DEFAULT
        }

    suspend fun setFullscreenSeekbarPaddingMode(mode: FullscreenSeekbarPaddingMode) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.FULLSCREEN_SEEKBAR_PADDING_MODE] = mode.name
        }
    }

    val fullscreenSeekbarCustomPaddingDp: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            (preferences[Keys.FULLSCREEN_SEEKBAR_CUSTOM_PADDING_DP] ?: DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP)
                .coerceIn(0, MAX_FULLSCREEN_SEEKBAR_PADDING_DP)
        }

    suspend fun setFullscreenSeekbarCustomPaddingDp(paddingDp: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.FULLSCREEN_SEEKBAR_CUSTOM_PADDING_DP] =
                paddingDp.coerceIn(0, MAX_FULLSCREEN_SEEKBAR_PADDING_DP)
        }
    }

    val fullscreenSeekbarHorizontalPaddingDp: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            val mode = preferences[Keys.FULLSCREEN_SEEKBAR_PADDING_MODE]
                ?.let { storedMode -> runCatching { FullscreenSeekbarPaddingMode.valueOf(storedMode) }.getOrNull() }
                ?: FullscreenSeekbarPaddingMode.DEFAULT
            val customPadding = (preferences[Keys.FULLSCREEN_SEEKBAR_CUSTOM_PADDING_DP] ?: DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP)
                .coerceIn(0, MAX_FULLSCREEN_SEEKBAR_PADDING_DP)

            when (mode) {
                FullscreenSeekbarPaddingMode.FULL_WIDTH -> 0
                FullscreenSeekbarPaddingMode.DEFAULT -> DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
                FullscreenSeekbarPaddingMode.CUSTOM -> customPadding
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

    val subtitleStyle: Flow<SubtitleStyle> = context.playerPreferencesDataStore.data
        .map { preferences ->
            SubtitleStyle(
                fontSize = preferences[Keys.SUBTITLE_FONT_SIZE] ?: 14f,
                textColor = Color(preferences[Keys.SUBTITLE_TEXT_COLOR] ?: Color.White.toArgb()),
                backgroundColor = Color(
                    preferences[Keys.SUBTITLE_BACKGROUND_COLOR]
                        ?: Color.Black.copy(alpha = 0.6f).toArgb()
                ),
                isBold = preferences[Keys.SUBTITLE_BOLD] ?: true,
                bottomPadding = preferences[Keys.SUBTITLE_BOTTOM_PADDING] ?: 48f
            )
        }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBTITLE_FONT_SIZE] = style.fontSize
            preferences[Keys.SUBTITLE_TEXT_COLOR] = style.textColor.toArgb()
            preferences[Keys.SUBTITLE_BACKGROUND_COLOR] = style.backgroundColor.toArgb()
            preferences[Keys.SUBTITLE_BOLD] = style.isBold
            preferences[Keys.SUBTITLE_BOTTOM_PADDING] = style.bottomPadding
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
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            context.playerPreferencesDataStore.edit { preferences ->
                preferences[Keys.PLAYBACK_SPEED] = speed
            }
        }
    }

    // Remember playback speed
    val rememberPlaybackSpeed: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.REMEMBER_PLAYBACK_SPEED] ?: false
        }

    suspend fun setRememberPlaybackSpeed(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.REMEMBER_PLAYBACK_SPEED] = enabled
        }
    }

    // Subscription check interval (default: 360 minutes / 6 hours)
    val subscriptionCheckIntervalMinutes: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SUBSCRIPTION_CHECK_INTERVAL_MINUTES] ?: 360
        }

    suspend fun setSubscriptionCheckIntervalMinutes(minutes: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_CHECK_INTERVAL_MINUTES] = minutes
        }
    }

    // Custom playback speeds
    val customSpeedsEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.CUSTOM_SPEEDS_ENABLED] ?: false
        }

    suspend fun setCustomSpeedsEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CUSTOM_SPEEDS_ENABLED] = enabled
        }
    }

    val customSpeedPresets: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.CUSTOM_SPEED_PRESETS] ?: ""
        }

    suspend fun setCustomSpeedPresets(presets: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CUSTOM_SPEED_PRESETS] = presets
        }
    }

    val speedSliderEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SPEED_SLIDER_ENABLED] ?: false
        }

    suspend fun setSpeedSliderEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SPEED_SLIDER_ENABLED] = enabled
        }
    }

    // Subscriptions feed view mode
    val subsFullWidthView: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SUBS_FULL_WIDTH_VIEW] ?: false
        }

    suspend fun setSubsFullWidthView(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBS_FULL_WIDTH_VIEW] = enabled
        }
    }

    val selectedSubscriptionGroup: Flow<String?> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBS_SELECTED_GROUP]?.takeIf { it.isNotBlank() } }

    suspend fun setSelectedSubscriptionGroup(groupName: String?) {
        context.playerPreferencesDataStore.edit { preferences ->
            if (groupName.isNullOrBlank()) {
                preferences.remove(Keys.SUBS_SELECTED_GROUP)
            } else {
                preferences[Keys.SUBS_SELECTED_GROUP] = groupName
            }
        }
    }

    val subscriptionRefreshOnStartup: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBS_REFRESH_ON_STARTUP] ?: false }

    suspend fun setSubscriptionRefreshOnStartup(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBS_REFRESH_ON_STARTUP] = enabled
        }
    }

    val subscriptionLastRefreshTime: Flow<Long> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBS_LAST_REFRESH_TIME] ?: 0L }

    val subscriptionLastRefreshedCount: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBS_LAST_REFRESHED_COUNT] ?: 0 }

    suspend fun setSubscriptionLastRefresh(timeMillis: Long, count: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBS_LAST_REFRESH_TIME] = timeMillis
            preferences[Keys.SUBS_LAST_REFRESHED_COUNT] = count
        }
    }

    val navTabOrder: Flow<List<Int>> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.NAV_TAB_ORDER]
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_NAV_TAB_ORDER
        }

    suspend fun setNavTabOrder(order: List<Int>) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.NAV_TAB_ORDER] = order.distinct().joinToString(",")
        }
    }

    val defaultNavTabIndex: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.DEFAULT_NAV_TAB_INDEX] ?: 0 }

    suspend fun setDefaultNavTabIndex(index: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEFAULT_NAV_TAB_INDEX] = index
        }
    }

    val commentsPreviewEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.COMMENTS_PREVIEW_ENABLED] ?: true }

    suspend fun setCommentsPreviewEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.COMMENTS_PREVIEW_ENABLED] = enabled
        }
    }

    val commentsEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.COMMENTS_ENABLED] ?: true }

    suspend fun setCommentsEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.COMMENTS_ENABLED] = enabled
        }
    }

    val subscriptionShowVideos: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBSCRIPTION_SHOW_VIDEOS] ?: true }

    suspend fun setSubscriptionShowVideos(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_SHOW_VIDEOS] = enabled
        }
    }

    val subscriptionShowShorts: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBSCRIPTION_SHOW_SHORTS] ?: true }

    suspend fun setSubscriptionShowShorts(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_SHOW_SHORTS] = enabled
        }
    }

    val subscriptionShowLive: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SUBSCRIPTION_SHOW_LIVE] ?: true }

    suspend fun setSubscriptionShowLive(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_SHOW_LIVE] = enabled
        }
    }

    // PiP Preferences
    val autoPipEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.AUTO_PIP_ENABLED] ?: false
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

    // Content filtering
    val hideWatchedVideos: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.HIDE_WATCHED_VIDEOS] ?: false
        }

    suspend fun setHideWatchedVideos(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.HIDE_WATCHED_VIDEOS] = enabled
        }
    }

    val disableShortsPlayer: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.DISABLE_SHORTS_PLAYER] ?: false
        }

    suspend fun setDisableShortsPlayer(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DISABLE_SHORTS_PLAYER] = enabled
        }
    }

    // Shorts background playback (default OFF — pauses when app goes to background)
    val shortsBackgroundPlay: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHORTS_BACKGROUND_PLAY] ?: false
        }

    suspend fun setShortsBackgroundPlay(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_BACKGROUND_PLAY] = enabled
        }
    }

    // Shorts playback mode (default LOOP — repeats the current short)
    val shortsPlaybackMode: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHORTS_PLAYBACK_MODE] ?: "loop"
        }

    suspend fun setShortsPlaybackMode(mode: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_PLAYBACK_MODE] = mode
        }
    }

    val shortsAutoScrollSeconds: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            (preferences[Keys.SHORTS_AUTO_SCROLL_SECONDS] ?: 10).coerceIn(5, 20)
        }

    suspend fun setShortsAutoScrollSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHORTS_AUTO_SCROLL_SECONDS] = seconds.coerceIn(5, 20)
        }
    }

    val subscriptionShortsExcludedChannels: Flow<Set<String>> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SUBSCRIPTION_SHORTS_EXCLUDED_CHANNELS].orEmpty()
        }

    suspend fun setSubscriptionShortsChannelExcluded(channelId: String, excluded: Boolean) {
        if (channelId.isBlank()) return
        context.playerPreferencesDataStore.edit { preferences ->
            val current = preferences[Keys.SUBSCRIPTION_SHORTS_EXCLUDED_CHANNELS].orEmpty()
            preferences[Keys.SUBSCRIPTION_SHORTS_EXCLUDED_CHANNELS] =
                if (excluded) current + channelId else current - channelId
        }
    }

    val upcomingVideoReminderIds: Flow<Set<String>> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.UPCOMING_VIDEO_REMINDER_IDS].orEmpty()
        }

    suspend fun setUpcomingVideoReminder(videoId: String, enabled: Boolean) {
        if (videoId.isBlank()) return
        context.playerPreferencesDataStore.edit { preferences ->
            val current = preferences[Keys.UPCOMING_VIDEO_REMINDER_IDS].orEmpty()
            preferences[Keys.UPCOMING_VIDEO_REMINDER_IDS] =
                if (enabled) current + videoId else current - videoId
        }
    }

    // Cache size — 0 means unlimited. Default 500 MB.
    val mediaCacheSizeMb: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MEDIA_CACHE_SIZE_MB] ?: 500
        }

    suspend fun setMediaCacheSizeMb(sizeMb: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MEDIA_CACHE_SIZE_MB] = sizeMb
        }
    }

    // Show region picker globe icon in CategoriesScreen top bar
    val showRegionPickerInExplore: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.SHOW_REGION_PICKER_IN_EXPLORE] ?: true
        }

    suspend fun setShowRegionPickerInExplore(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHOW_REGION_PICKER_IN_EXPLORE] = enabled
        }
    }

    // Selected app icon — component suffix string saved on each icon switch so it can be backed up/restored
    val selectedAppIcon: Flow<String?> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.APP_ICON_SUFFIX]
        }

    suspend fun setSelectedAppIcon(suffix: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.APP_ICON_SUFFIX] = suffix
        }
    }

    // Video title max lines in the player info section — 0 means no limit (Int.MAX_VALUE)
    val playlistSortOrder: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PLAYLIST_SORT_ORDER] ?: "manual"
        }

    suspend fun setPlaylistSortOrder(order: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PLAYLIST_SORT_ORDER] = order
        }
    }

    val videoTitleMaxLines: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.VIDEO_TITLE_MAX_LINES] ?: 1
        }

    suspend fun setVideoTitleMaxLines(lines: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.VIDEO_TITLE_MAX_LINES] = lines
        }
    }

    // Video card inline like/dislike action buttons (default off)
    val videoCardActionsEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.VIDEO_CARD_ACTIONS_ENABLED] ?: false }

    suspend fun setVideoCardActionsEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.VIDEO_CARD_ACTIONS_ENABLED] = enabled
        }
    }

    // Video card inline mark-as-watched action controls (default off)
    val videoCardMarkWatchedEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.VIDEO_CARD_MARK_WATCHED_ENABLED] ?: false }

    suspend fun setVideoCardMarkWatchedEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.VIDEO_CARD_MARK_WATCHED_ENABLED] = enabled
        }
    }

    // Show app logo icon in home screen top bar (default on)
    val showAppLogoIcon: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SHOW_APP_LOGO_ICON] ?: true }

    suspend fun setShowAppLogoIcon(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SHOW_APP_LOGO_ICON] = enabled
        }
    }

    // Screen-level view mode toggles
    val searchIsGridMode: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.SEARCH_IS_GRID_MODE] ?: false }

    suspend fun setSearchIsGridMode(isGrid: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.SEARCH_IS_GRID_MODE] = isGrid
        }
    }

    val channelIsGridView: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.CHANNEL_IS_GRID_VIEW] ?: false }

    suspend fun setChannelIsGridView(isGrid: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CHANNEL_IS_GRID_VIEW] = isGrid
        }
    }

    val categoriesIsListView: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.CATEGORIES_IS_LIST_VIEW] ?: false }

    suspend fun setCategoriesIsListView(isList: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.CATEGORIES_IS_LIST_VIEW] = isList
        }
    }

    // Buffer Preferences - Optimized for fast startup while maintaining stability
    // These are the defaults that balance quick playback start with smooth streaming
    val minBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MIN_BUFFER_MS] ?: BufferProfile.STABLE.minBuffer
        }

    suspend fun setMinBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MIN_BUFFER_MS] = ms
        }
    }

    val maxBufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MAX_BUFFER_MS] ?: BufferProfile.STABLE.maxBuffer
        }

    suspend fun setMaxBufferMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MAX_BUFFER_MS] = ms
        }
    }

    val bufferForPlaybackMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] ?: BufferProfile.STABLE.playbackBuffer
        }

    suspend fun setBufferForPlaybackMs(ms: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_MS] = ms
        }
    }
    
    val bufferForPlaybackAfterRebufferMs: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] ?: BufferProfile.STABLE.rebufferBuffer
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

    // Download dialog style (Classic full dialog vs new Compact dialog)
    val downloadDialogStyle: Flow<DownloadDialogStyle> = context.playerPreferencesDataStore.data
        .map { preferences ->
            runCatching { DownloadDialogStyle.valueOf(preferences[Keys.DOWNLOAD_DIALOG_STYLE] ?: DownloadDialogStyle.FULL.name) }
                .getOrDefault(DownloadDialogStyle.FULL)
        }

    suspend fun setDownloadDialogStyle(style: DownloadDialogStyle) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_DIALOG_STYLE] = style.name
        }
    }

    // Remembered last-used download options (used by the compact dialog to preselect).
    val lastDownloadType: Flow<String?> = context.playerPreferencesDataStore.data
        .map { it[Keys.LAST_DOWNLOAD_TYPE] }
    val lastDownloadHeight: Flow<Int?> = context.playerPreferencesDataStore.data
        .map { it[Keys.LAST_DOWNLOAD_HEIGHT] }
    val lastDownloadCodec: Flow<String?> = context.playerPreferencesDataStore.data
        .map { it[Keys.LAST_DOWNLOAD_CODEC] }
    val lastDownloadAudioLabel: Flow<String?> = context.playerPreferencesDataStore.data
        .map { it[Keys.LAST_DOWNLOAD_AUDIO_LABEL] }

    suspend fun setLastDownloadVideoChoice(height: Int, codec: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.LAST_DOWNLOAD_TYPE] = "VIDEO"
            preferences[Keys.LAST_DOWNLOAD_HEIGHT] = height
            preferences[Keys.LAST_DOWNLOAD_CODEC] = codec
        }
    }

    suspend fun setLastDownloadAudioChoice(audioLabel: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.LAST_DOWNLOAD_TYPE] = "AUDIO"
            preferences[Keys.LAST_DOWNLOAD_AUDIO_LABEL] = audioLabel
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

    /** Custom music download directory path (null = use the video/global download location defaults) */
    val musicDownloadLocation: Flow<String?> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MUSIC_DOWNLOAD_LOCATION]
        }

    suspend fun setMusicDownloadLocation(path: String?) {
        context.playerPreferencesDataStore.edit { preferences ->
            if (path != null) {
                preferences[Keys.MUSIC_DOWNLOAD_LOCATION] = path
            } else {
                preferences.remove(Keys.MUSIC_DOWNLOAD_LOCATION)
            }
        }
    }

    val proxyEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PROXY_ENABLED] ?: false
        }

    suspend fun setProxyEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_ENABLED] = enabled
        }
    }

    val proxyType: Flow<AppProxyType> = context.playerPreferencesDataStore.data
        .map { preferences ->
            AppProxyType.fromStorageValue(preferences[Keys.PROXY_TYPE])
        }

    suspend fun setProxyType(type: AppProxyType) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_TYPE] = type.storageValue
        }
    }

    val proxyHost: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PROXY_HOST].orEmpty()
        }

    suspend fun setProxyHost(host: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_HOST] = host.trim()
        }
    }

    val proxyPort: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PROXY_PORT] ?: 8080
        }

    suspend fun setProxyPort(port: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_PORT] = port
        }
    }

    val proxyUsername: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PROXY_USERNAME].orEmpty()
        }

    suspend fun setProxyUsername(username: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_USERNAME] = username.trim()
        }
    }

    val proxyPassword: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PROXY_PASSWORD].orEmpty()
        }

    suspend fun setProxyPassword(password: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_PASSWORD] = password
        }
    }

    val proxyConfig: Flow<AppProxyConfig> = context.playerPreferencesDataStore.data
        .map { preferences ->
            AppProxyConfig(
                enabled = preferences[Keys.PROXY_ENABLED] ?: false,
                type = AppProxyType.fromStorageValue(preferences[Keys.PROXY_TYPE]),
                host = preferences[Keys.PROXY_HOST].orEmpty(),
                port = preferences[Keys.PROXY_PORT] ?: 8080,
                username = preferences[Keys.PROXY_USERNAME].orEmpty(),
                password = preferences[Keys.PROXY_PASSWORD].orEmpty()
            )
        }

    suspend fun getProxyConfig(): AppProxyConfig = proxyConfig.first()

    suspend fun setProxyConfig(config: AppProxyConfig) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PROXY_ENABLED] = config.enabled
            preferences[Keys.PROXY_TYPE] = config.type.storageValue
            preferences[Keys.PROXY_HOST] = config.host.trim()
            preferences[Keys.PROXY_PORT] = config.port
            preferences[Keys.PROXY_USERNAME] = config.username.trim()
            if (config.password.isEmpty()) {
                preferences.remove(Keys.PROXY_PASSWORD)
            } else {
                preferences[Keys.PROXY_PASSWORD] = config.password
            }
        }
    }

    // Return YouTube Dislikes
    val rytdEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.RYTD_ENABLED] ?: true
        }

    suspend fun setRytdEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.RYTD_ENABLED] = enabled
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

    // Lyrics Provider ordering and enable/disable
    val lyricsProviderOrder: Flow<String> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.LYRICS_PROVIDER_ORDER] ?: ""
        }

    suspend fun setLyricsProviderOrder(order: String) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.LYRICS_PROVIDER_ORDER] = order
        }
    }

    private val providerEnabledKeys = mapOf(
        "BetterLyrics" to Keys.LYRICS_PROVIDER_ENABLED_BETTERLYRICS,
        "SimpMusic" to Keys.LYRICS_PROVIDER_ENABLED_SIMPMUSIC,
        "LyricsPlus" to Keys.LYRICS_PROVIDER_ENABLED_LYRICSPLUS,
        "LrcLib" to Keys.LYRICS_PROVIDER_ENABLED_LRCLIB,
        "YouTube" to Keys.LYRICS_PROVIDER_ENABLED_YOUTUBE,
        "KuGou" to Keys.LYRICS_PROVIDER_ENABLED_KUGOU,
        "Paxsenix" to Keys.LYRICS_PROVIDER_ENABLED_PAXSENIX,
        "YouTubeSubtitle" to Keys.LYRICS_PROVIDER_ENABLED_YOUTUBESUBTITLE,
    )

    fun isLyricsProviderEnabled(providerName: String): Flow<Boolean> =
        context.playerPreferencesDataStore.data.map { preferences ->
            val key = providerEnabledKeys[providerName] ?: return@map true
            preferences[key] ?: true
        }

    suspend fun setLyricsProviderEnabled(providerName: String, enabled: Boolean) {
        val key = providerEnabledKeys[providerName] ?: return
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    fun allLyricsProviderEnabledStates(): Flow<Map<String, Boolean>> =
        context.playerPreferencesDataStore.data.map { preferences ->
            providerEnabledKeys.mapValues { (_, key) -> preferences[key] ?: true }
        }

    // ========== MINI PLAYER PREFERENCES ==========

    val miniPlayerScale: Flow<Float> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MINI_PLAYER_SCALE] ?: 0.45f
        }

    suspend fun setMiniPlayerScale(scale: Float) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MINI_PLAYER_SCALE] = scale
        }
    }


    val miniPlayerContinueWatchingEnabled: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MINI_PLAYER_CONTINUE_WATCHING_ENABLED] ?: true
        }

    suspend fun setMiniPlayerContinueWatchingEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MINI_PLAYER_CONTINUE_WATCHING_ENABLED] = enabled
        }
    }

    val playDuringCalls: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.PLAY_DURING_CALLS] ?: false
        }

    suspend fun setPlayDuringCalls(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.PLAY_DURING_CALLS] = enabled
        }
    }

    val miniPlayerShowSkipControls: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MINI_PLAYER_SHOW_SKIP_CONTROLS] ?: false
        }

    suspend fun setMiniPlayerShowSkipControls(show: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MINI_PLAYER_SHOW_SKIP_CONTROLS] = show
        }
    }

    val miniPlayerShowNextPrevControls: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences ->
            preferences[Keys.MINI_PLAYER_SHOW_NEXT_PREV_CONTROLS] ?: false
        }

    suspend fun setMiniPlayerShowNextPrevControls(show: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.MINI_PLAYER_SHOW_NEXT_PREV_CONTROLS] = show
        }
    }

    // DEEP FLOW (INCOGNITO / NO-ENGINE) MODE

    val deepFlowActive: Flow<Boolean> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.DEEP_FLOW_ACTIVE] ?: false }

    val deepFlowActivatedAt: Flow<Long> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.DEEP_FLOW_ACTIVATED_AT] ?: 0L }

    val deepFlowExpireHours: Flow<Int> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.DEEP_FLOW_EXPIRE_HOURS] ?: 4 }

    suspend fun setDeepFlowActive(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEEP_FLOW_ACTIVE] = enabled
            if (enabled) {
                preferences[Keys.DEEP_FLOW_ACTIVATED_AT] = System.currentTimeMillis()
            } else {
                preferences[Keys.DEEP_FLOW_ACTIVATED_AT] = 0L
            }
        }
    }

    suspend fun setDeepFlowExpireHours(hours: Int) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.DEEP_FLOW_EXPIRE_HOURS] = hours
        }
    }

    // AUTO-BACKUP SETTINGS
    val autoBackupFrequency: Flow<LocalDataManager.AutoBackupFrequency> = context.playerPreferencesDataStore.data
        .map { preferences ->
            runCatching {
                LocalDataManager.AutoBackupFrequency.valueOf(
                    preferences[Keys.AUTO_BACKUP_FREQUENCY] ?: LocalDataManager.AutoBackupFrequency.NONE.name
                )
            }.getOrDefault(LocalDataManager.AutoBackupFrequency.NONE)
        }

    suspend fun setAutoBackupFrequency(frequency: LocalDataManager.AutoBackupFrequency) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.AUTO_BACKUP_FREQUENCY] = frequency.name
        }
    }

    val autoBackupFolderUri: Flow<String?> = context.playerPreferencesDataStore.data
        .map { preferences -> preferences[Keys.AUTO_BACKUP_FOLDER_URI]?.takeIf { it.isNotBlank() } }

    suspend fun setAutoBackupFolderUri(uri: String?) {
        context.playerPreferencesDataStore.edit { preferences ->
            if (uri != null) preferences[Keys.AUTO_BACKUP_FOLDER_URI] = uri
            else preferences.remove(Keys.AUTO_BACKUP_FOLDER_URI)
        }
    }

    val autoBackupType: Flow<LocalDataManager.AutoBackupType> = context.playerPreferencesDataStore.data
        .map { preferences ->
            runCatching {
                LocalDataManager.AutoBackupType.valueOf(
                    preferences[Keys.AUTO_BACKUP_TYPE] ?: LocalDataManager.AutoBackupType.APP_DATA.name
                )
            }.getOrDefault(LocalDataManager.AutoBackupType.APP_DATA)
        }

    suspend fun setAutoBackupType(type: LocalDataManager.AutoBackupType) {
        context.playerPreferencesDataStore.edit { preferences ->
            preferences[Keys.AUTO_BACKUP_TYPE] = type.name
        }
    }

    
    suspend fun isDeepFlowCurrentlyActive(): Boolean {
        val prefs = context.playerPreferencesDataStore.data.first()
        val active = prefs[Keys.DEEP_FLOW_ACTIVE] ?: false
        if (!active) return false
        val activatedAt = prefs[Keys.DEEP_FLOW_ACTIVATED_AT] ?: 0L
        val expireHours = prefs[Keys.DEEP_FLOW_EXPIRE_HOURS] ?: 4
        if (expireHours == DEEP_FLOW_NEVER_EXPIRES_HOURS) return true
        val elapsedHours = (System.currentTimeMillis() - activatedAt) / 3_600_000.0
        val stillActive = elapsedHours < expireHours
        if (!stillActive) {
            setDeepFlowActive(false)
        }
        return stillActive
    }

    suspend fun getExportData(): SettingsBackup {
        val prefs = context.playerPreferencesDataStore.data.first()
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        val ints = mutableMapOf<String, Int>()
        val floats = mutableMapOf<String, Float>()
        val longs = mutableMapOf<String, Long>()

        prefs.asMap().forEach { (key, value) ->
            if (key.name == "proxy_password") return@forEach
            when (value) {
                is String -> strings[key.name] = value
                is Boolean -> booleans[key.name] = value
                is Int -> ints[key.name] = value
                is Float -> floats[key.name] = value
                is Long -> longs[key.name] = value
            }
        }
        return SettingsBackup(strings, booleans, ints, floats, longs)
    }

    suspend fun restoreData(backup: SettingsBackup) {
        context.playerPreferencesDataStore.edit { prefs ->
            backup.strings.forEach { (k, v) ->
                if (k != "proxy_password") {
                    prefs[stringPreferencesKey(k)] = v
                }
            }
            backup.booleans.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
            backup.ints.forEach { (k, v) -> prefs[intPreferencesKey(k)] = v }
            backup.floats.forEach { (k, v) -> prefs[floatPreferencesKey(k)] = v }
            backup.longs.forEach { (k, v) -> prefs[longPreferencesKey(k)] = v }
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
    AGGRESSIVE("Fast Start", 3_000, 18_000, 250, 750),
    // Balanced: Good default for most connections
    STABLE("Balanced", 10_000, 40_000, 750, 1_500),
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
            return values().find { it.label == label } ?: AUTO
        }
        
        fun fromHeight(height: Int): VideoQuality {
            return values()
                .filter { it != AUTO }
                .minByOrNull { kotlin.math.abs(it.height - height) } ?: Q_720p
        }
    }
}

enum class MusicAudioQuality(val label: String) {
    AUTO("Auto"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    companion object {
        fun fromString(label: String): MusicAudioQuality {
            return values().find { it.label == label } ?: AUTO
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

enum class DownloadDialogStyle {
    FULL,
    COMPACT
}

enum class MusicPlayerBackgroundStyle {
    BLUR_GRADIENT,
    BLUR,
    GRADIENT,
    DEFAULT
}

enum class FullscreenSeekbarPaddingMode {
    FULL_WIDTH,
    DEFAULT,
    CUSTOM
}

enum class HomeViewMode {
    GRID,
    LIST
}

enum class PlayerRelatedCardStyle {
    COMPACT,    
    FULL_WIDTH 
}


