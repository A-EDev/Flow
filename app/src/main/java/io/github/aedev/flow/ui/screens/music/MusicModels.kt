package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.serialization.Serializable

enum class MusicItemType { SONG, ALBUM, PLAYLIST, ARTIST }

/**
 * Play-source prefix marking a genre/mood-scoped surface (genre rows, mood
 * chips). The player strips it for the "Playing from" label and hands the genre
 * to the music brain as listen-context provenance.
 */
const val MUSIC_GENRE_SOURCE_PREFIX = "genre:"

@Serializable
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
    val isExplicit: Boolean? = false,
    val isVideoSong: Boolean = false,
    val albumId: String? = null,
    val artists: List<MusicArtist> = emptyList(),
    val itemType: MusicItemType = MusicItemType.SONG,
) {
    val highResThumbnailUrl: String
        get() = ThumbnailUrlResolver.resolveMusicThumbnail(videoId, thumbnailUrl, 1080)

    val listThumbnailUrl: String
        get() = ThumbnailUrlResolver.resolveMusicThumbnail(videoId, thumbnailUrl, 256)
}

@Serializable
data class MusicArtist(
    val name: String,
    val id: String? = null,
)

/**
 * Repairs a Gson-deserialized track whose [MusicTrack.artists] may hold untyped maps
 * instead of [MusicArtist] objects: release builds that lose the field's generic
 * signature make Gson fall back to LinkedTreeMap entries, which crash with a
 * ClassCastException on first element access (issue #996). filterIsInstance performs
 * only instanceof checks, so it is safe on a poisoned list; bad entries are dropped
 * and the plain [MusicTrack.artist]/[MusicTrack.channelId] fallbacks take over.
 * Call it on every Gson read path that yields a [MusicTrack].
 */
fun MusicTrack.withTypedArtists(): MusicTrack {
    val raw: List<*> = artists
    return if (raw.all { it is MusicArtist }) this else copy(artists = raw.filterIsInstance<MusicArtist>())
}

data class DailyDiscoverItem(
    val seed: MusicTrack,
    val recommendation: MusicTrack,
)

data class CommunityMusicPlaylist(
    val playlist: MusicPlaylist,
    val tracks: List<MusicTrack>,
)

data class MusicPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val trackCount: Int = 0,
    val author: String = "",
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
    val tracks: List<MusicTrack> = emptyList(),
    val continuation: String? = null,
)

data class ArtistDetails(
    val name: String,
    val channelId: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String = "",
    val bannerUrl: String = "",
    val topTracks: List<MusicTrack> = emptyList(),
    val albums: List<MusicPlaylist> = emptyList(),
    val singles: List<MusicPlaylist> = emptyList(),
    val videos: List<MusicTrack> = emptyList(),
    val relatedArtists: List<ArtistDetails> = emptyList(),
    val featuredOn: List<MusicPlaylist> = emptyList(),
    val isSubscribed: Boolean = false,
    val albumsBrowseId: String? = null,
    val albumsParams: String? = null,
    val singlesBrowseId: String? = null,
    val singlesParams: String? = null,
    val topTracksBrowseId: String? = null,
    val topTracksParams: String? = null,
)
