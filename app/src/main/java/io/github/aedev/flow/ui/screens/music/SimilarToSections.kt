package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicItemType
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.MusicSection
import kotlin.random.Random

internal data class SimilarToBlock(
    val similar: MusicSection,
    val otherPerformances: MusicSection?,
    val moreFromArtist: MusicSection?,
)

internal object SimilarToSections {
    const val MIN_EXTRAS = 2
    const val MAX_EXTRAS = 3
    private const val LEADING_SONGS = 2
    private const val TOP_ARTIST_POOL = 10
    private const val TOP_ARTIST_PICKS = 2

    /**
     * Two of the most played artists, each with the first history track that names them. Distinct by
     * channel: "Artist" and "Artist & Guest" can share one, and two blocks on one seed repeat a feed key.
     */
    fun topArtistSeeds(
        history: List<MusicTrack>,
        random: Random,
    ): List<Pair<String, MusicTrack>> =
        history
            .groupBy { it.artist }
            .entries
            .sortedByDescending { it.value.size }
            .map { (name, plays) -> name to plays.first() }
            .filter { (_, track) -> track.channelId.isNotBlank() }
            .distinctBy { (_, track) -> track.channelId }
            .take(TOP_ARTIST_POOL)
            .shuffled(random)
            .take(TOP_ARTIST_PICKS)

    fun mixed(
        songs: List<MusicTrack>,
        artists: List<ArtistDetails>,
        playlists: List<MusicPlaylist>,
        random: Random,
    ): List<MusicTrack> {
        if (songs.isEmpty()) return songs
        val extras =
            artists.take(random.nextInt(MIN_EXTRAS, MAX_EXTRAS + 1)).map { it.asCollectionTrack() } +
                playlists.take(random.nextInt(MIN_EXTRAS, MAX_EXTRAS + 1)).map { it.asCollectionTrack(MusicItemType.PLAYLIST) }
        if (extras.isEmpty()) return songs
        val firstGap = minOf(LEADING_SONGS, songs.size)
        val gaps = (firstGap..songs.size).shuffled(random).take(extras.size).sorted()
        val shuffledExtras = extras.shuffled(random)
        val result = ArrayList<MusicTrack>(songs.size + gaps.size)
        var gapIndex = 0
        songs.forEachIndexed { index, song ->
            if (gapIndex < gaps.size && gaps[gapIndex] == index) result.add(shuffledExtras[gapIndex++])
            result.add(song)
        }
        while (gapIndex < gaps.size) result.add(shuffledExtras[gapIndex++])
        return result
    }
}

internal fun ArtistDetails.asCollectionTrack(): MusicTrack =
    MusicTrack(
        videoId = channelId,
        title = name,
        artist = name,
        thumbnailUrl = thumbnailUrl,
        duration = 0,
        channelId = channelId,
        itemType = MusicItemType.ARTIST,
    )

internal fun MusicPlaylist.asCollectionTrack(itemType: MusicItemType): MusicTrack =
    MusicTrack(
        videoId = id,
        title = title,
        artist = authorName ?: author,
        thumbnailUrl = thumbnailUrl,
        duration = 0,
        channelId = authorId.orEmpty(),
        itemType = itemType,
    )
