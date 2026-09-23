package io.github.aedev.flow.data.stats

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.di.NetworkIoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recapImages by preferencesDataStore(name = "flow_recap_images")
private val IMAGES = stringPreferencesKey("images")

/**
 * Portraits for the recap. Local sources come first: artwork the music engine keeps per track,
 * avatars subscriptions keep, and avatars fetched on an earlier visit. Only the channels a recap is
 * about to show and none of those know are fetched, a few at a time, once each; what comes back is
 * kept on disk so the next visit shows it straight away.
 */
@Singleton
class RecapImageResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val musicBrain: MusicBrainEngine,
        private val subscriptions: SubscriptionRepository,
        private val repository: YouTubeRepository,
        @NetworkIoDispatcher private val networkDispatcher: CoroutineDispatcher,
    ) {
        private val attempted = Collections.synchronizedSet(HashSet<String>())
        private val serializer = MapSerializer(String.serializer(), String.serializer())

        /** Every portrait the device already has, keyed by channel id or artist key. */
        suspend fun localImages(): Map<String, String> {
            val images = LinkedHashMap<String, String>()
            runCatching { musicBrain.artistArtwork() }.getOrNull()?.let(images::putAll)
            runCatching { subscriptions.getAllSubscriptions().first() }
                .getOrNull()
                ?.forEach { if (it.channelThumbnail.isNotBlank()) images[it.channelId] = it.channelThumbnail }
            images.putAll(cached())
            return images
        }

        /** Fetches the avatars [summary] would show but has none for; returns only what was found. */
        suspend fun fetchMissing(summary: RecapSummary): Map<String, String> {
            val wanted =
                summary
                    .shownPortraits()
                    .filter { it.imageUrl.isBlank() && it.id.startsWith(CHANNEL_PREFIX) && attempted.add(it.id) }
                    .map { it.id }
                    .distinct()
                    .take(MAX_FETCHES)
            if (wanted.isEmpty()) return emptyMap()
            val found =
                coroutineScope {
                    wanted
                        .chunked(PARALLEL)
                        .flatMap { batch ->
                            batch
                                .map { id ->
                                    async(networkDispatcher) {
                                        id to
                                            withTimeoutOrNull(
                                                FETCH_TIMEOUT_MS,
                                            ) { runCatching { repository.fetchChannelAvatarById(id) }.getOrNull() }
                                    }
                                }.awaitAll()
                        }
                }.mapNotNull { (id, url) -> url?.takeIf { it.isNotBlank() }?.let { id to it } }
                    .toMap()
            if (found.isNotEmpty()) remember(found)
            Log.d(TAG, "Fetched ${found.size} of ${wanted.size} recap portraits")
            return found
        }

        private suspend fun cached(): Map<String, String> =
            runCatching {
                context.recapImages.data
                    .first()[IMAGES]
                    ?.let { LedgerJson.decodeFromString(serializer, it) }
            }.getOrNull().orEmpty()

        private suspend fun remember(found: Map<String, String>) =
            withContext(networkDispatcher) {
                context.recapImages.edit { preferences ->
                    val merged =
                        LinkedHashMap(
                            preferences[IMAGES]?.let { runCatching { LedgerJson.decodeFromString(serializer, it) }.getOrNull() }.orEmpty(),
                        )
                    merged.putAll(found)
                    val kept =
                        merged.entries
                            .toList()
                            .takeLast(MAX_CACHED)
                            .associate { it.key to it.value }
                    preferences[IMAGES] = LedgerJson.encodeToString(serializer, kept)
                }
            }

        private companion object {
            const val TAG = "RecapImageResolver"
            const val CHANNEL_PREFIX = "UC"
            const val MAX_FETCHES = 16
            const val PARALLEL = 4
            const val FETCH_TIMEOUT_MS = 6_000L
            const val MAX_CACHED = 400
        }
    }

/** The ranked items whose portraits a recap shows, in the order it shows them. */
internal fun RecapSummary.shownPortraits(): List<RankedItem> =
    video.topChannels.take(SHOWN_RANKS) +
        video.discoveredChannels.take(SHOWN_FACES) +
        music.topArtists.take(SHOWN_RANKS) +
        music.discoveredArtists.take(SHOWN_FACES) +
        video.skippedChannels.take(SHOWN_SKIPS)

/** Fills every portrait that is blank from [images], keyed by channel id or artist key. */
fun RecapSummary.withImages(images: Map<String, String>): RecapSummary {
    if (images.isEmpty()) return this

    fun List<RankedItem>.filled() =
        map { item ->
            if (item.imageUrl.isBlank()) {
                images[item.id]?.let {
                    item.copy(imageUrl = it)
                } ?: item
            } else {
                item
            }
        }
    return copy(
        video =
            video.copy(
                topChannels = video.topChannels.filled(),
                discoveredChannels = video.discoveredChannels.filled(),
                skippedChannels = video.skippedChannels.filled(),
            ),
        music =
            music.copy(
                topArtists = music.topArtists.filled(),
                discoveredArtists = music.discoveredArtists.filled(),
                skippedArtists = music.skippedArtists.filled(),
                dislikedArtists = music.dislikedArtists.filled(),
                blockedArtists = music.blockedArtists.filled(),
            ),
    )
}

private const val SHOWN_RANKS = 5
private const val SHOWN_FACES = 8
private const val SHOWN_SKIPS = 3
