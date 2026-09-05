package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity

object MusicGraphSections {
    const val DEEP_CUTS_PER_ALBUM = 2

    fun deepCuts(
        albumTracks: Map<String, List<MusicGraphTrackEntity>>,
        albumPriority: List<String>,
        playedVideoIds: Set<String>,
        playedCanonicalKeys: Set<String>,
        limit: Int,
    ): List<MusicGraphTrackEntity> {
        val priority = albumPriority.withIndex().associate { it.value to it.index }
        return albumTracks.entries
            .sortedBy { priority[it.key] ?: Int.MAX_VALUE }
            .flatMap { (_, tracks) ->
                tracks
                    .filterNot { it.isVideo || it.videoId in playedVideoIds || it.canonicalKey in playedCanonicalKeys }
                    .sortedByDescending { it.engagement() }
                    .take(DEEP_CUTS_PER_ALBUM)
            }.distinctBy { it.canonicalKey }
            .take(limit)
    }

    fun artistsForYou(
        candidates: List<MusicGraphArtistEntity>,
        excludedArtistKeys: Set<String>,
        limit: Int,
    ): List<MusicGraphArtistEntity> =
        candidates
            .filterNot { it.browseId in excludedArtistKeys || it.name.trim().lowercase() in excludedArtistKeys }
            .take(limit)

    private fun MusicGraphTrackEntity.engagement(): Double {
        val views = viewCount ?: return 0.0
        val likes = likeCount ?: return 0.0
        return if (views > 0) likes.toDouble() / views else 0.0
    }
}
