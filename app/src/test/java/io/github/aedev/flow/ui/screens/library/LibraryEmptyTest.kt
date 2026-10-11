package io.github.aedev.flow.ui.screens.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryEmptyTest {
    private val nothing = LibraryCounts(0, 0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun `nothing stored and nothing downloading is an empty library`() {
        assertThat(libraryIsEmpty(nothing, activeDownloads = 0)).isTrue()
    }

    @Test
    fun `a download in progress keeps the library from looking empty`() {
        assertThat(libraryIsEmpty(nothing, activeDownloads = 1)).isFalse()
    }

    @Test
    fun `nothing is decided before both counts are known`() {
        assertThat(libraryIsEmpty(null, activeDownloads = 0)).isFalse()
        assertThat(libraryIsEmpty(nothing, activeDownloads = null)).isFalse()
    }

    @Test
    fun `stored content is never empty`() {
        assertThat(libraryIsEmpty(nothing.copy(history = 3), activeDownloads = 0)).isFalse()
    }
}
