package io.github.aedev.flow.ui.tv.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.card.LocalVideoCardPreferences
import io.github.aedev.flow.ui.components.shared.card.rememberCardTitle
import io.github.aedev.flow.ui.components.shared.videoMetadataLine

private val SpotlightThumbnailWidth = 288.dp
private val SpotlightAvatarSize = 24.dp

/**
 * The header of a TV browse page: the focused video's channel, title, details and description beside
 * its thumbnail, on the plain background (Google's immersive list). The thumbnail is the URL the card
 * already loaded, so it comes from the image cache rather than a new request.
 */
@Composable
fun TvSpotlight(
    video: Video?,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = video,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        contentKey = { it?.id },
        label = "tvSpotlight",
    ) { shown ->
        if (shown != null) TvSpotlightContent(shown)
    }
}

@Composable
private fun TvSpotlightContent(video: Video) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatarImage(
                    url = video.channelThumbnailUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(SpotlightAvatarSize)
                            .clip(CircleShape),
                )
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = rememberCardTitle(video),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = videoMetadataLine(video = video, isUpcoming = video.isUpcoming),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val about = video.description.ifBlank { video.snippet }
            if (about.isNotBlank()) {
                Text(
                    text = about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MediaThumbnail(
            videoId = video.id,
            thumbnailUrl = video.thumbnailUrl,
            width = SpotlightThumbnailWidth,
            shape = MaterialTheme.shapes.large,
            durationSeconds = video.duration.takeIf { it > 0 && !video.isLive },
            showWatchProgress = LocalVideoCardPreferences.current.showWatchProgress,
        )
    }
}
