package io.github.aedev.flow.data.music.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicTitleKeyTest {
    @Test
    fun `titles match across case, accents, brackets, features and the artist prefix`() {
        assertThat(musicTitleKey("The Weeknd - BLINDING LIGHTS [4K]", listOf("The Weeknd"))).isEqualTo("blinding lights")
        assertThat(musicTitleKey("Levitating (feat. DaBaby)")).isEqualTo("levitating")
        assertThat(musicTitleKey("Anti-Hero ft. Bleachers")).isEqualTo("anti hero")
        assertThat(musicTitleKey("Café del Mar")).isEqualTo("cafe del mar")
    }

    @Test
    fun `an artist channel's Topic suffix is not part of the name`() {
        assertThat(musicArtistKey("Fairuz - Topic")).isEqualTo(musicArtistKey("FAIRUZ"))
    }
}
