package io.github.aedev.flow.ui.screens.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedMusicScreenTest {
    @Test
    fun spotlightOpensExistingPlayerWhenItRepresentsTheActiveTrack() {
        val activeTrack = musicTrack("active")

        assertTrue(shouldOpenExistingMusicPlayer(activeTrack, activeTrack.copy(title = "Updated title")))
    }

    @Test
    fun spotlightLoadsNormallyWhenItRepresentsAnotherTrack() {
        assertFalse(shouldOpenExistingMusicPlayer(musicTrack("active"), musicTrack("recommended")))
    }

    private fun musicTrack(videoId: String): MusicTrack =
        MusicTrack(
            videoId = videoId,
            title = "Track $videoId",
            artist = "Flow Studio",
            thumbnailUrl = "https://example.com/$videoId.jpg",
            duration = 180,
        )
}
