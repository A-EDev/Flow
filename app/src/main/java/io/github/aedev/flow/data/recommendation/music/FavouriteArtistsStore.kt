package io.github.aedev.flow.data.recommendation.music

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FavouriteArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String = "",
)

private val Context.favouriteArtistsDataStore by safePreferencesDataStore(name = "favourite_artists")

/**
 * The artists the viewer picked by hand. The music brain only keeps their affinity, which listening
 * keeps changing, so the picks themselves live here for the picker to show.
 */
@Singleton
class FavouriteArtistsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val musicBrain: MusicBrainEngine,
    ) {
        private val key = stringPreferencesKey("artists")
        private val json = Json { ignoreUnknownKeys = true }

        val artists: Flow<List<FavouriteArtist>> = context.favouriteArtistsDataStore.data.map { decode(it[key]) }

        suspend fun setFavourite(
            artist: FavouriteArtist,
            favourite: Boolean,
        ) {
            context.favouriteArtistsDataStore.edit { preferences ->
                val others = decode(preferences[key]).filterNot { it.id == artist.id }
                preferences[key] = json.encodeToString(if (favourite) others + artist else others)
            }
            musicBrain.setFavouriteArtist(artist.id, artist.name, favourite)
        }

        private fun decode(raw: String?): List<FavouriteArtist> =
            raw?.let { runCatching { json.decodeFromString<List<FavouriteArtist>>(it) }.getOrNull() }.orEmpty()
    }
