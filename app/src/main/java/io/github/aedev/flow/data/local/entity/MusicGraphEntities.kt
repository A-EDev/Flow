package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_graph_track",
    indices = [Index("canonicalKey"), Index("albumId"), Index("lastSeenAt")],
)
data class MusicGraphTrackEntity(
    @PrimaryKey val videoId: String,
    val canonicalKey: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val albumId: String?,
    val albumTitle: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val isVideo: Boolean,
    val isExplicit: Boolean,
    val viewCount: Long?,
    val likeCount: Long?,
    val relatedCrawledAt: Long?,
    val lastSeenAt: Long,
)

@Entity(tableName = "music_graph_artist", indices = [Index("lastSeenAt")])
data class MusicGraphArtistEntity(
    @PrimaryKey val browseId: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val crawledAt: Long?,
    val lastSeenAt: Long,
)

@Entity(tableName = "music_graph_album", indices = [Index("artistId"), Index("lastSeenAt")])
data class MusicGraphAlbumEntity(
    @PrimaryKey val browseId: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val thumbnailUrl: String,
    val year: Int?,
    val tracksCrawledAt: Long?,
    val lastSeenAt: Long,
)

@Entity(tableName = "music_graph_playlist", indices = [Index("lastSeenAt")])
data class MusicGraphPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val author: String,
    val authorId: String?,
    val thumbnailUrl: String,
    val lastSeenAt: Long,
)

@Entity(
    tableName = "music_graph_edge",
    primaryKeys = ["fromId", "edgeType", "toId"],
    indices = [Index("toId"), Index("lastSeenAt")],
)
data class MusicGraphEdgeEntity(
    val fromId: String,
    val edgeType: String,
    val toId: String,
    val rank: Int,
    val lastSeenAt: Long,
)
