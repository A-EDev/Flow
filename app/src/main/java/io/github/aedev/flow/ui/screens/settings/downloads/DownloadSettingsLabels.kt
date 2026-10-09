package io.github.aedev.flow.ui.screens.settings.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AutoDownloadMode
import io.github.aedev.flow.data.video.downloader.work.RetagStatus

@Composable
internal fun LocationUi.label(): String =
    if (notWritable) stringResource(R.string.download_location_not_writable, saveFolder) else saveFolder

internal fun autoDownloadLabel(mode: AutoDownloadMode): Int =
    when (mode) {
        AutoDownloadMode.OFF -> R.string.off
        AutoDownloadMode.WIFI -> R.string.auto_download_wifi
        AutoDownloadMode.ALWAYS -> R.string.auto_download_always
    }

@Composable
internal fun retagLabel(status: RetagStatus): String =
    when (status) {
        RetagStatus.Waiting -> stringResource(R.string.download_retag_waiting)
        is RetagStatus.Running -> stringResource(R.string.download_retag_progress, status.done, status.total)
        is RetagStatus.Finished -> stringResource(R.string.download_retag_result, status.result.tagged, status.result.skipped)
    }

@Composable
internal fun earlierDownloadsLabel(search: EarlierDownloadsSearch): String =
    when (search) {
        EarlierDownloadsSearch.Running -> {
            stringResource(R.string.downloads_find_earlier_running)
        }

        is EarlierDownloadsSearch.Found -> {
            if (search.count > 0) {
                pluralStringResource(R.plurals.downloads_recovered_count, search.count, search.count)
            } else {
                stringResource(R.string.downloads_recovered_none)
            }
        }
    }
