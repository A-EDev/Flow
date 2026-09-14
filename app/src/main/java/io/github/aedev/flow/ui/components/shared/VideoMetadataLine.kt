package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.formatPremiereDate
import io.github.aedev.flow.utils.formatScheduledStart
import io.github.aedev.flow.utils.formatViewCount
import io.github.aedev.flow.utils.upcomingReleaseMs

@Composable
fun videoMetadataLine(
    video: Video,
    isUpcoming: Boolean,
    channelName: String = video.channelName,
    includeChannel: Boolean = false,
): String {
    val dateSettings = rememberDateDisplaySettings()
    if (isUpcoming) {
        val scheduled =
            remember(video.timestamp, video.uploadDate, dateSettings) {
                upcomingReleaseMs(video.timestamp, video.uploadDate)
                    ?.let { formatScheduledStart(it, dateSettings.resolve(DateContext.LISTS)) }
                    ?: formatPremiereDate(video.uploadDate)
            }
        return scheduled
            ?.let { stringResource(R.string.premiere_date_prefix, it) }
            ?: video.uploadDate.takeIf(String::isNotBlank)
            ?: stringResource(R.string.premiere_soon)
    }

    val uploadedAt =
        remember(video.uploadDate, video.timestamp, dateSettings) {
            dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
        }

    // A zero count means "not reported" — members-only uploads carry no view count at all — so the
    // row shows the date alone rather than a literal "0 views".
    if (video.viewCount <= 0L) {
        return when {
            !includeChannel -> uploadedAt
            uploadedAt.isBlank() -> channelName
            else -> stringResource(R.string.video_metadata_short_template, channelName, uploadedAt)
        }
    }

    // A live stream has viewers but no date, and a row must not end in a dangling separator.
    val views = stringResource(R.string.views_template, formatViewCount(video.viewCount))
    return when {
        uploadedAt.isBlank() && includeChannel -> stringResource(R.string.video_metadata_short_template, channelName, views)
        uploadedAt.isBlank() -> views
        includeChannel -> stringResource(R.string.video_metadata_template, channelName, views, uploadedAt)
        else -> stringResource(R.string.video_metadata_short_template, views, uploadedAt)
    }
}
