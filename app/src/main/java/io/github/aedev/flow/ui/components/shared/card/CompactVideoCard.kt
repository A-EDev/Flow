@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.QuickActionsViewModel
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.videoMetadataLine
import io.github.aedev.flow.ui.theme.extendedColors

/** The width every existing caller renders, so [thumbnailWidth] only ever widens it deliberately. */
val CompactVideoCardThumbnailWidth = 168.dp

/**
 * A horizontal Video Card optimized for side panes (tablets/foldables) or lists.
 * Image on Left, Info on Right.
 */
@Composable
fun CompactVideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    onChannelClick: ((String) -> Unit)? = null,
    showChannelName: Boolean = true,
    thumbnailWidth: Dp = CompactVideoCardThumbnailWidth,
) {
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
    val dateSettings = rememberDateDisplaySettings()
    val watchProgress = rememberWatchProgress(video.id)

    // DeArrow: replace clickbait titles and thumbnails if enabled
    val cardPreferences = LocalVideoCardPreferences.current
    val deArrowBadgeEnabledCompact = cardPreferences.deArrowBadgeEnabled
    val deArrowResultCompact = rememberDeArrowResult(video.id, cardPreferences.deArrowEnabled)
    val videoCardMarkWatchedEnabledCompact = cardPreferences.markWatchedEnabled
    val quickActionsVmCompact: QuickActionsViewModel = hiltViewModel()
    val isWatchedCompact = rememberIsWatched(video.id, quickActionsVmCompact.watchedVideoIds, watchProgress)
    val displayTitle = deArrowResultCompact?.title ?: video.title
    val displayThumbnailUrl = deArrowResultCompact?.thumbnailUrl ?: video.thumbnailUrl
    // A negative count is the older "no count reported" sentinel; a row that declares itself
    // upcoming counts too. The badge and the metadata line read the same answer.
    val isUpcomingRow = video.isUpcoming || video.viewCount < 0L

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
                ).padding(vertical = 8.dp, horizontal = 12.dp),
    ) {
        // Thumbnail (Left side)
        Box(
            modifier =
                Modifier
                    .width(thumbnailWidth)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            VideoCardThumbnailOverlays(
                video = video,
                displayTitle = displayTitle,
                displayThumbnailUrl = displayThumbnailUrl,
                watchProgress = watchProgress,
                isUpcoming = isUpcomingRow,
                badgePadding = 4.dp,
                showDeArrowBadge = deArrowResultCompact != null && deArrowBadgeEnabledCompact,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info (Right side)
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = displayTitle,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.12f,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(6.dp))

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
                        isUpcoming = isUpcomingRow,
                        channelName = displayChannelName,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isUpcomingRow) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.extendedColors.textSecondary.copy(alpha = 0.8f)
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )

            MembersOnlyLabel(video)
        }

        Column(
            modifier = Modifier.align(Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            IconButton(
                onClick = { showQuickActions = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (videoCardMarkWatchedEnabledCompact) {
                IconButton(
                    onClick = {
                        if (!isWatchedCompact) quickActionsVmCompact.markAsWatched(video)
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.mark_as_watched),
                        tint = if (isWatchedCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                }
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
