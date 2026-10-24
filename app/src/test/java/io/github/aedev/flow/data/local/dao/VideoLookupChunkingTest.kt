package io.github.aedev.flow.data.local.dao

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Guards #1424: a History lookup after a large import passed SQLite's 999-variable limit. */
class VideoLookupChunkingTest {
    @Test
    fun `a long id list is split into queries that stay under the variable limit`() =
        runTest {
            val ids = (1..2_500).map { "video$it" }
            val calls = mutableListOf<List<String>>()

            val rows = chunkedLookup(ids) { chunk -> chunk.also { calls += it } }

            assertThat(calls).hasSize(5)
            assertThat(calls.map { it.size }.max()).isAtMost(MAX_IDS_PER_QUERY)
            assertThat(rows).containsExactlyElementsIn(ids).inOrder()
        }

    @Test
    fun `a repeated id is asked for once`() =
        runTest {
            val calls = mutableListOf<List<String>>()

            chunkedLookup(listOf("a", "b", "a", "b", "c")) { chunk -> chunk.also { calls += it } }

            assertThat(calls).containsExactly(listOf("a", "b", "c"))
        }

    @Test
    fun `no ids sends no query`() =
        runTest {
            var called = false

            val rows = chunkedLookup(emptyList()) { chunk -> chunk.also { called = true } }

            assertThat(called).isFalse()
            assertThat(rows).isEmpty()
        }
}
