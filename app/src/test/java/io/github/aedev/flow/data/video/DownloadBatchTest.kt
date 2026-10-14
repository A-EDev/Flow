package io.github.aedev.flow.data.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadBatchTest {
    @Test
    fun `counts each outcome and finishes after the last video`() {
        val batch =
            DownloadBatch(collectionId = "PL1", total = 3)
                .record(QueueOutcome.QUEUED)
                .record(QueueOutcome.ALREADY_PRESENT)

        assertThat(batch.isFinished).isFalse()

        val done = batch.record(QueueOutcome.UNAVAILABLE)

        assertThat(done.isFinished).isTrue()
        assertThat(done.queued).isEqualTo(1)
        assertThat(done.skipped).isEqualTo(1)
        assertThat(done.processed).isEqualTo(3)
    }
}
