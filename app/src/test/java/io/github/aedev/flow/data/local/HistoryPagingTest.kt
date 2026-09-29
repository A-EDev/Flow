package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryPagingTest {
    private data class Row(
        val id: String,
        val timestamp: Long,
    )

    /** Answers the DAO's keyset query over [rows] and counts the calls. */
    private class FakeTable(
        var rows: List<Row>,
    ) {
        var reads = 0
        var afterRead: (Int) -> Unit = {}

        fun page(
            beforeTimestamp: Long,
            afterVideoId: String,
            limit: Int,
        ): List<Row> {
            reads++
            return rows
                .filter { it.timestamp < beforeTimestamp || (it.timestamp == beforeTimestamp && it.id > afterVideoId) }
                .sortedWith(compareByDescending<Row> { it.timestamp }.thenBy { it.id })
                .take(limit)
                .also { afterRead(reads) }
        }
    }

    private suspend fun FakeTable.readAll(pageSize: Int): List<Row> =
        readAllPages(
            pageSize = pageSize,
            readPage = { before, after, limit -> page(before, after, limit) },
            timestamp = { it.timestamp },
            videoId = { it.id },
        )

    private fun newestFirst(rows: List<Row>) = rows.sortedWith(compareByDescending<Row> { it.timestamp }.thenBy { it.id })

    @Test
    fun `reads every row newest first across pages`() =
        runTest {
            val rows = (1..1_234L).map { Row("v$it", timestamp = it) }
            val table = FakeTable(rows)

            assertThat(table.readAll(pageSize = 100)).containsExactlyElementsIn(newestFirst(rows)).inOrder()
            assertThat(table.reads).isEqualTo(13)
        }

    @Test
    fun `rows sharing a timestamp across a page boundary are neither skipped nor repeated`() =
        runTest {
            val rows = (1..25).map { Row("v%02d".format(it), timestamp = if (it <= 20) 500L else it.toLong()) }
            val table = FakeTable(rows)

            assertThat(table.readAll(pageSize = 7)).containsExactlyElementsIn(newestFirst(rows)).inOrder()
        }

    @Test
    fun `an exact multiple of the page size ends on one empty read`() =
        runTest {
            val table = FakeTable((1..20L).map { Row("v$it", it) })

            assertThat(table.readAll(pageSize = 10)).hasSize(20)
            assertThat(table.reads).isEqualTo(3)
        }

    @Test
    fun `an empty table is one read`() =
        runTest {
            val table = FakeTable(emptyList())

            assertThat(table.readAll(pageSize = 10)).isEmpty()
            assertThat(table.reads).isEqualTo(1)
        }

    @Test
    fun `a row moved into a later page while reading is kept once`() =
        runTest {
            val table = FakeTable((1..30L).map { Row("v$it", it) })
            table.afterRead = { read ->
                if (read == 1) table.rows = table.rows.map { if (it.id == "v30") it.copy(timestamp = 0L) else it }
            }

            val ids = table.readAll(pageSize = 10).map { it.id }

            assertThat(ids).containsNoDuplicates()
            assertThat(ids).hasSize(30)
        }
}
