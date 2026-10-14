package io.github.aedev.flow.data.local.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadWithItemsTest {
    @Test
    fun `a download with no file rows is not finished`() {
        assertThat(download().overallStatus).isEqualTo(DownloadItemStatus.PENDING)
        assertThat(download().primaryFilePath).isNull()
    }

    @Test
    fun `a download is finished only when every file row is`() {
        assertThat(download(DownloadItemStatus.COMPLETED).overallStatus).isEqualTo(DownloadItemStatus.COMPLETED)
        assertThat(download(DownloadItemStatus.COMPLETED, DownloadItemStatus.PAUSED).overallStatus)
            .isEqualTo(DownloadItemStatus.PAUSED)
    }

    @Test
    fun `the worst status wins`() {
        assertThat(download(DownloadItemStatus.FAILED, DownloadItemStatus.DOWNLOADING).overallStatus)
            .isEqualTo(DownloadItemStatus.DOWNLOADING)
        assertThat(download(DownloadItemStatus.COMPLETED, DownloadItemStatus.FAILED).overallStatus)
            .isEqualTo(DownloadItemStatus.FAILED)
    }

    @Test
    fun `the video file is the primary file, otherwise the audio file`() {
        val both =
            DownloadWithItems(
                DownloadEntity(videoId = "v", title = "t", uploader = "u"),
                listOf(item(DownloadItemStatus.COMPLETED, DownloadFileType.AUDIO, "/a.m4a"), item(DownloadItemStatus.COMPLETED)),
            )
        assertThat(both.primaryFilePath).isEqualTo("/v.mp4")
        assertThat(both.isAudioOnly).isFalse()
    }

    private fun download(vararg statuses: DownloadItemStatus) =
        DownloadWithItems(
            DownloadEntity(videoId = "v", title = "t", uploader = "u"),
            statuses.mapIndexed { index, status -> item(status, path = "/v$index.mp4") }.let { items ->
                if (items.size == 1) listOf(item(statuses.first())) else items
            },
        )

    private fun item(
        status: DownloadItemStatus,
        type: DownloadFileType = DownloadFileType.VIDEO,
        path: String = "/v.mp4",
    ) = DownloadItemEntity(videoId = "v", fileType = type, fileName = path.substringAfterLast('/'), filePath = path, status = status)
}
