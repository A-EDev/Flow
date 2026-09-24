package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.ChannelAvatarStack
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

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .videoCardClickable(state, onClick)
                .then(if (useInternalPadding) Modifier.padding(horizontal = VideoCardDefaults.Inset) else Modifier),
    ) {
        VideoCardThumbnail(
            state = state,
            isUpcoming = video.isUpcoming,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        // The avatar's 48 dp target overhangs its 40 dp image by 4 dp, so the row gives those 4 dp back
        // and the avatar still lines up where it always has.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (showChannel) 8.dp else 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (showChannel) 8.dp else 12.dp),
        ) {
            if (showChannel) {
                ChannelAvatarStack(
                    urls = state.avatarUrls,
                    contentDescription = state.channelName,
                    avatarSize = VideoCardDefaults.AvatarSize,
                    modifier =
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .videoCardChannelClickable(state, onChannelClick),
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
                )

                MembersOnlyLabel(video)
            }

            VideoCardSideActions(
                state = state,
                showWatched = false,
                onWatched = actions.onWatched,
                alignTo = 12.dp,
            )
        }

        if (cardPreferences.actionsEnabled || cardPreferences.markWatchedEnabled) {
            VideoCardFeedback(
                state = state,
                showRating = cardPreferences.actionsEnabled,
                showWatched = cardPreferences.markWatchedEnabled,
                actions = actions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VideoCardDefaults.Inset)
                        .padding(bottom = VideoCardDefaults.Inset),
            )
        }
    }

    VideoCardSheets(state = state, onChannelClick = onChannelClick)
}
