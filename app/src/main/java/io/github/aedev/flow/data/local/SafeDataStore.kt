package io.github.aedev.flow.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlin.properties.ReadOnlyProperty

private const val SAFE_DATA_STORE_TAG = "SafeDataStore"

fun safePreferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(
        name = name,
        corruptionHandler = ReplaceFileCorruptionHandler { corruption ->
            Log.w(SAFE_DATA_STORE_TAG, "Resetting corrupted preferences DataStore: $name", corruption)
            emptyPreferences()
        }
    )
