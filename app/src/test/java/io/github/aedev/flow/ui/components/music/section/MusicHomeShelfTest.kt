package io.github.aedev.flow.ui.components.music.section

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicHomeShelfTest {
    @Test
    fun `every feed section can be hidden`() {
        HomeSectionType.entries.forEach { type -> assertThat(MusicHomeShelf.of(type).name).isEqualTo(type.name) }
    }

    @Test
    fun `stored names that no longer exist are ignored`() {
        assertThat(MusicHomeShelf.fromStored(setOf("CHARTS", "RETIRED_SHELF")))
            .containsExactly(MusicHomeShelf.CHARTS)
    }
}
