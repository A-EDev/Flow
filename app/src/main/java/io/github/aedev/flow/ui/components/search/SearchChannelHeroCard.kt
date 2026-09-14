package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButton
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * The creator block search puts above the results for a channel-name query.
 *
 * The avatar leads a single metadata line and the two actions sit on their own row. The channel's
 * blurb is deliberately absent: it arrives as one unstyled run with raw urls in it, and the card is
 * a way in to the channel rather than somewhere to read.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchChannelHeroCard(
    channel: Channel,
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = SectionSpacing)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = CardHorizontalPadding, vertical = CardVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BlockSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AvatarSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatarImage(
                    url = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.size(AvatarSize),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LineSpacing),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VerifiedSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (channel.isVerified) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.verified),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(VerifiedIconSize),
                            )
                        }
                    }
                    channel.metadataLine()?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowSubscribeButton(
                    isSubscribed = isSubscribed,
                    onSubscribeClick = onSubscribeToggle,
                    onUnsubscribeClick = onSubscribeToggle,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onClick,
                    shapes = ButtonDefaults.shapes(),
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
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
private val SectionSpacing = 8.dp
private val CardHorizontalPadding = 16.dp
private val CardVerticalPadding = 14.dp
private val AvatarSize = 56.dp
private val AvatarSpacing = 14.dp
private val BlockSpacing = 12.dp
private val LineSpacing = 2.dp
private val VerifiedSpacing = 4.dp
private val VerifiedIconSize = 15.dp
private val ActionSpacing = 8.dp
