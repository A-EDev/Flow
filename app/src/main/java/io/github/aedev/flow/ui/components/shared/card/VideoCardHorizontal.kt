@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.videoMetadataLine
import io.github.aedev.flow.ui.theme.extendedColors

@Composable
fun VideoCardHorizontal(
    video: Video,
    modifier: Modifier = Modifier,
    onChannelClick: ((String) -> Unit)? = null,
    showChannelName: Boolean = true,
    onClick: () -> Unit,
) {
    val dateSettings = rememberDateDisplaySettings()
    val cardPreferences = LocalVideoCardPreferences.current
    val deArrowResult = rememberDeArrowResult(video.id, cardPreferences.deArrowEnabled)
    val displayTitle = deArrowResult?.title ?: video.title
    val displayThumbnailUrl = deArrowResult?.thumbnailUrl ?: video.thumbnailUrl
    val upcomingReminderIds = cardPreferences.upcomingReminderIds
    val watchProgress = rememberWatchProgress(video.id)

    var showQuickActions by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    val collaboratorItems = rememberCollaboratorItems(video)
    val displayChannelName = rememberCollaboratorChannelDisplayName(video.channelName, collaboratorItems)
    val openChannelOrCollaborators = {
        if (collaboratorItems.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick?.invoke(video.channelId)
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onLongClick = { showQuickActions = true },
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)) // Sleek corners
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            VideoCardThumbnailOverlays(
                video = video,
                displayTitle = displayTitle,
                displayThumbnailUrl = displayThumbnailUrl,
                watchProgress = watchProgress,
                isUpcoming = video.isUpcoming,
                badgePadding = 6.dp,
                showReminderBadge = video.isUpcoming && video.id in upcomingReminderIds,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Column {
                if (showChannelName) {
                    Text(
                        text = displayChannelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            if (onChannelClick != null) {
                                Modifier.clickable { openChannelOrCollaborators() }
                            } else {
                                Modifier
                            },
                    )
                }

                Text(
                    text =
                        videoMetadataLine(
                            video = video,
                            isUpcoming = video.isUpcoming,
                            channelName = displayChannelName,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (video.isUpcoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.extendedColors.textSecondary
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    VideoCardSheets(
        video = video,
        collaborators = collaboratorItems,
        showQuickActions = showQuickActions,
        showCollaborators = showCollaborators,
        onChannelClick = onChannelClick,
        onDismissQuickActions = { showQuickActions = false },
        onDismissCollaborators = { showCollaborators = false },
    )
}
