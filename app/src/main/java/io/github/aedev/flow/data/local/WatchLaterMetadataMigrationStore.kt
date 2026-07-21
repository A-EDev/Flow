package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.watchLaterMetadataMigrationDataStore: DataStore<Preferences> by
    safePreferencesDataStore(name = "watch_later_metadata_migration")

@Singleton
class WatchLaterMetadataMigrationStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.applicationContext.watchLaterMetadataMigrationDataStore

    data class State(
        val isComplete: Boolean,
        val processedVideoIds: Set<String>,
        val failedOnceVideoIds: Set<String>
    )

    suspend fun state(): State {
        val preferences = dataStore.data.first()
        return State(
            isComplete = (preferences[MIGRATION_VERSION] ?: 0) >= CURRENT_VERSION,
            processedVideoIds = preferences[PROCESSED_VIDEO_IDS].orEmpty(),
            failedOnceVideoIds = preferences[FAILED_ONCE_VIDEO_IDS].orEmpty()
        )
    }

    suspend fun markProcessed(videoId: String) {
        dataStore.edit { preferences ->
            preferences[PROCESSED_VIDEO_IDS] =
                preferences[PROCESSED_VIDEO_IDS].orEmpty() + videoId
            preferences[FAILED_ONCE_VIDEO_IDS] =
                preferences[FAILED_ONCE_VIDEO_IDS].orEmpty() - videoId
        }
    }

    suspend fun markFailedOnce(videoId: String) {
        dataStore.edit { preferences ->
            preferences[FAILED_ONCE_VIDEO_IDS] =
                preferences[FAILED_ONCE_VIDEO_IDS].orEmpty() + videoId
        }
    }

    suspend fun complete() {
        dataStore.edit { preferences ->
            preferences[MIGRATION_VERSION] = CURRENT_VERSION
            preferences.remove(PROCESSED_VIDEO_IDS)
            preferences.remove(FAILED_ONCE_VIDEO_IDS)
        }
    }

    private companion object {
        const val CURRENT_VERSION = 1
        val MIGRATION_VERSION = intPreferencesKey("migration_version")
        val PROCESSED_VIDEO_IDS = stringSetPreferencesKey("processed_video_ids")
        val FAILED_ONCE_VIDEO_IDS = stringSetPreferencesKey("failed_once_video_ids")
    }
}
