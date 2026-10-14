package io.github.aedev.flow.player

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

class MusicRadioStationTest {
    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = "Artist", thumbnailUrl = "", duration = 200)

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        EnhancedMusicPlayerManager.setRadioStationActive(false)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `a new queue ends a started station even when it shares a track with it`() {
        EnhancedMusicPlayerManager.setRadioStationActive(true)

        EnhancedMusicPlayerManager.playTrack(track("shared"), audioUrl = "", queue = listOf(track("shelf"), track("shared")))

        assertThat(EnhancedMusicPlayerManager.radioStationActive.value).isFalse()
    }
}
