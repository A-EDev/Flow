package io.github.aedev.flow.data.music.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTrackFiltersTest {
    private fun track(
        id: String,
        duration: Int = 200,
        isVideoSong: Boolean = false,
        itemType: MusicItemType = MusicItemType.SONG,
    ) = MusicTrack(id, "t", "a", "", duration, isVideoSong = isVideoSong, itemType = itemType)

    @Test
    fun keepsSongsAndDropsVideosAlbumsAndOddLengths() {
        val tracks =
            listOf(
                track("song"),
                track("unknown-length", duration = 0),
                track("video", isVideoSong = true),
                track("album", itemType = MusicItemType.ALBUM),
                track("clip", duration = 12),
                track("mix", duration = 3600),
                track(""),
            )
        assertEquals(listOf("song", "unknown-length"), tracks.audioMusicOnly().map { it.videoId })
    }

    @Test
    fun keepsTheFirstOfEachId() {
        assertEquals(listOf("a", "b"), listOf(track("a"), track("b"), track("a")).audioMusicOnly().map { it.videoId })
    }
}
