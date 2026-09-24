package io.github.aedev.flow.data.localmedia

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localMediaStore by safePreferencesDataStore(name = "local_media")

/** What the local library hides, and how it is laid out. Defaults match what it always hid. */
data class LocalMediaSettings(
    /** Voice notes, recordings and chat app media, found by their folder names. */
    val hideAppAudio: Boolean = true,
    /** Songs shorter than this are hidden; 0 shows every length. */
    val minAudioSeconds: Int = DEFAULT_MIN_AUDIO_SECONDS,
    /** Folders the viewer hid, by MediaStore bucket id. */
    val hiddenFolderIds: Set<String> = emptySet(),
    /** Null follows the window: a list on phones, a grid on wider windows. */
    val videosAsGrid: Boolean? = null,
) {
    companion object {
        const val DEFAULT_MIN_AUDIO_SECONDS = 60
        val MIN_AUDIO_CHOICES = listOf(0, 10, 30, 60)
    }
}

@Singleton
class LocalMediaPreferences
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val HIDE_APP_AUDIO = booleanPreferencesKey("hide_app_audio")
            val MIN_AUDIO_SECONDS = intPreferencesKey("min_audio_seconds")
            val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folder_ids")
            val VIDEOS_AS_GRID = booleanPreferencesKey("videos_as_grid")
        }

        val settings: Flow<LocalMediaSettings> =
            context.localMediaStore.data
                .map { prefs ->
                    LocalMediaSettings(
                        hideAppAudio = prefs[Keys.HIDE_APP_AUDIO] ?: true,
                        minAudioSeconds = prefs[Keys.MIN_AUDIO_SECONDS] ?: LocalMediaSettings.DEFAULT_MIN_AUDIO_SECONDS,
                        hiddenFolderIds = prefs[Keys.HIDDEN_FOLDERS].orEmpty(),
                        videosAsGrid = prefs[Keys.VIDEOS_AS_GRID],
                    )
                }.distinctUntilChanged()

        suspend fun setHideAppAudio(hide: Boolean) = context.localMediaStore.edit { it[Keys.HIDE_APP_AUDIO] = hide }

        suspend fun setMinAudioSeconds(seconds: Int) = context.localMediaStore.edit { it[Keys.MIN_AUDIO_SECONDS] = seconds }

        suspend fun setFolderHidden(
            folderId: String,
            hidden: Boolean,
        ) = context.localMediaStore.edit {
            val current = it[Keys.HIDDEN_FOLDERS].orEmpty()
            it[Keys.HIDDEN_FOLDERS] = if (hidden) current + folderId else current - folderId
        }

        suspend fun setVideosAsGrid(grid: Boolean) = context.localMediaStore.edit { it[Keys.VIDEOS_AS_GRID] = grid }
    }
