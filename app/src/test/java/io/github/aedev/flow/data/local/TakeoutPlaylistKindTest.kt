package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TakeoutPlaylistKindTest {
    @Test
    fun `a playlist of known songs is music`() {
        assertThat(isTakeoutMusicPlaylist(listOf("a", "b", "c"), setOf("a", "b", "c"))).isTrue()
    }

    @Test
    fun `three songs in five is enough`() {
        assertThat(isTakeoutMusicPlaylist(listOf("a", "b", "c", "x", "y"), setOf("a", "b", "c"))).isTrue()
    }

    @Test
    fun `two songs in five stays a video playlist`() {
        assertThat(isTakeoutMusicPlaylist(listOf("a", "b", "x", "y", "z"), setOf("a", "b"))).isFalse()
    }

    @Test
    fun `a repeated song counts once`() {
        assertThat(isTakeoutMusicPlaylist(listOf("a", "a", "a", "x", "y"), setOf("a"))).isFalse()
    }

    @Test
    fun `nothing known about the export keeps every playlist as video`() {
        assertThat(isTakeoutMusicPlaylist(listOf("a", "b"), emptySet())).isFalse()
        assertThat(isTakeoutMusicPlaylist(emptyList(), setOf("a"))).isFalse()
    }
}
