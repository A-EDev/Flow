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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.pressScale
import io.github.aedev.flow.ui.components.shared.videoMetadataLine

/** Thumbnail on the left, details on the right: lists, side panes and search rows. */
@Composable
internal fun VideoCardRow(
    video: Video,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)?,
    showChannel: Boolean,
    thumbnailWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val state = rememberVideoCardState(video)
    val cardPreferences = LocalVideoCardPreferences.current
    val actions = LocalVideoCardActions.current
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
                    onLongClick = { state.sheets.showQuickActions = true },
                    onClick = onClick,
                ).padding(vertical = 8.dp, horizontal = 12.dp),
    ) {
        VideoCardThumbnail(
            state = state,
            isUpcoming = isUpcomingRow,
            shape = MaterialTheme.shapes.medium,
            width = thumbnailWidth,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (showChannel) {
                Text(
                    text = state.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (onChannelClick != null) {
                            Modifier.clickable { state.openChannel(onChannelClick) }
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
                        channelName = state.channelName,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isUpcomingRow) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            MembersOnlyLabel(video)
        }

        Column(
            modifier = Modifier.align(Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            IconButton(
                onClick = { state.sheets.showQuickActions = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (cardPreferences.markWatchedEnabled) {
                IconButton(
                    onClick = {
                        if (!state.isWatched) actions.onWatched(video)
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.mark_as_watched),
                        tint = if (state.isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    VideoCardSheets(state = state, onChannelClick = onChannelClick)
}
