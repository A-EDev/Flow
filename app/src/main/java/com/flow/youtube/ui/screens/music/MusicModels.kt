package com.flow.youtube.ui.screens.music

data class MusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Int,
    val views: Long = 0,
    val sourceUrl: String = "", // Full URL for NewPipe extraction
    val album: String = "",
    val channelId: String = "",
    val isExplicit: Boolean? = false
)

data class MusicPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val trackCount: Int = 0,
    val author: String = ""
)

data class PlaylistDetails(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val author: String,
    val authorId: String? = null,
    val authorAvatarUrl: String? = null,
    val trackCount: Int,
    val description: String? = null,
    val views: Long? = null,
    val durationText: String? = null,
    val dateText: String? = null,
    val tracks: List<MusicTrack> = emptyList()
)

data class ArtistDetails(
    val name: String,
    val channelId: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String,
    val bannerUrl: String,
    val topTracks: List<MusicTrack>,
    val albums: List<MusicPlaylist> = emptyList(),
    val singles: List<MusicPlaylist> = emptyList(),
    val videos: List<MusicTrack> = emptyList(),
    val relatedArtists: List<ArtistDetails> = emptyList(),
    val featuredOn: List<MusicPlaylist> = emptyList(),
    val isSubscribed: Boolean = false
)
