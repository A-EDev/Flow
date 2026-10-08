package io.github.aedev.flow.data.scrobble

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.KeystoreSecretBox
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scrobbleDataStore by safePreferencesDataStore(name = "scrobbling")

data class ScrobbleSettings(
    val accounts: Map<ScrobbleService, ScrobbleAccount> = emptyMap(),
    val ownKeyEnabled: Boolean = false,
    val ownKeys: AudioscrobblerKeys = AudioscrobblerKeys("", ""),
    val nowPlaying: Boolean = true,
    val scrobbleLocal: Boolean = false,
    val sendLikes: Boolean = false,
    val pending: Map<ScrobbleService, Int> = emptyMap(),
)

/**
 * Accounts, the viewer's own Last.fm key, the options, and the listens still waiting to be sent.
 * Session keys, tokens and the API secret are sealed with the Keystore before they are written.
 */
@Singleton
class ScrobbleStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val ownKeyEnabledKey = booleanPreferencesKey("lastfm_own_key_enabled")
        private val ownApiKeyKey = stringPreferencesKey("lastfm_own_api_key")
        private val ownSecretKey = stringPreferencesKey("lastfm_own_secret")
        private val nowPlayingKey = booleanPreferencesKey("now_playing")
        private val scrobbleLocalKey = booleanPreferencesKey("scrobble_local")
        private val sendLikesKey = booleanPreferencesKey("send_likes")

        private fun nameKey(service: ScrobbleService) = stringPreferencesKey("${service.name}_user")

        private fun secretKey(service: ScrobbleService) = stringPreferencesKey("${service.name}_secret")

        private fun queueKey(service: ScrobbleService) = stringPreferencesKey("${service.name}_queue")

        private fun lovesKey(service: ScrobbleService) = stringPreferencesKey("${service.name}_loves")

        val settings: Flow<ScrobbleSettings> =
            context.scrobbleDataStore.data
                .map { preferences ->
                    ScrobbleSettings(
                        accounts =
                            ScrobbleService.entries
                                .mapNotNull { service ->
                                    preferences.account(service)?.let { service to it }
                                }.toMap(),
                        ownKeyEnabled = preferences[ownKeyEnabledKey] ?: false,
                        ownKeys =
                            AudioscrobblerKeys(
                                apiKey = preferences[ownApiKeyKey].orEmpty(),
                                secret = KeystoreSecretBox.open(preferences[ownSecretKey]),
                            ),
                        nowPlaying = preferences[nowPlayingKey] ?: true,
                        scrobbleLocal = preferences[scrobbleLocalKey] ?: false,
                        sendLikes = preferences[sendLikesKey] ?: false,
                        pending = ScrobbleService.entries.associateWith { preferences.queue(it).size + preferences.loves(it).size },
                    )
                }.flowOn(Dispatchers.IO)

        suspend fun current(): ScrobbleSettings = settings.first()

        suspend fun setAccount(
            service: ScrobbleService,
            account: ScrobbleAccount?,
        ) {
            val sealed = account?.let { withContext(Dispatchers.IO) { KeystoreSecretBox.seal(it.secret) } }
            context.scrobbleDataStore.edit { preferences ->
                if (account == null || sealed == null) {
                    preferences.remove(nameKey(service))
                    preferences.remove(secretKey(service))
                    preferences.remove(queueKey(service))
                    preferences.remove(lovesKey(service))
                } else {
                    preferences[nameKey(service)] = account.userName
                    preferences[secretKey(service)] = sealed
                }
            }
        }

        suspend fun setOwnKeys(
            enabled: Boolean,
            keys: AudioscrobblerKeys,
        ) {
            val sealed = withContext(Dispatchers.IO) { KeystoreSecretBox.seal(keys.secret.trim()) }
            context.scrobbleDataStore.edit { preferences ->
                preferences[ownKeyEnabledKey] = enabled
                preferences[ownApiKeyKey] = keys.apiKey.trim()
                preferences[ownSecretKey] = sealed
            }
        }

        suspend fun setNowPlaying(enabled: Boolean) {
            context.scrobbleDataStore.edit { it[nowPlayingKey] = enabled }
        }

        suspend fun setScrobbleLocal(enabled: Boolean) {
            context.scrobbleDataStore.edit { it[scrobbleLocalKey] = enabled }
        }

        suspend fun setSendLikes(enabled: Boolean) {
            context.scrobbleDataStore.edit { it[sendLikesKey] = enabled }
        }

        suspend fun enqueueLove(
            service: ScrobbleService,
            love: LoveEntry,
        ) {
            context.scrobbleDataStore.edit { preferences ->
                preferences[lovesKey(service)] = json.encodeToString((preferences.loves(service) + love).takeLast(MAX_QUEUE))
            }
        }

        suspend fun pendingLoves(service: ScrobbleService): List<LoveEntry> =
            context.scrobbleDataStore.data
                .first()
                .loves(service)

        suspend fun dropLove(
            service: ScrobbleService,
            love: LoveEntry,
        ) {
            context.scrobbleDataStore.edit { preferences ->
                val loves = preferences.loves(service)
                val index = loves.indexOf(love)
                if (index >= 0) preferences[lovesKey(service)] = json.encodeToString(loves.filterIndexed { i, _ -> i != index })
            }
        }

        suspend fun enqueue(
            service: ScrobbleService,
            entry: ScrobbleEntry,
        ) {
            context.scrobbleDataStore.edit { preferences ->
                preferences[queueKey(service)] = json.encodeToString((preferences.queue(service) + entry).takeLast(MAX_QUEUE))
            }
        }

        suspend fun pending(service: ScrobbleService): List<ScrobbleEntry> =
            context.scrobbleDataStore.data
                .first()
                .queue(service)

        /** Removes the [sent] entries from the head of the queue, leaving anything queued since. */
        suspend fun drop(
            service: ScrobbleService,
            sent: List<ScrobbleEntry>,
        ) {
            context.scrobbleDataStore.edit { preferences ->
                val queue = preferences.queue(service)
                val remaining = if (queue.take(sent.size) == sent) queue.drop(sent.size) else queue - sent.toSet()
                preferences[queueKey(service)] = json.encodeToString(remaining)
            }
        }

        private fun Preferences.account(service: ScrobbleService): ScrobbleAccount? {
            val sealed = this[secretKey(service)] ?: return null
            val secret = KeystoreSecretBox.open(sealed).takeIf { it.isNotEmpty() } ?: return null
            return ScrobbleAccount(userName = this[nameKey(service)].orEmpty(), secret = secret)
        }

        private fun Preferences.loves(service: ScrobbleService): List<LoveEntry> =
            this[lovesKey(service)]
                ?.let { runCatching { json.decodeFromString<List<LoveEntry>>(it) }.getOrNull() }
                .orEmpty()

        private fun Preferences.queue(service: ScrobbleService): List<ScrobbleEntry> =
            this[queueKey(service)]
                ?.let { runCatching { json.decodeFromString<List<ScrobbleEntry>>(it) }.getOrNull() }
                .orEmpty()

        private companion object {
            const val MAX_QUEUE = 2_000
        }
    }
