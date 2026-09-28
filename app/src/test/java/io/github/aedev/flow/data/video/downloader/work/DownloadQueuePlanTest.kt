package io.github.aedev.flow.data.video.downloader.work

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.QueuedDownload
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import org.junit.Test

class DownloadQueuePlanTest {
    private fun row(
        id: String,
        status: DownloadItemStatus = DownloadItemStatus.PENDING,
        createdAt: Long = 0,
    ) = QueuedDownload(id, createdAt, status)

    @Test
    fun `waiting downloads start oldest first up to the limit`() {
        val plan = DownloadQueuePlan.of(listOf(row("a"), row("b"), row("c")), running = emptySet(), limit = 2)

        assertThat(plan.toStart).containsExactly("a", "b").inOrder()
        assertThat(plan.toStop).isEmpty()
    }

    @Test
    fun `running downloads count against the limit`() {
        val plan =
            DownloadQueuePlan.of(
                listOf(row("a", DownloadItemStatus.DOWNLOADING), row("b"), row("c")),
                running = setOf("a"),
                limit = 2,
            )

        assertThat(plan.toStart).containsExactly("b")
    }

    @Test
    fun `a paused or removed download is stopped and frees its slot`() {
        val plan =
            DownloadQueuePlan.of(
                listOf(row("a", DownloadItemStatus.PAUSED), row("c")),
                running = setOf("a", "b"),
                limit = 1,
            )

        assertThat(plan.toStop).containsExactly("a", "b")
        assertThat(plan.toStart).containsExactly("c")
    }

    @Test
    fun `a lowered limit only holds new downloads back`() {
        val plan =
            DownloadQueuePlan.of(
                listOf(row("a", DownloadItemStatus.DOWNLOADING), row("b", DownloadItemStatus.DOWNLOADING), row("c")),
                running = setOf("a", "b"),
                limit = 1,
            )

        assertThat(plan.toStop).isEmpty()
        assertThat(plan.toStart).isEmpty()
    }
}
