package io.github.aedev.flow.data.recommendation

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The names of blocked channels, keyed by channel id. The brain keeps only the ids, so this is what
 * names them in Hidden from you. Device-local: synced and restored blocks get their names here again.
 */
@Singleton
class BlockedChannelNameStore
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        private companion object {
            const val TAG = "BlockedChannelNames"
        }

        @Serializable
        data class Index(
            val names: Map<String, String> = emptyMap(),
        )

        private object IndexSerializer : Serializer<Index> {
            override val defaultValue: Index = Index()

            override suspend fun readFrom(input: InputStream): Index =
                try {
                    val text = input.bufferedReader().readText()
                    if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<Index>(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read blocked channel names", e)
                    defaultValue
                }

            override suspend fun writeTo(
                t: Index,
                output: OutputStream,
            ) {
                output.write(Json.encodeToString(t).toByteArray())
            }
        }

        private val Context.blockedNamesDataStore: DataStore<Index>
            by dataStore(fileName = "flow_blocked_channel_names_v1.json", serializer = IndexSerializer)

        suspend fun names(): Map<String, String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.blockedNamesDataStore.data
                        .first()
                        .names
                }.getOrDefault(emptyMap())
            }

        /** Saves [names] and drops the names of channels no longer in [blocked]. */
        suspend fun save(
            names: Map<String, String>,
            blocked: Set<String>,
        ) = update { current -> (current + names.filterValues { it.isNotBlank() }).filterKeys { it in blocked } }

        suspend fun remember(
            channelId: String,
            name: String,
        ) {
            if (channelId.isBlank() || name.isBlank()) return
            update { it + (channelId to name) }
        }

        suspend fun forget(channelId: String) = update { it - channelId }

        private suspend fun update(transform: (Map<String, String>) -> Map<String, String>) =
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.blockedNamesDataStore.updateData { index -> Index(transform(index.names)) }
                }.onFailure { Log.e(TAG, "Failed to update blocked channel names", it) }
            }
    }
