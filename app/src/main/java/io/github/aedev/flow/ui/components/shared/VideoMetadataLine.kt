package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.formatPremiereDate
import io.github.aedev.flow.utils.formatViewCount

@Composable
fun videoMetadataLine(
    video: Video,
    isUpcoming: Boolean,
    channelName: String = video.channelName,
    includeChannel: Boolean = false,
): String {
    if (isUpcoming) {
        return formatPremiereDate(video.uploadDate)
            ?.let { stringResource(R.string.premiere_date_prefix, it) }
            ?: stringResource(R.string.premiere_soon)
    }

    val dateSettings = rememberDateDisplaySettings()
    val uploadedAt =
        remember(video.uploadDate, video.timestamp, dateSettings) {
            dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
        }

    if (video.viewCount < 0L) {
        return stringResource(R.string.video_metadata_short_template, channelName, uploadedAt)
    }

    val views = stringResource(R.string.views_template, formatViewCount(video.viewCount))
    return if (includeChannel) {
        stringResource(R.string.video_metadata_template, channelName, views, uploadedAt)
    } else {
        stringResource(R.string.video_metadata_short_template, views, uploadedAt)
    }
}
