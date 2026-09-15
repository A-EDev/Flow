package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButton
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButtonSize
import io.github.aedev.flow.ui.components.shared.MediaArtworkTint
import io.github.aedev.flow.ui.components.shared.rememberMediaArtworkTint
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * The creator block search puts above the results for a channel-name query: the card and the
 * creator's latest uploads on one surface, tinted by the avatar through the same helper the
 * description sheet and the music hero use.
 */
@Composable
fun SearchChannelHeroCard(
    channel: Channel,
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    latestTitle: String? = null,
    latestVideos: List<Video> = emptyList(),
    onVideoClick: (Video) -> Unit = {},
) {
    val tint = rememberMediaArtworkTint(channel.thumbnailUrl)

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = CardMargin, vertical = CardGap),
        shape = MaterialTheme.shapes.large,
        color = tint.container,
        contentColor = tint.onContainer,
    ) {
        BoxWithConstraints {
            val actionsInline = maxWidth >= InlineActionsWidth
            val cardWidth = stripCardWidth(maxWidth)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = CardVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(BlockSpacing),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = CardHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(AvatarSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelAvatarImage(
                        url = channel.thumbnailUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.size(AvatarSize).clip(CircleShape),
                    )
                    ChannelIdentity(channel = channel, tint = tint, modifier = Modifier.weight(1f))
                    if (actionsInline) {
                        ChannelActions(
                            isSubscribed = isSubscribed,
                            onSubscribeToggle = onSubscribeToggle,
                            onOpen = onClick,
                            tint = tint,
                            modifier = Modifier.width(InlineActionsColumn),
                        )
                    }
                }

                if (!actionsInline) {
                    ChannelActions(
                        isSubscribed = isSubscribed,
                        onSubscribeToggle = onSubscribeToggle,
                        onOpen = onClick,
                        tint = tint,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CardHorizontalPadding),
                    )
                }

                if (latestVideos.isNotEmpty()) {
                    LatestStrip(
                        title = latestTitle,
                        videos = latestVideos,
                        tint = tint,
                        cardWidth = cardWidth,
                        onOpenChannel = onClick,
                        onVideoClick = onVideoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelIdentity(
    channel: Channel,
    tint: MediaArtworkTint,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VerifiedSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = tint.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (channel.isVerified) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.verified),
                    tint = tint.accent,
                    modifier = Modifier.size(VerifiedIconSize),
                )
            }
        }
        channel.metadataLine()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = tint.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelActions(
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit,
    onOpen: () -> Unit,
    tint: MediaArtworkTint,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ActionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowSubscribeButton(
            isSubscribed = isSubscribed,
            onSubscribeClick = onSubscribeToggle,
            onUnsubscribeClick = onSubscribeToggle,
            size = FlowSubscribeButtonSize.Wide,
            tint = tint,
            modifier = if (isSubscribed) Modifier else Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onOpen,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tint.onContainer),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.search_channel_go_to),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LatestStrip(
    title: String?,
    videos: List<Video>,
    tint: MediaArtworkTint,
    cardWidth: Dp,
    onOpenChannel: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StripTitleSpacing)) {
        title?.let {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenChannel)
                        .padding(horizontal = CardHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = tint.onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tint.onContainer,
                    modifier = Modifier.size(ChevronSize),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = CardHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(StripSpacing),
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCardFullWidth(
                    video = video,
                    useInternalPadding = false,
                    showChannelAvatar = false,
                    showChannelName = false,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

/**
 * Strip cards are sized from the window rather than pinned, so a phone shows two and a peek of the
 * third while a tablet shows four of the same shape.
 */
internal fun stripCardWidth(availableWidth: Dp): Dp {
    val divisor = if (availableWidth < CompactStripWidth) COMPACT_STRIP_DIVISOR else WIDE_STRIP_DIVISOR
    return (availableWidth / divisor).coerceIn(StripCardMinWidth, StripCardMaxWidth)
}

@Composable
private fun Channel.metadataLine(): String? {
    val parts =
        listOfNotNull(
            handle.takeIf(String::isNotBlank),
            subscriberCount
                .takeIf { it > 0 }
                ?.let { stringResource(R.string.subscribers_count_template, formatSubscriberCount(it)) },
            videoCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.videos_count_template, it, it) },
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
}

private const val SEPARATOR = " • "
private const val COMPACT_STRIP_DIVISOR = 1.3f
private const val WIDE_STRIP_DIVISOR = 2.6f
private val CompactStripWidth = 600.dp
private val StripCardMinWidth = 260.dp
private val StripCardMaxWidth = 380.dp
private val InlineActionsWidth = 640.dp
private val InlineActionsColumn = 360.dp
private val CardMargin = 8.dp
private val CardGap = 8.dp
private val CardHorizontalPadding = 14.dp
private val CardVerticalPadding = 14.dp
private val AvatarSize = 56.dp
private val AvatarSpacing = 14.dp
private val BlockSpacing = 12.dp
private val LineSpacing = 2.dp
private val VerifiedSpacing = 4.dp
private val VerifiedIconSize = 15.dp
private val ChevronSize = 20.dp
private val ActionSpacing = 8.dp
private val StripSpacing = 10.dp
private val StripTitleSpacing = 8.dp
