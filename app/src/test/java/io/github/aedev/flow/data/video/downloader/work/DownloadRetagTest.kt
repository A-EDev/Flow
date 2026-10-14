package io.github.aedev.flow.data.video.downloader.work

import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class DownloadRetagTest {
    private fun row(
        videoId: String = "abc",
        path: String = "/storage/emulated/0/Download/Flow/Video.mp4",
        status: DownloadItemStatus = DownloadItemStatus.COMPLETED,
        requestJson: String? = null,
    ) = DownloadWithItems(
        download = DownloadEntity(videoId = videoId, title = "Video", uploader = "Channel", requestJson = requestJson),
        items =
            listOf(
                DownloadItemEntity(
                    videoId = videoId,
                    fileType = DownloadFileType.VIDEO,
                    fileName = path.substringAfterLast('/'),
                    filePath = path,
                    format = "mp4",
                    status = status,
                ),
            ),
    )

    @Test
    fun `an older finished mp4 or m4a the app saved is retagged`() {
        assertThat(DownloadRetagger.isCandidate(row())).isTrue()
        assertThat(DownloadRetagger.isCandidate(row(path = "/Music/Flow/Song.m4a"))).isTrue()
    }

    @Test
    fun `new, unfinished, recovered, untaggable and picked-folder downloads are left alone`() {
        assertThat(DownloadRetagger.isCandidate(row(requestJson = "{}"))).isFalse()
        assertThat(DownloadRetagger.isCandidate(row(status = DownloadItemStatus.PAUSED))).isFalse()
        assertThat(DownloadRetagger.isCandidate(row(videoId = "recovered_123"))).isFalse()
        assertThat(DownloadRetagger.isCandidate(row(path = "/Download/Flow/Video.mkv"))).isFalse()
        assertThat(DownloadRetagger.isCandidate(row(path = "content://com.android.externalstorage.documents/document/1"))).isFalse()
    }

    private fun work(
        state: WorkInfo.State,
        done: Int = 0,
        total: Int = 0,
    ): WorkInfo =
        mockk {
            every { this@mockk.state } returns state
            every { progress } returns workDataOf(DownloadRetagWorker.KEY_DONE to done, DownloadRetagWorker.KEY_TOTAL to total)
        }

    @Test
    fun `the status reads the running work's progress, then the saved result`() {
        assertThat(RetagStatus.of(null, null)).isEqualTo(RetagStatus.Waiting)
        assertThat(RetagStatus.of(work(WorkInfo.State.ENQUEUED), null)).isEqualTo(RetagStatus.Waiting)
        assertThat(RetagStatus.of(work(WorkInfo.State.RUNNING), null)).isEqualTo(RetagStatus.Waiting)
        assertThat(RetagStatus.of(work(WorkInfo.State.RUNNING, done = 3, total = 12), null)).isEqualTo(RetagStatus.Running(3, 12))
        assertThat(RetagStatus.of(work(WorkInfo.State.SUCCEEDED), RetagResult(10, 2))).isEqualTo(RetagStatus.Finished(RetagResult(10, 2)))
    }
}
