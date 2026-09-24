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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.QuickActionsViewModel
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import io.github.aedev.flow.ui.components.shared.ChannelAvatarStack
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.thumbnailGradientOverlay
import io.github.aedev.flow.ui.components.shared.videoMetadataLine
import io.github.aedev.flow.ui.theme.extendedColors

@Composable
fun VideoCardFullWidth(
    video: Video,
    modifier: Modifier = Modifier,
    useInternalPadding: Boolean = true,
    showChannelAvatar: Boolean = true,
    showChannelName: Boolean = true,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onMoreClick: () -> Unit = {},
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
    val deArrowBadgeEnabledFullWidth = cardPreferences.deArrowBadgeEnabled
    val deArrowResultFullWidth = rememberDeArrowResult(video.id, cardPreferences.deArrowEnabled)
    val displayTitle = deArrowResultFullWidth?.title ?: video.title
    val displayThumbnailUrl = deArrowResultFullWidth?.thumbnailUrl ?: video.thumbnailUrl
    val videoCardActionsEnabledFW = cardPreferences.actionsEnabled
    val videoCardMarkWatchedEnabledFW = cardPreferences.markWatchedEnabled
    val upcomingReminderIds = cardPreferences.upcomingReminderIds
    val quickActionsVmFW: QuickActionsViewModel = hiltViewModel()
    val isWatchedFW = rememberIsWatched(video.id, quickActionsVmFW.watchedVideoIds, watchProgress)

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onLongClick = { showQuickActions = true },
                    onClick = onClick,
                ).then(if (useInternalPadding) Modifier.padding(horizontal = 12.dp) else Modifier),
    ) {
        // Thumbnail with duration
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .thumbnailGradientOverlay(),
        ) {
            VideoCardThumbnailOverlays(
                video = video,
                displayTitle = displayTitle,
                displayThumbnailUrl = displayThumbnailUrl,
                watchProgress = watchProgress,
                isUpcoming = video.isUpcoming,
                badgePadding = 8.dp,
                showReminderBadge = video.isUpcoming && video.id in upcomingReminderIds,
                showDeArrowBadge = deArrowResultFullWidth != null && deArrowBadgeEnabledFullWidth,
            )
        }

        // Video info section
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showChannelAvatar) {
                ChannelAvatarStack(
                    urls = video.channelAvatarUrls(collaboratorItems),
                    contentDescription = displayChannelName,
                    avatarSize = 40.dp,
                    modifier =
                        if (onChannelClick != null) {
                            Modifier.clickable { openChannelOrCollaborators() }
                        } else {
                            Modifier
                        },
                )
            }

            // Video details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayTitle,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.12f,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        videoMetadataLine(
                            video = video,
                            isUpcoming = video.isUpcoming,
                            channelName = displayChannelName,
                            includeChannel = showChannelName,
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
                    modifier =
                        if (onChannelClick != null) {
                            Modifier.clickable { openChannelOrCollaborators() }
                        } else {
                            Modifier
                        },
                )

                MembersOnlyLabel(video)
            }

            // More options button
            IconButton(
                onClick = { showQuickActions = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Video card quick actions (like/dislike/mark watched)
        if (videoCardActionsEnabledFW || videoCardMarkWatchedEnabledFW) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (videoCardActionsEnabledFW) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { quickActionsVmFW.markAsInteresting(video) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.ThumbUp,
                                contentDescription = stringResource(R.string.i_like_this),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.i_like_this),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { quickActionsVmFW.markNotInterested(video) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.ThumbDown,
                                contentDescription = stringResource(R.string.not_interested),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.not_interested),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (videoCardMarkWatchedEnabledFW) {
                    val watchedTint =
                        if (isWatchedFW) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isWatchedFW) quickActionsVmFW.markAsWatched(video)
                                }.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = stringResource(R.string.mark_as_watched),
                            modifier = Modifier.size(16.dp),
                            tint = watchedTint,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.mark_as_watched),
                            style = MaterialTheme.typography.labelMedium,
                            color = watchedTint,
                        )
                    }
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
