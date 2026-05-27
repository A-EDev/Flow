//==================================================================================================
//This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
//==================================================================================================

package io.github.aedev.flow.data.lyrics.paxsenix.models

import com.google.gson.annotations.SerializedName

data class SearchResult(
    val id: String = "",
    val songName: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Int? = null,
    val artwork: String? = null
) {
    val displayName: String get() = trackName ?: songName ?: ""
    val displayArtist: String get() = artistName ?: ""
}

data class LyricsContent(
    val timestamp: Long = 0,
    val endtime: Long = 0,
    val duration: Long = 0,
    val structure: String? = null,
    val text: List<LyricText> = emptyList(),
    val background: Boolean = false,
    val backgroundText: List<LyricText> = emptyList(),
    val oppositeTurn: Boolean = false
)

data class LyricText(
    val text: String = "",
    val timestamp: Long = 0,
    val endtime: Long = 0,
    val duration: Long = 0,
    val part: Boolean = false
)

data class LyricsResponse(
    val type: String? = null,
    val content: List<LyricsContent> = emptyList(),
    val elrc: String? = null,
    val elrcMultiPerson: String? = null,
    val ttmlContent: String? = null,
    val plain: String? = null
)

data class AppleMusicSearchResponse(
    val results: AppleMusicResults = AppleMusicResults(),
    val resources: AppleMusicResources? = null
)

data class AppleMusicResults(
    val songs: AppleMusicSongsResult? = null
)

data class AppleMusicSongsResult(
    val data: List<AppleMusicSongData> = emptyList()
)

data class AppleMusicSongData(
    val id: String = "",
    val type: String = ""
)

data class AppleMusicResources(
    val songs: Map<String, AppleMusicSongDetail>? = null
)

data class AppleMusicSongDetail(
    val attributes: AppleMusicSongAttributes = AppleMusicSongAttributes()
)

data class AppleMusicSongAttributes(
    val name: String = "",
    val artistName: String = "",
    val albumName: String? = null,
    val artwork: AppleMusicArtwork? = null,
    val url: String? = null,
    val durationInMillis: Long? = null
)

data class AppleMusicArtwork(
    val url: String = ""
)
