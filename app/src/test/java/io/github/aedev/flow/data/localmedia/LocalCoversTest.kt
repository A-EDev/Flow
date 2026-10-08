package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalCoversTest {
    @Test
    fun `the most specific cover name wins, in any case`() {
        assertThat(folderCoverName(listOf("01 Song.opus", "Folder.JPG", "cover.png"))).isEqualTo("cover.png")
        assertThat(folderCoverName(listOf("back.jpg", "FRONT.jpeg"))).isEqualTo("FRONT.jpeg")
    }

    @Test
    fun `windows media albumart files count`() {
        assertThat(folderCoverName(listOf("AlbumArt_{ABC}_Large.jpg"))).isEqualTo("AlbumArt_{ABC}_Large.jpg")
    }

    @Test
    fun `other images and look-alike names are not covers`() {
        assertThat(folderCoverName(listOf("booklet.jpg", "cover.txt", "covers.jpg", "back.png"))).isNull()
    }
}
