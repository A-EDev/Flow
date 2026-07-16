package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.platform.AppUiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appUiModeDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "app_ui_mode")

/** Stores the interface override independently from playback preferences. */
class AppUiModePreferences(context: Context) {
    private val appContext = context.applicationContext

    val mode: Flow<AppUiMode> = appContext.appUiModeDataStore.data.map { preferences ->
        AppUiMode.fromStorage(preferences[MODE])
    }

    suspend fun setMode(mode: AppUiMode) {
        appContext.appUiModeDataStore.edit { preferences ->
            preferences[MODE] = mode.name
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
    }
}
