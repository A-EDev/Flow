package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.audioSettingsDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "audio_settings")

class AudioSettingsPersistence private constructor(private val context: Context) {

    companion object {
        private val PITCH_KEY = floatPreferencesKey("pitch_level")
        private val SPEED_KEY = floatPreferencesKey("speed_level")
        private val EQ_PROFILE_KEY = stringPreferencesKey("eq_profile_name")
        private val BASS_BOOST_KEY = floatPreferencesKey("bass_boost_strength")
        private val VIRTUALIZER_KEY = floatPreferencesKey("virtualizer_strength")
        private val VOLUME_MULTIPLIER_KEY = floatPreferencesKey("volume_multiplier")
        private val CUSTOM_EQ_KEY = stringPreferencesKey("custom_eq_profile_json")
        private val CUSTOM_PRESETS_KEY = stringPreferencesKey("custom_eq_presets_json")

        @Volatile
        private var INSTANCE: AudioSettingsPersistence? = null

        fun getInstance(context: Context): AudioSettingsPersistence {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioSettingsPersistence(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    data class AudioSettings(
        val pitch: Float = 0.0f,
        val speed: Float = 1.0f,
        val eqProfile: String = "Flat",
        val bassBoost: Float = 0f,
        val virtualizer: Float = 0f,
        val volumeMultiplier: Float = 1.0f
    )

    val settingsFlow: Flow<AudioSettings> = context.audioSettingsDataStore.data
        .map { preferences ->
            AudioSettings(
                pitch = preferences[PITCH_KEY] ?: 0.0f,
                speed = preferences[SPEED_KEY] ?: 1.0f,
                eqProfile = preferences[EQ_PROFILE_KEY] ?: "Flat",
                bassBoost = preferences[BASS_BOOST_KEY] ?: 0f,
                virtualizer = preferences[VIRTUALIZER_KEY] ?: 0f,
                volumeMultiplier = preferences[VOLUME_MULTIPLIER_KEY] ?: 1.0f
            )
        }

    suspend fun savePitch(pitch: Float) {
        context.audioSettingsDataStore.edit { it[PITCH_KEY] = pitch }
    }

    suspend fun saveSpeed(speed: Float) {
        context.audioSettingsDataStore.edit { it[SPEED_KEY] = speed }
    }

    suspend fun saveEqProfile(profileName: String) {
        context.audioSettingsDataStore.edit { it[EQ_PROFILE_KEY] = profileName }
    }

    suspend fun saveBassBoost(strength: Float) {
        context.audioSettingsDataStore.edit { it[BASS_BOOST_KEY] = strength }
    }
    
    suspend fun saveVirtualizer(strength: Float) {
        context.audioSettingsDataStore.edit { it[VIRTUALIZER_KEY] = strength }
    }

    suspend fun saveVolumeMultiplier(multiplier: Float) {
        context.audioSettingsDataStore.edit { it[VOLUME_MULTIPLIER_KEY] = multiplier }
    }

    val customEqJsonFlow: Flow<String?> = context.audioSettingsDataStore.data
        .map { it[CUSTOM_EQ_KEY] }

    val customPresetsJsonFlow: Flow<String?> = context.audioSettingsDataStore.data
        .map { it[CUSTOM_PRESETS_KEY] }

    suspend fun saveCustomEqJson(json: String) {
        context.audioSettingsDataStore.edit { it[CUSTOM_EQ_KEY] = json }
    }

    suspend fun saveCustomPresetsJson(json: String) {
        context.audioSettingsDataStore.edit { it[CUSTOM_PRESETS_KEY] = json }
    }
}
