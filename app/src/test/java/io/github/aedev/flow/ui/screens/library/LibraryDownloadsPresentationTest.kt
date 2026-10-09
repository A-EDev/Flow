package io.github.aedev.flow.ui.screens.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryDownloadsPresentationTest {
    @Test
    fun `a shelf still loading keeps its placeholder`() {
        assertThat(libraryDownloadsPresentation(null)).isEqualTo(LibraryDownloadsPresentation.Loading)
    }

    @Test
    fun `no finished download stays a row so the page can be opened`() {
        assertThat(libraryDownloadsPresentation(0)).isEqualTo(LibraryDownloadsPresentation.Row)
    }

    @Test
    fun `finished downloads stay a shelf`() {
        assertThat(libraryDownloadsPresentation(3)).isEqualTo(LibraryDownloadsPresentation.Shelf)
    }

    @Test
    fun `an in-progress download keeps the library from looking empty`() {
        assertThat(libraryLooksEmpty(countsKnown = true, countsEmpty = true, activeDownloads = 1)).isFalse()
    }

    @Test
    fun `unknown counts are not treated as an empty library`() {
        assertThat(libraryLooksEmpty(countsKnown = false, countsEmpty = true, activeDownloads = 0)).isFalse()
        assertThat(libraryLooksEmpty(countsKnown = true, countsEmpty = true, activeDownloads = null)).isFalse()
    }

    @Test
    fun `a library with nothing stored and nothing downloading is empty`() {
        assertThat(libraryLooksEmpty(countsKnown = true, countsEmpty = true, activeDownloads = 0)).isTrue()
        assertThat(libraryLooksEmpty(countsKnown = true, countsEmpty = false, activeDownloads = 0)).isFalse()
    }
}
