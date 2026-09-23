package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.ui.components.shared.FlowPopIn
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButton
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButtonSize
import io.github.aedev.flow.ui.components.shared.dismissKeyboardOnPress
import io.github.aedev.flow.ui.components.shared.flowArtistShape
import io.github.aedev.flow.ui.components.shared.flowSegmentShape
import io.github.aedev.flow.utils.formatSubscriberCount

private val AvatarSize = 48.dp
private val SearchLoadingSize = 24.dp
private val ChipSpacing = 8.dp
private val PromptPadding = 32.dp
private const val MAX_QUICK_SEARCHES = 6
private const val MAX_POPPED_ROWS = 8

@Composable
internal fun ChannelsStep(
    state: OnboardingUiState,
    onQueryChange: (String) -> Unit,
    onSubscribeToggle: (Channel) -> Unit,
    onNotificationsChange: (String, Boolean) -> Unit,
    header: LazyListScope.() -> Unit,
    contentPadding: PaddingValues,
) {
    val focusManager = LocalFocusManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().dismissKeyboardOnPress { focusManager.clearFocus() },
        contentPadding = contentPadding,
    ) {
        header()
        item(key = "search") {
            FlowSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.onboarding_channels_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
                onSearch = { focusManager.clearFocus() },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingContent = { if (state.searching) SearchLoading() },
            )
        }
        item(key = "quick") { QuickSearches(state.topics, state.query, onQueryChange) }
        channelResults(state, onSubscribeToggle, onNotificationsChange)
        if (state.subscribed.isNotEmpty()) {
            item(key = "added") {
                Text(
                    text = pluralStringResource(R.plurals.onboarding_channels_added_count, state.subscribed.size, state.subscribed.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = PromptPadding / 2),
                )
            }
        }
    }
}

private fun LazyListScope.channelResults(
    state: OnboardingUiState,
    onSubscribeToggle: (Channel) -> Unit,
    onNotificationsChange: (String, Boolean) -> Unit,
) {
    when {
        state.query.isBlank() -> {
            item(key = "prompt") { Prompt(stringResource(R.string.onboarding_channels_empty_prompt)) }
        }

        state.results.isEmpty() && !state.searching -> {
            item(key = "empty") { Prompt(stringResource(R.string.onboarding_channels_no_results, state.query)) }
        }

        state.results.isNotEmpty() -> {
            item(key = "results") { FlowSectionHeader(stringResource(R.string.onboarding_channels_results_header)) }
            itemsIndexed(state.results, key = { _, channel -> channel.id }) { index, channel ->
                val row: @Composable () -> Unit = {
                    ChannelRow(
                        channel = channel,
                        subscribed = state.isSubscribed(channel.id),
                        notifying = channel.id in state.notifying,
                        shape = flowSegmentShape(index, state.results.size),
                        onToggle = { onSubscribeToggle(channel) },
                        onNotificationsChange = { onNotificationsChange(channel.id, it) },
                    )
                }
                val modifier = Modifier.animateItem().padding(bottom = FlowSegmentedGap)
                if (index < MAX_POPPED_ROWS) FlowPopIn(index, modifier) { row() } else Box(modifier) { row() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoading() {
    LoadingIndicator(Modifier.size(SearchLoadingSize))
}

@Composable
private fun QuickSearches(
    topics: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    if (topics.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(top = ChipSpacing),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        topics.take(MAX_QUICK_SEARCHES).forEach { topic ->
            SuggestionChip(
                onClick = { onQueryChange(topic) },
                label = { Text(topic) },
                icon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                    )
                },
                colors =
                    if (query == topic) {
                        SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        SuggestionChipDefaults.suggestionChipColors()
                    },
            )
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = PromptPadding),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    subscribed: Boolean,
    notifying: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        shapes = ListItemDefaults.shapes(shape = shape),
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                supportingContentColor = colors.onSurfaceVariant,
            ),
        leadingContent = {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(AvatarSize)
                        .clip(flowArtistShape())
                        .background(colors.surfaceContainerHighest),
            )
        },
        supportingContent =
            if (channel.subscriberCount > 0) {
                { Text(stringResource(R.string.onboarding_channels_subscribers, formatSubscriberCount(channel.subscriberCount))) }
            } else {
                null
            },
        trailingContent = {
            FlowSubscribeButton(
                isSubscribed = subscribed,
                onSubscribeClick = onToggle,
                onUnsubscribeClick = onToggle,
                isNotificationsEnabled = notifying,
                onNotificationChange = onNotificationsChange,
                size = FlowSubscribeButtonSize.Compact,
            )
        },
    ) {
        Text(text = channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
