package io.github.aedev.flow.data.music.video

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The video form of a song for the player's Song/Video switch. A track that is already a video is
 * its own video; any other song is matched to its official music video by one search, and the
 * answer, found or not, is remembered for the session.
 */
@Singleton
class MusicVideoVersions internal constructor(
    private val searchVideos: suspend (String) -> List<SongItem>,
) {
    @Inject
    constructor() : this({ query ->
        withContext(PerformanceDispatcher.networkIO) {
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_VIDEO)
                .getOrThrow()
                .items
                .filterIsInstance<SongItem>()
        }
    })

    private val known = LinkedHashMap<String, MusicTrack?>()

    /**
     * The track to play when the viewer wants to watch [song]: itself when it has a picture, its
     * official video otherwise, or null when it has none. Throws when the search itself failed.
     */
    suspend fun videoFor(song: MusicTrack): MusicTrack? {
        if (song.isVideoSong) return song
        synchronized(known) { if (song.videoId in known) return known[song.videoId] }
        val artist = song.artists.firstOrNull()?.name ?: song.artist
        val match = MusicVideoMatching.pick(song, searchVideos("$artist ${song.title}"))
        val video =
            match?.let {
                song.copy(
                    videoId = it.id,
                    duration = it.duration ?: song.duration,
                    isVideoSong = true,
                )
            }
        synchronized(known) {
            known[song.videoId] = video
            if (known.size > MAX_REMEMBERED) known.remove(known.keys.first())
        }
        return video
    }

    private companion object {
        const val MAX_REMEMBERED = 300
    }
}
