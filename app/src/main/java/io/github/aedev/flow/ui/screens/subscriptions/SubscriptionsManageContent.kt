package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel

private val ContentHorizontalPadding = 16.dp
private val SelectorVerticalPadding = 8.dp
private val ListItemSpacing = 12.dp
private val SelectorIconSize = 18.dp

/**
 * Manage mode: pick video or music subscriptions, then act on each channel. The video/music choice
 * is an M3 Expressive connected button group rather than a segmented row.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubscriptionsManageContent(
    channels: List<Channel>,
    searchQuery: String,
    notificationStates: Map<String, Boolean>,
    excludedShortsChannelIds: Set<String>,
    onChannelClick: (Channel) -> Unit,
    onNotificationChange: (String, Boolean) -> Unit,
    onShortsExcludeChange: (String, Boolean) -> Unit,
    onUnsubscribe: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val activeList =
        remember(channels, selectedTabIndex) {
            if (selectedTabIndex == 0) {
                channels.filterNot { it.isMusic }
            } else {
                channels.filter { it.isMusic }
            }
        }

    val filteredChannels =
        remember(activeList, searchQuery) {
            if (searchQuery.isBlank()) {
                activeList
            } else {
                activeList.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ContentHorizontalPadding, vertical = SelectorVerticalPadding),
        ) {
            ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
                customItem({
                    SubscriptionKindToggle(
                        selected = selectedTabIndex == 0,
                        onSelect = { selectedTabIndex = 0 },
                        icon = Icons.Default.OndemandVideo,
                        label = stringResource(R.string.subscriptions_video_section_title),
                    )
                }) {}
                customItem({
                    SubscriptionKindToggle(
                        selected = selectedTabIndex == 1,
                        onSelect = { selectedTabIndex = 1 },
                        icon = Icons.Default.MusicNote,
                        label = stringResource(R.string.subscriptions_music_section_title),
                    )
                }) {}
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = ContentHorizontalPadding,
                    end = ContentHorizontalPadding,
                    top = 4.dp,
                    bottom = ContentHorizontalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
        ) {
            item {
                Text(
                    text =
                        pluralStringResource(
                            id = R.plurals.channels_count,
                            count = filteredChannels.size,
                            filteredChannels.size,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            items(filteredChannels, key = { it.id }) { channel ->
                SubscriptionManagerItem(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    onUnsubscribe = { onUnsubscribe(channel) },
                    isNotificationsEnabled = notificationStates[channel.id] ?: false,
                    areShortsExcluded = channel.id in excludedShortsChannelIds,
                    onNotificationChange = { enabled -> onNotificationChange(channel.id, enabled) },
                    onShortsExcludeChange = { excluded -> onShortsExcludeChange(channel.id, excluded) },
                )
            }

            if (filteredChannels.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_subscriptions_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = ContentHorizontalPadding),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ButtonGroupScope.SubscriptionKindToggle(
    selected: Boolean,
    onSelect: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val interaction = remember { MutableInteractionSource() }
    ToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
        interactionSource = interaction,
        modifier = Modifier.animateWidth(interaction),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(SelectorIconSize),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = label)
    }
}
