package io.github.aedev.flow.data.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CachedSongMigrationTest {
    @Test
    fun `a cached song with no row is downloaded as a file`() {
        val plan = CachedSongMigration.of(listOf("a"), cached = setOf("a"), hasFile = { false }, hasRow = { false })

        assertThat(plan.queue).containsExactly("a")
        assertThat(plan.drop).isEmpty()
    }

    @Test
    fun `a cached copy is dropped only once its file exists`() {
        val plan = CachedSongMigration.of(listOf("a", "b"), cached = setOf("a", "b"), hasFile = { it == "a" }, hasRow = { true })

        assertThat(plan.drop).containsExactly("a")
        assertThat(plan.queue).isEmpty()
    }

    @Test
    fun `a song already queued or failed is left to its row`() {
        val plan = CachedSongMigration.of(listOf("a"), cached = setOf("a"), hasFile = { false }, hasRow = { true })

        assertThat(plan.queue).isEmpty()
        assertThat(plan.drop).isEmpty()
    }

    @Test
    fun `songs the cache never finished are not touched`() {
        val plan = CachedSongMigration.of(listOf("a", "a", "b"), cached = setOf("a"), hasFile = { false }, hasRow = { false })

        assertThat(plan.queue).containsExactly("a")
    }
}
