@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.ChannelAvatarStack
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.components.shared.videoMetadataLine

/** The thumbnail across the full width with the details beneath: feeds and grids. */
@Composable
internal fun VideoCardStacked(
    video: Video,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)?,
    showChannel: Boolean,
    useInternalPadding: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberVideoCardState(video)
    val cardPreferences = LocalVideoCardPreferences.current
    val actions = LocalVideoCardActions.current

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onLongClick = { state.sheets.showQuickActions = true },
                    onClick = onClick,
                ).then(if (useInternalPadding) Modifier.padding(horizontal = 12.dp) else Modifier),
    ) {
        VideoCardThumbnail(
            state = state,
            isUpcoming = video.isUpcoming,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showChannel) {
                ChannelAvatarStack(
                    urls = state.avatarUrls,
                    contentDescription = state.channelName,
                    avatarSize = 40.dp,
                    modifier =
                        if (onChannelClick != null) {
                            Modifier.clickable { state.openChannel(onChannelClick) }
                        } else {
                            Modifier
                        },
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        videoMetadataLine(
                            video = video,
                            isUpcoming = video.isUpcoming,
                            channelName = state.channelName,
                            includeChannel = showChannel,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (video.isUpcoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (onChannelClick != null) {
                            Modifier.clickable { state.openChannel(onChannelClick) }
                        } else {
                            Modifier
                        },
                )

                MembersOnlyLabel(video)
            }

            IconButton(
                onClick = { state.sheets.showQuickActions = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (cardPreferences.actionsEnabled || cardPreferences.markWatchedEnabled) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (cardPreferences.actionsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { actions.onInterested(video) }
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
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { actions.onNotInterested(video) }
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

                if (cardPreferences.markWatchedEnabled) {
                    val watchedTint =
                        if (state.isWatched) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (!state.isWatched) actions.onWatched(video)
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

    VideoCardSheets(state = state, onChannelClick = onChannelClick)
}
