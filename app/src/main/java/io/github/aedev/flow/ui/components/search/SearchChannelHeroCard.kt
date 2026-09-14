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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * The creator card search puts above the results for a channel-name query: avatar, name,
 * verification, handle, subscriber and video counts, the channel's own blurb, and the way in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchChannelHeroCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(CardPadding),
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

                val metadata =
                    listOfNotNull(
                        channel.handle.takeIf(String::isNotBlank),
                        channel.subscriberCount
                            .takeIf { it > 0 }
                            ?.let { stringResource(R.string.subscribers_count_template, formatSubscriberCount(it)) },
                        channel.videoCount
                            .takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.videos_count_template, it, it) },
                    )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata.joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            OutlinedButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.search_channel_go_to))
            }
        }
    }
}

private const val SEPARATOR = " • "
private val CardPadding = 14.dp
private val AvatarSize = 64.dp
private val LineSpacing = 3.dp
private val VerifiedSpacing = 4.dp
private val VerifiedIconSize = 16.dp
