package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Discovery search results are filed as music by the same conventions the NewPipe mapping read. */
class MusicVideoGuessTest {
    @Test
    fun `a vevo uploader is music`() {
        assertThat(looksLikeMusicVideo("Song", "ArtistVEVO")).isTrue()
    }

    @Test
    fun `an auto-generated topic channel is music`() {
        assertThat(looksLikeMusicVideo("Song", "Artist - Topic")).isTrue()
    }

    @Test
    fun `an official music video is music`() {
        assertThat(looksLikeMusicVideo("Artist - Song (Official Music Video)", "Artist")).isTrue()
    }

    @Test
    fun `an official video is music`() {
        assertThat(looksLikeMusicVideo("Artist - Song [Official Video]", "Artist")).isTrue()
    }

    @Test
    fun `an official audio upload is music`() {
        assertThat(looksLikeMusicVideo("Artist - Song (Official Audio)", "Artist")).isTrue()
    }

    @Test
    fun `a title marked official in brackets is music`() {
        assertThat(looksLikeMusicVideo("Artist - Song (Official)", "Artist")).isTrue()
    }

    @Test
    fun `an ordinary upload is not music`() {
        assertThat(looksLikeMusicVideo("Building a PC in 10 minutes", "Tech Channel")).isFalse()
    }
}
