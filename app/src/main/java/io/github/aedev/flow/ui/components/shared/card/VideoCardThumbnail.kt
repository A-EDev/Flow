package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.VideoStatusBadge
import io.github.aedev.flow.ui.components.shared.VideoThumbnailImage
import io.github.aedev.flow.ui.components.shared.WatchProgressBar
import io.github.aedev.flow.ui.theme.ArtworkScrimContent
import io.github.aedev.flow.ui.theme.artworkScrim
import io.github.aedev.flow.ui.theme.artworkScrimContent

private const val DEARROW_BADGE_ALPHA = 0.85f
private val DeArrowBadgeMargin = 4.dp
private val DeArrowBadgeSize = 16.dp
private val DeArrowBadgeInset = 2.dp
private const val REMINDER_BADGE_SCRIM_ALPHA = 0.7f
private const val REMINDER_BADGE_BORDER_ALPHA = 0.2f
private val ReminderBadgeBorderWidth = 0.5.dp

@Composable
internal fun BoxScope.VideoCardThumbnailOverlays(
    video: Video,
    displayTitle: String,
    displayThumbnailUrl: String?,
    watchProgress: Float?,
    isUpcoming: Boolean,
    badgePadding: Dp,
    showReminderBadge: Boolean = false,
    showDeArrowBadge: Boolean = false,
) {
    VideoThumbnailImage(
        videoId = video.id,
        model = displayThumbnailUrl,
        contentDescription = displayTitle,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )

    VideoStatusBadge(
        isLive = video.isLive,
        isUpcoming = isUpcoming,
        durationSeconds = video.duration,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(badgePadding),
    )

    if (showReminderBadge) {
        UpcomingReminderBadge(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(badgePadding),
        )
    }

    watchProgress?.let { progress ->
        WatchProgressBar(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }

    if (showDeArrowBadge) {
        DeArrowBadge(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(DeArrowBadgeMargin),
        )
    }
}

@Composable
private fun DeArrowBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = DEARROW_BADGE_ALPHA),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoFixHigh,
            contentDescription = stringResource(R.string.dearrow_badge),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier
                    .size(DeArrowBadgeSize)
                    .padding(DeArrowBadgeInset),
        )
    }
}

@Composable
private fun UpcomingReminderBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = artworkScrim(REMINDER_BADGE_SCRIM_ALPHA),
        border = BorderStroke(ReminderBadgeBorderWidth, artworkScrimContent(REMINDER_BADGE_BORDER_ALPHA)),
    ) {
        Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = stringResource(R.string.upcoming_video_reminder_badge),
            tint = ArtworkScrimContent,
            modifier =
                Modifier
                    .size(20.dp)
                    .padding(4.dp),
        )
    }
}
