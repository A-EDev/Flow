package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalMediaReindexTest {
    private val root = "/storage/emulated/0"

    @Test
    fun `only folders holding a missing file are scanned`() {
        val paths = listOf("$root/Movies/a.mp4", "$root/Movies/b.mp4", "$root/Music/c.mp3")
        val missing = setOf("$root/Movies/b.mp4")

        assertThat(foldersToReindex(paths) { it !in missing }).containsExactly("$root/Movies")
    }

    @Test
    fun `a folder inside another scanned one is left to that scan`() {
        val paths = listOf("$root/Movies/a.mp4", "$root/Movies/Trips/b.mp4", "$root/Movies Old/c.mp4")

        assertThat(foldersToReindex(paths) { false }).containsExactly("$root/Movies", "$root/Movies Old").inOrder()
    }

    @Test
    fun `nothing is scanned when every file is where MediaStore says`() {
        assertThat(foldersToReindex(listOf("$root/Movies/a.mp4")) { true }).isEmpty()
    }
}
