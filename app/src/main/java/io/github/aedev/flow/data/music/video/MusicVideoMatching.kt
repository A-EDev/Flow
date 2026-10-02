package io.github.aedev.flow.data.music.video

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.musicArtistKey
import io.github.aedev.flow.data.music.model.musicTitleKey
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import io.github.aedev.flow.utils.foldForSearch

/**
 * Picks a song's official music video out of a video search. Signed-out responses never carry
 * YouTube Music's own song-to-video link, so the match is the artist's official upload of the
 * same title, never a live take, remix, cover or a static "official audio" upload.
 */
internal object MusicVideoMatching {
    private val NonWord = Regex("""[^\p{L}\p{N}]+""")

    // An official upload of the same title in one of these forms is a different recording or no video.
    private val OtherVersions =
        listOf(
            "live",
            "remix",
            "cover",
            "karaoke",
            "instrumental",
            "sped up",
            "slowed",
            "reverb",
            "acoustic",
            "teaser",
            "trailer",
            "nightcore",
            "extended",
            "audio",
            "visualizer",
            "visualiser",
            "behind the scenes",
        )

    fun pick(
        song: MusicTrack,
        candidates: List<SongItem>,
    ): SongItem? = candidates.firstOrNull { isOfficialVideoOf(song, it) }

    fun isOfficialVideoOf(
        song: MusicTrack,
        candidate: SongItem,
    ): Boolean =
        candidate.musicVideoType == MUSIC_VIDEO_TYPE_OMV &&
            sameArtist(song, candidate) &&
            musicTitleKey(candidate.title, song.artistNames()) == musicTitleKey(song.title, song.artistNames()) &&
            OtherVersions.none { it.isWordIn(candidate.title) && !it.isWordIn(song.title) } &&
            plausibleLength(song.duration, candidate.duration)

    private fun sameArtist(
        song: MusicTrack,
        candidate: SongItem,
    ): Boolean {
        val songIds = (song.artists.mapNotNull { it.id } + song.channelId).filter(String::isNotBlank).toSet()
        if (candidate.artists.any { it.id != null && it.id in songIds }) return true
        val names = song.artistNames().map(::musicArtistKey).toSet()
        return candidate.artists.any { musicArtistKey(it.name) in names }
    }

    private fun MusicTrack.artistNames(): List<String> = artists.map { it.name }.ifEmpty { listOf(artist) }

    private fun String.isWordIn(title: String): Boolean = " ${title.foldForSearch().replace(NonWord, " ")} ".contains(" $this ")

    // An intro or outro makes a video longer than its song, but not twice as long and never half.
    private fun plausibleLength(
        songSeconds: Int,
        videoSeconds: Int?,
    ): Boolean {
        if (songSeconds <= 0 || videoSeconds == null || videoSeconds <= 0) return true
        return videoSeconds * 2 >= songSeconds && videoSeconds <= songSeconds * 2 + MAX_EXTRA_SECONDS
    }

    private const val MAX_EXTRA_SECONDS = 60
}
