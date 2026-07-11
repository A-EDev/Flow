package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.canonicalFamily
import io.github.aedev.flow.ui.theme.defaultVariant
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

val Context.dataStore: DataStore<Preferences> by safePreferencesDataStore(name = "flow_preferences")

class LocalDataManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val gson = Gson()

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
        private val WATCH_HISTORY = stringPreferencesKey("watch_history")
        private val LIKED_VIDEOS = stringPreferencesKey("liked_videos")
        private val PLAYLISTS = stringPreferencesKey("playlists")
        private val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
        private val VIDEO_QUALITY_WIFI = stringPreferencesKey("quality_wifi")
        private val VIDEO_QUALITY_CELLULAR = stringPreferencesKey("quality_cellular")
        private val BACKGROUND_PLAY = stringPreferencesKey("background_play")
        private val TRENDING_REGION = stringPreferencesKey("trending_region")
        private val LAST_UPDATE_CHECK = stringPreferencesKey("last_update_check")
        private val BEDTIME_REMINDER = androidx.datastore.preferences.core.booleanPreferencesKey("bedtime_reminder")
        private val BEDTIME_START_HOUR = androidx.datastore.preferences.core.intPreferencesKey("bedtime_start_hour")
        private val BEDTIME_START_MINUTE = androidx.datastore.preferences.core.intPreferencesKey("bedtime_start_minute")
        private val BEDTIME_END_HOUR = androidx.datastore.preferences.core.intPreferencesKey("bedtime_end_hour") // Optional, mostly for UI
        private val BEDTIME_END_MINUTE = androidx.datastore.preferences.core.intPreferencesKey("bedtime_end_minute")
        
        private val BREAK_REMINDER = androidx.datastore.preferences.core.booleanPreferencesKey("break_reminder")
        private val BREAK_FREQUENCY = androidx.datastore.preferences.core.intPreferencesKey("break_frequency") // Minutes

        private val CUSTOM_THEME_COLORS = stringPreferencesKey("custom_theme_colors")
        private val CUSTOM_THEME_PALETTES = stringPreferencesKey("custom_theme_palettes_v2")
        private val THEME_VARIANT = stringPreferencesKey("theme_variant")
        private val SYSTEM_LIGHT_THEME_MODE = stringPreferencesKey("system_light_theme_mode")
        private val SYSTEM_DARK_THEME_MODE = stringPreferencesKey("system_dark_theme_mode")
        private val SYSTEM_DARK_THEME_VARIANT = stringPreferencesKey("system_dark_theme_variant")

        val AUTO_BACKUP_LAST_RUN = androidx.datastore.preferences.core.longPreferencesKey("auto_backup_last_run")
    }

    enum class AutoBackupFrequency { NONE, DAILY, WEEKLY, MONTHLY }
    enum class AutoBackupType { APP_DATA, BRAIN, MASTER }

    // Update Settings
    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_UPDATE_CHECK]?.toLongOrNull() ?: 0L
    }

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK] = timestamp.toString()
        }
    }

    // Theme Settings
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        parseThemeMode(prefs[THEME_MODE], ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.canonicalFamily().name
        }
    }

    val themeVariant: Flow<ThemeVariant> = context.dataStore.data.map { prefs ->
        parseThemeVariant(
            raw = prefs[THEME_VARIANT],
            fallback = parseLegacyThemeMode(prefs[THEME_MODE], ThemeMode.SYSTEM).defaultVariant()
        )
    }

    suspend fun setThemeVariant(variant: ThemeVariant) {
        context.dataStore.edit { prefs -> prefs[THEME_VARIANT] = variant.name }
    }

    val systemLightThemeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        parseThemeMode(prefs[SYSTEM_LIGHT_THEME_MODE], ThemeMode.DARK)
    }

    suspend fun setSystemLightThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[SYSTEM_LIGHT_THEME_MODE] = mode.canonicalFamily().name
        }
    }

    val systemDarkThemeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        parseThemeMode(prefs[SYSTEM_DARK_THEME_MODE], ThemeMode.DARK)
    }

    suspend fun setSystemDarkThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[SYSTEM_DARK_THEME_MODE] = mode.canonicalFamily().name
        }
    }

    val systemDarkThemeVariant: Flow<ThemeVariant> = context.dataStore.data.map { prefs ->
        parseThemeVariant(
            prefs[SYSTEM_DARK_THEME_VARIANT],
            parseLegacyThemeMode(prefs[SYSTEM_DARK_THEME_MODE], ThemeMode.DARK)
                .defaultVariant()
                .let { if (it == ThemeVariant.LIGHT) ThemeVariant.DARK else it }
        )
    }

    suspend fun setSystemDarkThemeVariant(variant: ThemeVariant) {
        context.dataStore.edit { prefs -> prefs[SYSTEM_DARK_THEME_VARIANT] = variant.name }
    }

    private fun parseThemeMode(raw: String?, fallback: ThemeMode): ThemeMode {
        return parseLegacyThemeMode(raw, fallback).canonicalFamily()
    }

    private fun parseLegacyThemeMode(raw: String?, fallback: ThemeMode): ThemeMode {
        return runCatching { raw?.let(ThemeMode::valueOf) ?: fallback }.getOrDefault(fallback)
    }

    private fun parseThemeVariant(raw: String?, fallback: ThemeVariant): ThemeVariant {
        return runCatching { raw?.let(ThemeVariant::valueOf) ?: fallback }.getOrDefault(fallback)
    }

    val customThemePalettes: Flow<CustomThemePalettes> = context.dataStore.data.map { prefs ->
        deserializeCustomThemePalettes(
            raw = prefs[CUSTOM_THEME_PALETTES],
            legacyRaw = prefs[CUSTOM_THEME_COLORS]
        )
    }

    suspend fun setCustomThemePalettes(palettes: CustomThemePalettes) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_THEME_PALETTES] = gson.toJson(palettes)
        }
    }

    private fun deserializeCustomThemePalettes(raw: String?, legacyRaw: String?): CustomThemePalettes {
        if (!raw.isNullOrBlank()) {
            runCatching { gson.fromJson(raw, CustomThemePalettes::class.java) }
                .getOrNull()
                ?.let { return it }
        }
        val legacyValues = legacyRaw
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf { it.size == 16 }
        return if (legacyValues != null) {
            CustomThemePalettes(dark = CustomThemeColors.fromLegacy(legacyValues))
        } else {
            CustomThemePalettes()
        }
    }

    // Subscriptions
    val subscriptions: Flow<List<Channel>> = context.dataStore.data.map { prefs ->
        val json = prefs[SUBSCRIPTIONS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Channel>>() {}.type)
    }

    suspend fun addSubscription(channel: Channel) {
        context.dataStore.edit { prefs ->
            val current: List<Channel> = gson.fromJson(
                prefs[SUBSCRIPTIONS] ?: "[]",
                object : TypeToken<List<Channel>>() {}.type
            )
            val updated = current.toMutableList()
            if (updated.none { it.id == channel.id }) {
                updated.add(channel)
                prefs[SUBSCRIPTIONS] = gson.toJson(updated)
            }
        }
    }

    suspend fun removeSubscription(channelId: String) {
        context.dataStore.edit { prefs ->
            val current: List<Channel> = gson.fromJson(
                prefs[SUBSCRIPTIONS] ?: "[]",
                object : TypeToken<List<Channel>>() {}.type
            )
            val updated = current.filter { it.id != channelId }
            prefs[SUBSCRIPTIONS] = gson.toJson(updated)
        }
    }

    // Watch History
    val watchHistory: Flow<List<Video>> = context.dataStore.data.map { prefs ->
        val json = prefs[WATCH_HISTORY] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Video>>() {}.type)
    }

    suspend fun addToWatchHistory(video: Video) {
        if (PlayerPreferences(context).isDeepFlowCurrentlyActive()) return

        context.dataStore.edit { prefs ->
            val current: List<Video> = gson.fromJson(
                prefs[WATCH_HISTORY] ?: "[]",
                object : TypeToken<List<Video>>() {}.type
            )
            val updated = current.toMutableList()
            updated.removeAll { it.id == video.id }
            updated.add(0, video)
            if (updated.size > 500) {
                updated.removeAt(updated.size - 1)
            }
            prefs[WATCH_HISTORY] = gson.toJson(updated)
        }
    }

    suspend fun clearWatchHistory() {
        context.dataStore.edit { prefs ->
            prefs[WATCH_HISTORY] = "[]"
        }
    }

    // Liked Videos
    val likedVideos: Flow<List<Video>> = context.dataStore.data.map { prefs ->
        val json = prefs[LIKED_VIDEOS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Video>>() {}.type)
    }

    suspend fun toggleLike(video: Video) {
        context.dataStore.edit { prefs ->
            val current: List<Video> = gson.fromJson(
                prefs[LIKED_VIDEOS] ?: "[]",
                object : TypeToken<List<Video>>() {}.type
            )
            val updated = current.toMutableList()
            if (updated.any { it.id == video.id }) {
                updated.removeAll { it.id == video.id }
            } else {
                updated.add(0, video)
            }
            prefs[LIKED_VIDEOS] = gson.toJson(updated)
        }
    }

    // Playlists
    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Playlist>>() {}.type)
    }

    suspend fun createPlaylist(name: String): Playlist {
        val newPlaylist = Playlist(
            id = "local_${System.currentTimeMillis()}",
            name = name,
            thumbnailUrl = "",
            videoCount = 0,
            isLocal = true
        )
        context.dataStore.edit { prefs ->
            val current: List<Playlist> = gson.fromJson(
                prefs[PLAYLISTS] ?: "[]",
                object : TypeToken<List<Playlist>>() {}.type
            )
            val updated = current.toMutableList()
            updated.add(newPlaylist)
            prefs[PLAYLISTS] = gson.toJson(updated)
        }
        return newPlaylist
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        context.dataStore.edit { prefs ->
            val current: List<Playlist> = gson.fromJson(
                prefs[PLAYLISTS] ?: "[]",
                object : TypeToken<List<Playlist>>() {}.type
            )
            val updated = current.map { playlist ->
                if (playlist.id == playlistId) {
                    val videos = playlist.videos.toMutableList()
                    if (videos.none { it.id == video.id }) {
                        videos.add(video)
                    }
                    playlist.copy(
                        videos = videos,
                        videoCount = videos.size,
                        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: ""
                    )
                } else {
                    playlist
                }
            }
            prefs[PLAYLISTS] = gson.toJson(updated)
        }
    }

    // Search History
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[SEARCH_HISTORY]?.toList() ?: emptyList()
    }

    suspend fun addSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
            current.add(query)
            if (current.size > 20) {
                current.remove(current.first())
            }
            prefs[SEARCH_HISTORY] = current
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs[SEARCH_HISTORY] = emptySet()
        }
    }

    // Settings
    val trendingRegion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRENDING_REGION] ?: "US"
    }

    suspend fun setTrendingRegion(region: String) {
        context.dataStore.edit { prefs ->
            prefs[TRENDING_REGION] = region
        }
    }

    val bedtimeReminder: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BEDTIME_REMINDER] ?: false
    }

    val breakReminder: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BREAK_REMINDER] ?: false
    }

    suspend fun setBedtimeReminder(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BEDTIME_REMINDER] = enabled
        }
    }

    val bedtimeStartHour: Flow<Int> = context.dataStore.data.map { prefs -> prefs[BEDTIME_START_HOUR] ?: 23 } // Default 11 PM
    val bedtimeStartMinute: Flow<Int> = context.dataStore.data.map { prefs -> prefs[BEDTIME_START_MINUTE] ?: 0 }
    val bedtimeEndHour: Flow<Int> = context.dataStore.data.map { prefs -> prefs[BEDTIME_END_HOUR] ?: 7 } // Default 7 AM
    val bedtimeEndMinute: Flow<Int> = context.dataStore.data.map { prefs -> prefs[BEDTIME_END_MINUTE] ?: 0 }

    suspend fun setBedtimeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit { prefs ->
            prefs[BEDTIME_START_HOUR] = startHour
            prefs[BEDTIME_START_MINUTE] = startMinute
            prefs[BEDTIME_END_HOUR] = endHour
            prefs[BEDTIME_END_MINUTE] = endMinute
        }
    }

    suspend fun setBreakReminder(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BREAK_REMINDER] = enabled
        }
    }

    val breakFrequency: Flow<Int> = context.dataStore.data.map { prefs -> prefs[BREAK_FREQUENCY] ?: 30 } // Default 30 min

    suspend fun setBreakFrequency(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[BREAK_FREQUENCY] = minutes
        }
    }

    suspend fun getExportData(): SettingsBackup {
        val prefs = context.dataStore.data.first()
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        val ints = mutableMapOf<String, Int>()
        val floats = mutableMapOf<String, Float>()
        val longs = mutableMapOf<String, Long>()

        prefs.asMap().entries.forEach { (key, value) ->
            val name = key.name
            if (name == "theme_mode" || name == "theme_variant" || name == "accent_color" ||
                name == "custom_theme_colors" || name == "custom_theme_palettes_v2" ||
                name == "system_light_theme_mode" || name == "system_dark_theme_mode" ||
                name == "system_dark_theme_variant" ||
                name == "bedtime_reminder" || name == "break_reminder" ||
                name.startsWith("bedtime_") || name == "break_frequency") {
                
                when (value) {
                    is String -> strings[name] = value
                    is Boolean -> booleans[name] = value
                    is Int -> ints[name] = value
                    is Float -> floats[name] = value
                    is Long -> longs[name] = value
                }
            }
        }

        val storedThemeMode = parseLegacyThemeMode(prefs[THEME_MODE], ThemeMode.SYSTEM)
        strings[THEME_MODE.name] = storedThemeMode.canonicalFamily().name
        strings[THEME_VARIANT.name] = parseThemeVariant(
            prefs[THEME_VARIANT],
            storedThemeMode.defaultVariant()
        ).name

        val storedSystemLightMode = parseLegacyThemeMode(prefs[SYSTEM_LIGHT_THEME_MODE], ThemeMode.DARK)
        strings[SYSTEM_LIGHT_THEME_MODE.name] = storedSystemLightMode.canonicalFamily().name

        val storedSystemDarkMode = parseLegacyThemeMode(prefs[SYSTEM_DARK_THEME_MODE], ThemeMode.DARK)
        strings[SYSTEM_DARK_THEME_MODE.name] = storedSystemDarkMode.canonicalFamily().name
        strings[SYSTEM_DARK_THEME_VARIANT.name] = parseThemeVariant(
            prefs[SYSTEM_DARK_THEME_VARIANT],
            storedSystemDarkMode.defaultVariant().let {
                if (it == ThemeVariant.LIGHT) ThemeVariant.DARK else it
            }
        ).name
        return SettingsBackup(strings, booleans, ints, floats, longs)
    }

    suspend fun restoreData(backup: SettingsBackup) {
        context.dataStore.edit { prefs ->
            val restoredThemeMode = backup.strings["theme_mode"]
                ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            restoredThemeMode?.let { mode ->
                prefs[THEME_MODE] = mode.canonicalFamily().name
                prefs[THEME_VARIANT] = backup.strings["theme_variant"]
                    ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                    ?.name
                    ?: mode.defaultVariant().name
            }

            val restoredSystemLightMode = backup.strings["system_light_theme_mode"]
                ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            restoredSystemLightMode?.let { mode ->
                prefs[SYSTEM_LIGHT_THEME_MODE] = mode.canonicalFamily().name
            }

            val restoredSystemDarkMode = backup.strings["system_dark_theme_mode"]
                ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            restoredSystemDarkMode?.let { mode ->
                prefs[SYSTEM_DARK_THEME_MODE] = mode.canonicalFamily().name
                val fallbackVariant = mode.defaultVariant().let {
                    if (it == ThemeVariant.LIGHT) ThemeVariant.DARK else it
                }
                prefs[SYSTEM_DARK_THEME_VARIANT] = backup.strings["system_dark_theme_variant"]
                    ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                    ?.name
                    ?: fallbackVariant.name
            }

            if (restoredThemeMode == null) {
                backup.strings["theme_variant"]
                    ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                    ?.let { prefs[THEME_VARIANT] = it.name }
            }
            if (restoredSystemDarkMode == null) {
                backup.strings["system_dark_theme_variant"]
                    ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                    ?.let { prefs[SYSTEM_DARK_THEME_VARIANT] = it.name }
            }

            listOf("accent_color", "custom_theme_colors", "custom_theme_palettes_v2").forEach { key ->
                backup.strings[key]?.let { prefs[stringPreferencesKey(key)] = it }
            }
            backup.booleans.forEach { (k, v) -> 
                if (k == "bedtime_reminder" || k == "break_reminder") {
                    prefs[androidx.datastore.preferences.core.booleanPreferencesKey(k)] = v 
                }
            }
            backup.ints.forEach { (k, v) -> 
                if (k.startsWith("bedtime_") || k == "break_frequency") {
                    prefs[androidx.datastore.preferences.core.intPreferencesKey(k)] = v 
                }
            }
        }
    }


    val autoBackupLastRun: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[AUTO_BACKUP_LAST_RUN] ?: 0L
    }

    suspend fun setAutoBackupLastRun(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_LAST_RUN] = timestamp
        }
    }
}

