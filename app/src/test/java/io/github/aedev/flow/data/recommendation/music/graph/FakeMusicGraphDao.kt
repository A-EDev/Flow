package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.local.dao.MusicGraphDao
import io.github.aedev.flow.data.local.dao.MusicGraphTrackEdgeRow
import io.github.aedev.flow.data.local.entity.MusicGraphAlbumEntity
import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphEdgeEntity
import io.github.aedev.flow.data.local.entity.MusicGraphPlaylistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity

class FakeMusicGraphDao : MusicGraphDao {
    val tracks = LinkedHashMap<String, MusicGraphTrackEntity>()
    val artists = LinkedHashMap<String, MusicGraphArtistEntity>()
    val albums = LinkedHashMap<String, MusicGraphAlbumEntity>()
    val playlists = LinkedHashMap<String, MusicGraphPlaylistEntity>()
    val edges = LinkedHashMap<Triple<String, String, String>, MusicGraphEdgeEntity>()

    private fun MusicGraphEdgeEntity.key() = Triple(fromId, edgeType, toId)

    private fun edgesFrom(
        fromId: String,
        type: String,
    ) = edges.values.filter { it.fromId == fromId && it.edgeType == type }.sortedBy { it.rank }

    private fun isLinked(id: String) = edges.values.any { it.toId == id || it.fromId == id }

    override suspend fun upsertTracks(tracks: List<MusicGraphTrackEntity>) = tracks.forEach { this.tracks[it.videoId] = it }

    override suspend fun upsertArtists(artists: List<MusicGraphArtistEntity>) = artists.forEach { this.artists[it.browseId] = it }

    override suspend fun upsertAlbums(albums: List<MusicGraphAlbumEntity>) = albums.forEach { this.albums[it.browseId] = it }

    override suspend fun upsertPlaylists(playlists: List<MusicGraphPlaylistEntity>) =
        playlists.forEach { this.playlists[it.playlistId] = it }

    override suspend fun upsertEdges(edges: List<MusicGraphEdgeEntity>) = edges.forEach { this.edges[it.key()] = it }

    override suspend fun deleteEdgesFrom(
        fromId: String,
        types: List<String>,
    ) {
        edges.values.removeAll { it.fromId == fromId && it.edgeType in types }
    }

    override suspend fun track(videoId: String) = tracks[videoId]

    override suspend fun tracks(videoIds: List<String>) = videoIds.mapNotNull { tracks[it] }

    override suspend fun artist(browseId: String) = artists[browseId]

    override suspend fun artists(browseIds: List<String>) = browseIds.mapNotNull { artists[it] }

    override suspend fun albums(browseIds: List<String>) = browseIds.mapNotNull { albums[it] }

    override suspend fun albumsWithFreshTracks(
        browseIds: List<String>,
        since: Long,
    ) = browseIds.filter { (albums[it]?.tracksCrawledAt ?: Long.MIN_VALUE) >= since }

    override suspend fun sourcesWithFreshEdges(
        fromIds: List<String>,
        type: String,
        since: Long,
    ) = edges.values
        .filter { it.edgeType == type && it.fromId in fromIds && it.lastSeenAt >= since }
        .map { it.fromId }
        .distinct()

    override suspend fun tracksFrom(
        fromId: String,
        type: String,
    ) = edgesFrom(fromId, type).mapNotNull { tracks[it.toId] }

    override suspend fun trackEdgesFrom(
        fromIds: List<String>,
        type: String,
    ) = fromIds.flatMap { from -> edgesFrom(from, type).mapNotNull { edge -> tracks[edge.toId]?.let { MusicGraphTrackEdgeRow(from, it) } } }

    override suspend fun artistsFrom(
        fromId: String,
        type: String,
    ) = edgesFrom(fromId, type).mapNotNull { artists[it.toId] }

    override suspend fun mostLinkedArtists(
        fromIds: List<String>,
        type: String,
        limit: Int,
    ) = fromIds
        .flatMap { edgesFrom(it, type) }
        .groupBy { it.toId }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<MusicGraphEdgeEntity>>> { it.value.size }
                .thenByDescending { artists[it.key]?.subscriberCount ?: 0L },
        ).mapNotNull { artists[it.key] }
        .take(limit)

    override suspend fun albumsFrom(
        fromId: String,
        type: String,
    ) = edgesFrom(fromId, type).mapNotNull { albums[it.toId] }

    override suspend fun playlistsFrom(
        fromId: String,
        type: String,
    ) = edgesFrom(fromId, type).mapNotNull { playlists[it.toId] }

    override suspend fun deleteEdgesBefore(before: Long) {
        edges.values.removeAll { it.lastSeenAt < before }
    }

    override suspend fun capEdgesPerSource(cap: Int) {
        edges.values
            .groupBy { it.fromId to it.edgeType }
            .values
            .forEach { group ->
                group
                    .sortedWith(compareByDescending<MusicGraphEdgeEntity> { it.lastSeenAt }.thenBy { it.rank })
                    .drop(cap)
                    .forEach { edges.remove(it.key()) }
            }
    }

    override suspend fun capTracks(keep: Int) {
        tracks.values
            .sortedByDescending { it.lastSeenAt }
            .drop(keep)
            .forEach { tracks.remove(it.videoId) }
    }

    override suspend fun deleteDanglingEdges() {
        edges.values.removeAll { it.toId !in tracks && it.toId !in artists && it.toId !in albums && it.toId !in playlists }
    }

    override suspend fun deleteOrphanTracksBefore(before: Long) {
        tracks.values.removeAll { it.lastSeenAt < before && !isLinked(it.videoId) }
    }

    override suspend fun deleteOrphanArtistsBefore(before: Long) {
        artists.values.removeAll { it.lastSeenAt < before && !isLinked(it.browseId) }
    }

    override suspend fun deleteOrphanAlbumsBefore(before: Long) {
        albums.values.removeAll { it.lastSeenAt < before && !isLinked(it.browseId) }
    }

    override suspend fun deleteOrphanPlaylistsBefore(before: Long) {
        playlists.values.removeAll { it.lastSeenAt < before && !isLinked(it.playlistId) }
    }

    override suspend fun trackCount() = tracks.size

    override suspend fun edgeCount() = edges.size
}
