package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs Last.fm's listeners pair with what the viewer has on repeat: a second opinion beside
 * YouTube's own suggestions. Only for viewers signed in to Last.fm, fetched when the shelf is first
 * shown and kept for [TTL_MS] while the seeds stay the same.
 */
@Singleton
class LastFmDiscovery
    @Inject
    constructor(
        private val scrobbler: Scrobbler,
        private val musicBrain: MusicBrainEngine,
    ) {
        private class Cached(
            val seeds: List<String>,
            val atMs: Long,
            val tracks: List<MusicTrack>,
        )

        private val lock = Mutex()
        private var cached: Cached? = null

        suspend fun tracks(): List<MusicTrack> =
            lock.withLock {
                withContext(PerformanceDispatcher.networkIO) {
                    val seeds = musicBrain.heavyRotationTracks(SEEDS)
                    val seedIds = seeds.map { it.videoId }
                    val now = System.currentTimeMillis()
                    cached?.takeIf { it.seeds == seedIds && now - it.atMs < TTL_MS }?.let { return@withContext it.tracks }
                    if (seeds.isEmpty()) return@withContext emptyList()
                    val similar =
                        seeds.map { seed ->
                            val artist = seed.artists.firstOrNull()?.name ?: seed.artist
                            scrobbler.similarTracks(artist, seed.title, PER_SEED).getOrDefault(emptyList())
                        }
                    val picks =
                        discoveryPicks(
                            similar = similar,
                            seeds = seeds.map { (it.artists.firstOrNull()?.name ?: it.artist) to it.title },
                            hiddenArtists = musicBrain.hiddenArtists.value,
                            limit = SHOWN,
                        )
                    resolve(picks).also { cached = Cached(seedIds, now, it) }
                }
            }

        /** One bounded round of searches per chunk; a song is kept only when its artist matches. */
        private suspend fun resolve(picks: List<Pair<String, String>>): List<MusicTrack> =
            picks
                .chunked(PARALLEL_SEARCHES)
                .flatMap { chunk ->
                    coroutineScope {
                        chunk.map { (artist, title) -> async { runCatching { findSong(artist, title) }.getOrNull() } }.awaitAll()
                    }
                }.filterNotNull()
                .distinctBy { it.videoId }

        private suspend fun findSong(
            artist: String,
            title: String,
        ): MusicTrack? {
            val wanted = artist.matchKey()
            return YouTube
                .search("$artist $title", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                ?.firstOrNull { song -> song.artists.any { it.name.matchKey() == wanted } }
                ?.let(InnertubeMusicService::convertToMusicTrack)
        }

        private companion object {
            const val SEEDS = 3
            const val PER_SEED = 15
            const val SHOWN = 12
            const val PARALLEL_SEARCHES = 4
            const val TTL_MS = 6 * 60 * 60 * 1000L
        }
    }

/**
 * Takes from each seed in turn, so one song's neighbourhood cannot fill the shelf, and leaves out
 * the seeds themselves and any artist the viewer hid.
 */
internal fun discoveryPicks(
    similar: List<List<Pair<String, String>>>,
    seeds: List<Pair<String, String>>,
    hiddenArtists: Set<String>,
    limit: Int,
): List<Pair<String, String>> {
    val seedKeys = seeds.mapTo(HashSet()) { (artist, title) -> artist.matchKey() to title.matchKey() }
    val hidden = hiddenArtists.mapTo(HashSet()) { it.matchKey() }
    val longest = similar.maxOfOrNull { it.size } ?: 0
    return (0 until longest)
        .flatMap { index -> similar.mapNotNull { it.getOrNull(index) } }
        .filterNot { (artist, title) -> (artist.matchKey() to title.matchKey()) in seedKeys || artist.matchKey() in hidden }
        .distinctBy { (artist, title) -> artist.matchKey() to title.matchKey() }
        .take(limit)
}
