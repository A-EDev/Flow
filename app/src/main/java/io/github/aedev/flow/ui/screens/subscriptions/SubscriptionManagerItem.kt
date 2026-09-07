package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.ChannelGroupBadge
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButton
import io.github.aedev.flow.ui.components.shared.titleMarquee

private val RowPadding = 12.dp
private val AvatarSize = 48.dp
private val AvatarTextSpacing = 16.dp
private val TitleBlockSpacing = 4.dp

@Composable
internal fun SubscriptionManagerItem(
    channel: Channel,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit,
    isNotificationsEnabled: Boolean,
    areShortsExcluded: Boolean,
    onNotificationChange: (Boolean) -> Unit,
    onShortsExcludeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface)
                .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(AvatarSize)) {
            ChannelAvatarImage(
                url = channel.thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(AvatarSize)
                        .clip(subscriptionAvatarShape(channel.isMusic))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            ChannelGroupBadge(channelId = channel.id)
        }

        Spacer(modifier = Modifier.width(AvatarTextSpacing))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TitleBlockSpacing),
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.titleMarquee(),
            )
            if (channel.isMusic) {
                SubscriptionMusicPill()
            }
        }

        FlowSubscribeButton(
            isSubscribed = true,
            onSubscribeClick = {},
            isNotificationsEnabled = isNotificationsEnabled,
            onUnsubscribeClick = onUnsubscribe,
            onNotificationChange = onNotificationChange,
            areShortsExcluded = areShortsExcluded,
            onShortsExcludeChange = onShortsExcludeChange,
        )
    }
}
