package io.github.aedev.flow.data.recommendation.music.graph

import android.util.Log
import io.github.aedev.flow.data.local.dao.MusicGraphDao
import io.github.aedev.flow.data.local.entity.MusicGraphAlbumEntity
import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphEdgeEntity
import io.github.aedev.flow.data.local.entity.MusicGraphPlaylistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.music.model.RelatedMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class MusicGraphEdgeType {
    RELATED_SIMILAR,
    RADIO,
    RELATED_PLAYLIST,
    ARTIST_SIMILAR,
    ARTIST_TRACK,
    ARTIST_ALBUM,
    ARTIST_SINGLE,
    ARTIST_FEATURED_ON,
    ALBUM_TRACK,
}

@Singleton
class MusicGraphStore
    @Inject
    constructor(
        private val dao: MusicGraphDao,
    ) {
        private companion object {
            const val TAG = "MusicGraph"
            const val DAY_MS = 24 * 60 * 60 * 1000L
            const val RELATED_TTL_MS = 7 * DAY_MS
            const val ARTIST_TTL_MS = 7 * DAY_MS
            const val ALBUM_TRACKS_TTL_MS = 30 * DAY_MS
            const val NODE_RETENTION_MS = 60 * DAY_MS
            const val EDGES_PER_SOURCE = 64
            const val MAX_TRACKS = 20_000
            const val ARTIST_CANDIDATES = 40
            const val SQL_CHUNK = 400
        }

        private val trimmed = AtomicBoolean(false)
        private val trimLock = Mutex()

        suspend fun relatedFor(seedId: String): RelatedMusic? =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                val now = System.currentTimeMillis()
                val seed = dao.track(seedId) ?: return@withContext null
                val crawledAt = seed.relatedCrawledAt ?: return@withContext null
                if (now - crawledAt > RELATED_TTL_MS) return@withContext null
                val seedArtistId = seed.artistId
                RelatedMusic(
                    seed = seed.toTrack(),
                    seedArtistId = seedArtistId,
                    tracks = dao.tracksFrom(seedId, MusicGraphEdgeType.RELATED_SIMILAR.name).filterNot { it.isVideo }.map { it.toTrack() },
                    radioTracks = dao.tracksFrom(seedId, MusicGraphEdgeType.RADIO.name).filterNot { it.isVideo }.map { it.toTrack() },
                    similarArtists =
                        seedArtistId?.let { dao.artistsFrom(it, MusicGraphEdgeType.ARTIST_SIMILAR.name) }.orEmpty().map { it.toDetails() },
                    playlists = dao.playlistsFrom(seedId, MusicGraphEdgeType.RELATED_PLAYLIST.name).map { it.toPlaylist() },
                    artistAlbums =
                        seedArtistId?.let { dao.albumsFrom(it, MusicGraphEdgeType.ARTIST_ALBUM.name) }.orEmpty().map { it.toPlaylist() },
                )
            }

        suspend fun recordRelated(
            seedId: String,
            seed: MusicTrack?,
            related: RelatedMusic,
        ) = withContext(Dispatchers.IO) {
            ensureTrimmed()
            val now = System.currentTimeMillis()
            val seedTrack = related.seed ?: seed
            val seedArtistId = related.seedArtistId ?: seedTrack?.primaryArtistId()
            val tracks =
                buildList {
                    seedTrack?.let {
                        add(
                            it.toEntity(now).copy(
                                videoId = seedId,
                                artistId = seedArtistId ?: it.primaryArtistId(),
                                relatedCrawledAt = now,
                            ),
                        )
                    }
                    related.tracks.forEach { add(it.toEntity(now)) }
                    related.radioTracks.forEach { add(it.toEntity(now)) }
                }
            if (seedTrack == null) {
                dao.track(seedId)?.let { dao.upsertTracks(listOf(it.copy(relatedCrawledAt = now, lastSeenAt = now))) }
            }
            upsertTracksMerged(tracks)
            upsertArtistsMerged(related.similarArtists.map { it.toEntity(now) })
            dao.upsertPlaylists(related.playlists.map { it.toPlaylistEntity(now) })
            upsertAlbumsMerged(related.artistAlbums.map { it.toAlbumEntity(now) })

            dao.deleteEdgesFrom(
                seedId,
                listOf(MusicGraphEdgeType.RELATED_SIMILAR.name, MusicGraphEdgeType.RADIO.name, MusicGraphEdgeType.RELATED_PLAYLIST.name),
            )
            val edges =
                buildList {
                    addAll(related.tracks.map { it.videoId }.toEdges(seedId, MusicGraphEdgeType.RELATED_SIMILAR, now))
                    addAll(
                        related.radioTracks
                            .map { it.videoId }
                            .filter { it != seedId }
                            .toEdges(seedId, MusicGraphEdgeType.RADIO, now),
                    )
                    addAll(related.playlists.map { it.id }.toEdges(seedId, MusicGraphEdgeType.RELATED_PLAYLIST, now))
                    if (seedArtistId != null) {
                        if (related.similarArtists.isNotEmpty()) {
                            dao.deleteEdgesFrom(seedArtistId, listOf(MusicGraphEdgeType.ARTIST_SIMILAR.name))
                        }
                        addAll(related.similarArtists.map { it.channelId }.toEdges(seedArtistId, MusicGraphEdgeType.ARTIST_SIMILAR, now))
                        addAll(related.artistAlbums.map { it.id }.toEdges(seedArtistId, MusicGraphEdgeType.ARTIST_ALBUM, now))
                    }
                }
            dao.upsertEdges(edges)
        }

        suspend fun artistFor(browseId: String): ArtistDetails? =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                val now = System.currentTimeMillis()
                val artist = dao.artist(browseId) ?: return@withContext null
                val crawledAt = artist.crawledAt ?: return@withContext null
                if (now - crawledAt > ARTIST_TTL_MS) return@withContext null
                artist.toDetails().copy(
                    topTracks = dao.tracksFrom(browseId, MusicGraphEdgeType.ARTIST_TRACK.name).map { it.toTrack() },
                    albums = dao.albumsFrom(browseId, MusicGraphEdgeType.ARTIST_ALBUM.name).map { it.toPlaylist() },
                    singles = dao.albumsFrom(browseId, MusicGraphEdgeType.ARTIST_SINGLE.name).map { it.toPlaylist() },
                    relatedArtists = dao.artistsFrom(browseId, MusicGraphEdgeType.ARTIST_SIMILAR.name).map { it.toDetails() },
                    featuredOn = dao.playlistsFrom(browseId, MusicGraphEdgeType.ARTIST_FEATURED_ON.name).map { it.toPlaylist() },
                )
            }

        suspend fun recordArtist(details: ArtistDetails) =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                if (details.channelId.isBlank()) return@withContext
                val now = System.currentTimeMillis()
                upsertArtistsMerged(listOf(details.toEntity(now).copy(crawledAt = now)) + details.relatedArtists.map { it.toEntity(now) })
                upsertTracksMerged(details.topTracks.map { it.toEntity(now) })
                upsertAlbumsMerged((details.albums + details.singles).map { it.toAlbumEntity(now) })
                dao.upsertPlaylists(details.featuredOn.map { it.toPlaylistEntity(now) })
                dao.deleteEdgesFrom(
                    details.channelId,
                    listOf(
                        MusicGraphEdgeType.ARTIST_TRACK.name,
                        MusicGraphEdgeType.ARTIST_ALBUM.name,
                        MusicGraphEdgeType.ARTIST_SINGLE.name,
                        MusicGraphEdgeType.ARTIST_SIMILAR.name,
                        MusicGraphEdgeType.ARTIST_FEATURED_ON.name,
                    ),
                )
                dao.upsertEdges(
                    details.topTracks.map { it.videoId }.toEdges(details.channelId, MusicGraphEdgeType.ARTIST_TRACK, now) +
                        details.albums.map { it.id }.toEdges(details.channelId, MusicGraphEdgeType.ARTIST_ALBUM, now) +
                        details.singles.map { it.id }.toEdges(details.channelId, MusicGraphEdgeType.ARTIST_SINGLE, now) +
                        details.relatedArtists.map { it.channelId }.toEdges(details.channelId, MusicGraphEdgeType.ARTIST_SIMILAR, now) +
                        details.featuredOn.map { it.id }.toEdges(details.channelId, MusicGraphEdgeType.ARTIST_FEATURED_ON, now),
                )
            }

        suspend fun recordAlbum(details: PlaylistDetails) =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                val now = System.currentTimeMillis()
                upsertAlbumsMerged(
                    listOf(
                        MusicGraphAlbumEntity(
                            browseId = details.id,
                            title = details.title,
                            artistId = details.authorId,
                            artistName = details.author,
                            thumbnailUrl = details.thumbnailUrl,
                            year = details.description?.toIntOrNull(),
                            tracksCrawledAt = now,
                            lastSeenAt = now,
                        ),
                    ),
                )
                upsertTracksMerged(details.tracks.map { it.toEntity(now).copy(albumId = details.id, albumTitle = details.title) })
                dao.deleteEdgesFrom(details.id, listOf(MusicGraphEdgeType.ALBUM_TRACK.name))
                dao.upsertEdges(details.tracks.map { it.videoId }.toEdges(details.id, MusicGraphEdgeType.ALBUM_TRACK, now))
            }

        suspend fun albumIdsNeedingTracks(albumIds: List<String>): List<String> =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                if (albumIds.isEmpty()) return@withContext emptyList()
                val since = System.currentTimeMillis() - ALBUM_TRACKS_TTL_MS
                val fresh = albumIds.chunked(SQL_CHUNK).flatMap { dao.albumsWithFreshTracks(it, since) }.toSet()
                albumIds.filterNot { it in fresh }
            }

        suspend fun deepCuts(
            albumIds: List<String>,
            played: List<MusicTrack>,
            limit: Int,
        ): List<MusicTrack> =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                if (albumIds.isEmpty()) return@withContext emptyList()
                val rows = albumIds.chunked(SQL_CHUNK).flatMap { dao.trackEdgesFrom(it, MusicGraphEdgeType.ALBUM_TRACK.name) }
                MusicGraphSections
                    .deepCuts(
                        albumTracks = rows.groupBy({ it.fromId }, { it.track }),
                        albumPriority = albumIds,
                        playedVideoIds = played.mapTo(HashSet()) { it.videoId },
                        playedCanonicalKeys = played.mapTo(HashSet()) { it.canonicalKey() },
                        limit = limit,
                    ).map { it.toTrack() }
            }

        suspend fun artistsForYou(
            seedArtistIds: List<String>,
            excludedArtistKeys: Set<String>,
            limit: Int,
        ): List<ArtistDetails> =
            withContext(Dispatchers.IO) {
                ensureTrimmed()
                if (seedArtistIds.isEmpty()) return@withContext emptyList()
                val candidates =
                    dao.mostLinkedArtists(seedArtistIds.take(SQL_CHUNK), MusicGraphEdgeType.ARTIST_SIMILAR.name, ARTIST_CANDIDATES)
                MusicGraphSections.artistsForYou(candidates, excludedArtistKeys, limit).map { it.toDetails() }
            }

        private suspend fun ensureTrimmed() {
            if (trimmed.get()) return
            trimLock.withLock {
                if (trimmed.get()) return
                val now = System.currentTimeMillis()
                dao.deleteEdgesBefore(now - NODE_RETENTION_MS)
                dao.capEdgesPerSource(EDGES_PER_SOURCE)
                dao.capTracks(MAX_TRACKS)
                dao.deleteDanglingEdges()
                dao.deleteOrphanTracksBefore(now - NODE_RETENTION_MS)
                dao.deleteOrphanArtistsBefore(now - NODE_RETENTION_MS)
                dao.deleteOrphanAlbumsBefore(now - NODE_RETENTION_MS)
                dao.deleteOrphanPlaylistsBefore(now - NODE_RETENTION_MS)
                Log.d(TAG, "trimmed: tracks=${dao.trackCount()} edges=${dao.edgeCount()}")
                trimmed.set(true)
            }
        }

        private suspend fun upsertTracksMerged(incoming: List<MusicGraphTrackEntity>) {
            if (incoming.isEmpty()) return
            val distinct = incoming.associateBy { it.videoId }
            val existing =
                distinct.keys
                    .chunked(SQL_CHUNK)
                    .flatMap { dao.tracks(it) }
                    .associateBy { it.videoId }
            dao.upsertTracks(
                distinct.values.map { track ->
                    val old = existing[track.videoId] ?: return@map track
                    track.copy(
                        viewCount = track.viewCount ?: old.viewCount,
                        likeCount = track.likeCount ?: old.likeCount,
                        albumId = track.albumId ?: old.albumId,
                        albumTitle = track.albumTitle.ifBlank { old.albumTitle },
                        artistId = track.artistId ?: old.artistId,
                        relatedCrawledAt = track.relatedCrawledAt ?: old.relatedCrawledAt,
                    )
                },
            )
        }

        private suspend fun upsertArtistsMerged(incoming: List<MusicGraphArtistEntity>) {
            if (incoming.isEmpty()) return
            val distinct = incoming.filter { it.browseId.isNotBlank() }.associateBy { it.browseId }
            val existing =
                distinct.keys
                    .chunked(SQL_CHUNK)
                    .flatMap { dao.artists(it) }
                    .associateBy { it.browseId }
            dao.upsertArtists(
                distinct.values.map { artist ->
                    val old = existing[artist.browseId] ?: return@map artist
                    artist.copy(
                        thumbnailUrl = artist.thumbnailUrl.ifBlank { old.thumbnailUrl },
                        subscriberCount = maxOf(artist.subscriberCount, old.subscriberCount),
                        crawledAt = artist.crawledAt ?: old.crawledAt,
                    )
                },
            )
        }

        private suspend fun upsertAlbumsMerged(incoming: List<MusicGraphAlbumEntity>) {
            if (incoming.isEmpty()) return
            val distinct = incoming.filter { it.browseId.isNotBlank() }.associateBy { it.browseId }
            val existing =
                distinct.keys
                    .chunked(SQL_CHUNK)
                    .flatMap { dao.albums(it) }
                    .associateBy { it.browseId }
            dao.upsertAlbums(
                distinct.values.map { album ->
                    val old = existing[album.browseId] ?: return@map album
                    album.copy(
                        artistId = album.artistId ?: old.artistId,
                        year = album.year ?: old.year,
                        tracksCrawledAt = album.tracksCrawledAt ?: old.tracksCrawledAt,
                    )
                },
            )
        }

        private fun List<String>.toEdges(
            fromId: String,
            type: MusicGraphEdgeType,
            now: Long,
        ): List<MusicGraphEdgeEntity> =
            filter { it.isNotBlank() && it != fromId }
                .distinct()
                .mapIndexed { index, toId ->
                    MusicGraphEdgeEntity(fromId = fromId, edgeType = type.name, toId = toId, rank = index, lastSeenAt = now)
                }

        private fun MusicTrack.primaryArtistId(): String? = artists.firstOrNull()?.id ?: channelId.takeIf { it.isNotBlank() }

        private fun MusicTrack.toEntity(now: Long) =
            MusicGraphTrackEntity(
                videoId = videoId,
                canonicalKey = canonicalKey(),
                title = title,
                artist = artist,
                artistId = primaryArtistId(),
                albumId = albumId,
                albumTitle = album,
                thumbnailUrl = thumbnailUrl,
                durationSec = duration,
                isVideo = isVideoSong,
                isExplicit = isExplicit == true,
                viewCount = views.takeIf { it > 0 },
                likeCount = likes.takeIf { it > 0 },
                relatedCrawledAt = null,
                lastSeenAt = now,
            )

        private fun MusicGraphTrackEntity.toTrack() =
            MusicTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                duration = durationSec,
                views = viewCount ?: 0,
                likes = likeCount ?: 0,
                album = albumTitle,
                channelId = artistId.orEmpty(),
                isExplicit = isExplicit,
                isVideoSong = isVideo,
                albumId = albumId,
                artists = listOf(MusicArtist(name = artist, id = artistId)),
            )

        private fun ArtistDetails.toEntity(now: Long) =
            MusicGraphArtistEntity(
                browseId = channelId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount,
                crawledAt = null,
                lastSeenAt = now,
            )

        private fun MusicGraphArtistEntity.toDetails() =
            ArtistDetails(
                name = name,
                channelId = browseId,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount,
            )

        private fun MusicPlaylist.toAlbumEntity(now: Long) =
            MusicGraphAlbumEntity(
                browseId = id,
                title = title,
                artistId = authorId,
                artistName = authorName.orEmpty(),
                thumbnailUrl = thumbnailUrl,
                year = author.toIntOrNull(),
                tracksCrawledAt = null,
                lastSeenAt = now,
            )

        private fun MusicGraphAlbumEntity.toPlaylist() =
            MusicPlaylist(
                id = browseId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                author = year?.toString().orEmpty(),
                authorId = artistId,
                authorName = artistName.ifBlank { null },
            )

        private fun MusicPlaylist.toPlaylistEntity(now: Long) =
            MusicGraphPlaylistEntity(
                playlistId = id,
                title = title,
                author = author,
                authorId = authorId,
                thumbnailUrl = thumbnailUrl,
                lastSeenAt = now,
            )

        private fun MusicGraphPlaylistEntity.toPlaylist() =
            MusicPlaylist(
                id = playlistId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                author = author,
                authorId = authorId,
                authorName = author.ifBlank { null },
            )
    }
