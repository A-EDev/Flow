package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.aedev.flow.data.local.entity.MusicGraphAlbumEntity
import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphEdgeEntity
import io.github.aedev.flow.data.local.entity.MusicGraphPlaylistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity

data class MusicGraphTrackEdgeRow(
    val fromId: String,
    @Embedded val track: MusicGraphTrackEntity,
)

@Dao
interface MusicGraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<MusicGraphTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<MusicGraphArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<MusicGraphAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<MusicGraphPlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdges(edges: List<MusicGraphEdgeEntity>)

    @Query("DELETE FROM music_graph_edge WHERE fromId = :fromId AND edgeType IN (:types)")
    suspend fun deleteEdgesFrom(
        fromId: String,
        types: List<String>,
    )

    @Query("SELECT * FROM music_graph_track WHERE videoId = :videoId")
    suspend fun track(videoId: String): MusicGraphTrackEntity?

    @Query("SELECT * FROM music_graph_track WHERE videoId IN (:videoIds)")
    suspend fun tracks(videoIds: List<String>): List<MusicGraphTrackEntity>

    @Query("SELECT * FROM music_graph_artist WHERE browseId = :browseId")
    suspend fun artist(browseId: String): MusicGraphArtistEntity?

    @Query("SELECT * FROM music_graph_artist WHERE browseId IN (:browseIds)")
    suspend fun artists(browseIds: List<String>): List<MusicGraphArtistEntity>

    @Query("SELECT * FROM music_graph_album WHERE browseId IN (:browseIds)")
    suspend fun albums(browseIds: List<String>): List<MusicGraphAlbumEntity>

    @Query("SELECT browseId FROM music_graph_album WHERE browseId IN (:browseIds) AND tracksCrawledAt >= :since")
    suspend fun albumsWithFreshTracks(
        browseIds: List<String>,
        since: Long,
    ): List<String>

    @Query(
        """
        SELECT t.* FROM music_graph_edge e
        JOIN music_graph_track t ON t.videoId = e.toId
        WHERE e.fromId = :fromId AND e.edgeType = :type
        ORDER BY e.rank
        """,
    )
    suspend fun tracksFrom(
        fromId: String,
        type: String,
    ): List<MusicGraphTrackEntity>

    @Query(
        """
        SELECT e.fromId AS fromId, t.* FROM music_graph_edge e
        JOIN music_graph_track t ON t.videoId = e.toId
        WHERE e.edgeType = :type AND e.fromId IN (:fromIds)
        ORDER BY e.fromId, e.rank
        """,
    )
    suspend fun trackEdgesFrom(
        fromIds: List<String>,
        type: String,
    ): List<MusicGraphTrackEdgeRow>

    @Query(
        """
        SELECT a.* FROM music_graph_edge e
        JOIN music_graph_artist a ON a.browseId = e.toId
        WHERE e.fromId = :fromId AND e.edgeType = :type
        ORDER BY e.rank
        """,
    )
    suspend fun artistsFrom(
        fromId: String,
        type: String,
    ): List<MusicGraphArtistEntity>

    @Query(
        """
        SELECT a.* FROM music_graph_edge e
        JOIN music_graph_artist a ON a.browseId = e.toId
        WHERE e.edgeType = :type AND e.fromId IN (:fromIds)
        GROUP BY a.browseId
        ORDER BY COUNT(*) DESC, a.subscriberCount DESC
        LIMIT :limit
        """,
    )
    suspend fun mostLinkedArtists(
        fromIds: List<String>,
        type: String,
        limit: Int,
    ): List<MusicGraphArtistEntity>

    @Query(
        """
        SELECT al.* FROM music_graph_edge e
        JOIN music_graph_album al ON al.browseId = e.toId
        WHERE e.fromId = :fromId AND e.edgeType = :type
        ORDER BY e.rank
        """,
    )
    suspend fun albumsFrom(
        fromId: String,
        type: String,
    ): List<MusicGraphAlbumEntity>

    @Query(
        """
        SELECT p.* FROM music_graph_edge e
        JOIN music_graph_playlist p ON p.playlistId = e.toId
        WHERE e.fromId = :fromId AND e.edgeType = :type
        ORDER BY e.rank
        """,
    )
    suspend fun playlistsFrom(
        fromId: String,
        type: String,
    ): List<MusicGraphPlaylistEntity>

    @Query("DELETE FROM music_graph_edge WHERE lastSeenAt < :before")
    suspend fun deleteEdgesBefore(before: Long)

    @Query(
        """
        DELETE FROM music_graph_edge WHERE rowid IN (
            SELECT e.rowid FROM music_graph_edge e
            WHERE (
                SELECT COUNT(*) FROM music_graph_edge o
                WHERE o.fromId = e.fromId AND o.edgeType = e.edgeType
                    AND (o.lastSeenAt > e.lastSeenAt OR (o.lastSeenAt = e.lastSeenAt AND o.rank < e.rank))
            ) >= :cap
        )
        """,
    )
    suspend fun capEdgesPerSource(cap: Int)

    @Query(
        """
        DELETE FROM music_graph_track WHERE videoId IN (
            SELECT videoId FROM music_graph_track ORDER BY lastSeenAt DESC LIMIT -1 OFFSET :keep
        )
        """,
    )
    suspend fun capTracks(keep: Int)

    @Query(
        """
        DELETE FROM music_graph_edge
        WHERE toId NOT IN (SELECT videoId FROM music_graph_track)
            AND toId NOT IN (SELECT browseId FROM music_graph_artist)
            AND toId NOT IN (SELECT browseId FROM music_graph_album)
            AND toId NOT IN (SELECT playlistId FROM music_graph_playlist)
        """,
    )
    suspend fun deleteDanglingEdges()

    @Query(
        """
        DELETE FROM music_graph_track WHERE lastSeenAt < :before
            AND videoId NOT IN (SELECT toId FROM music_graph_edge)
            AND videoId NOT IN (SELECT fromId FROM music_graph_edge)
        """,
    )
    suspend fun deleteOrphanTracksBefore(before: Long)

    @Query(
        """
        DELETE FROM music_graph_artist WHERE lastSeenAt < :before
            AND browseId NOT IN (SELECT toId FROM music_graph_edge)
            AND browseId NOT IN (SELECT fromId FROM music_graph_edge)
        """,
    )
    suspend fun deleteOrphanArtistsBefore(before: Long)

    @Query(
        """
        DELETE FROM music_graph_album WHERE lastSeenAt < :before
            AND browseId NOT IN (SELECT toId FROM music_graph_edge)
            AND browseId NOT IN (SELECT fromId FROM music_graph_edge)
        """,
    )
    suspend fun deleteOrphanAlbumsBefore(before: Long)

    @Query("DELETE FROM music_graph_playlist WHERE lastSeenAt < :before AND playlistId NOT IN (SELECT toId FROM music_graph_edge)")
    suspend fun deleteOrphanPlaylistsBefore(before: Long)

    @Query("SELECT COUNT(*) FROM music_graph_track")
    suspend fun trackCount(): Int

    @Query("SELECT COUNT(*) FROM music_graph_edge")
    suspend fun edgeCount(): Int
}
