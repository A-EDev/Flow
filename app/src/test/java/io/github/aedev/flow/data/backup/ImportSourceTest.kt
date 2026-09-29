package io.github.aedev.flow.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportSourceTest {
    @Test
    fun `every import is offered by exactly one source`() {
        val offered = ImportSource.entries.flatMap { it.kinds }

        assertThat(offered).containsExactlyElementsIn(ImportKind.entries)
        assertThat(offered).containsNoDuplicates()
    }
}
