package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Shared with the equalizer store: one file may only ever have one DataStore instance. */
internal val Context.audioSettingsDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "audio_settings")

class AudioSettingsPersistence private constructor(
    private val context: Context,
) {
    companion object {
        private val PITCH_KEY = floatPreferencesKey("pitch_level")
        private val SPEED_KEY = floatPreferencesKey("speed_level")

        @Volatile
        private var instance: AudioSettingsPersistence? = null

        fun getInstance(context: Context): AudioSettingsPersistence =
            instance ?: synchronized(this) {
                instance ?: AudioSettingsPersistence(context.applicationContext).also { instance = it }
            }
    }

    data class AudioSettings(
        val pitch: Float = 0.0f,
        val speed: Float = 1.0f,
    )

    val settingsFlow: Flow<AudioSettings> =
        context.audioSettingsDataStore.data
            .map { preferences ->
                AudioSettings(
                    pitch = preferences[PITCH_KEY] ?: 0.0f,
                    speed = preferences[SPEED_KEY] ?: 1.0f,
                )
            }

    suspend fun savePitch(pitch: Float) {
        context.audioSettingsDataStore.edit { it[PITCH_KEY] = pitch }
    }

    suspend fun saveSpeed(speed: Float) {
        context.audioSettingsDataStore.edit { it[SPEED_KEY] = speed }
    }
}
