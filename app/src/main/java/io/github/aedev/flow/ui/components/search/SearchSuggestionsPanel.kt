package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.SearchHistoryItem
import io.github.aedev.flow.data.local.SearchType
import io.github.aedev.flow.innertube.pages.search.SearchSuggestion
import io.github.aedev.flow.ui.components.shared.FlowSuggestionRow

/**
 * What the search screen shows before a query is submitted: the user's own history first, then
 * what YouTube suggests, on the screen's own background rather than a raised surface.
 */
@Composable
fun SearchSuggestionsPanel(
    query: String,
    history: List<SearchHistoryItem>,
    suggestions: List<SearchSuggestion>,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onDeleteHistoryItem: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = HeaderStartPadding, end = HeaderEndPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recent_searches),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.clear_search_history))
                    }
                }
            }
            items(history, key = { "history:${it.id}" }) { item ->
                FlowSuggestionRow(
                    text = item.query,
                    leadingIcon = if (item.type == SearchType.VOICE) Icons.Rounded.Mic else Icons.Rounded.History,
                    onClick = { onSubmit(item.query) },
                    query = query,
                    trailingIcon = Icons.Rounded.Close,
                    trailingContentDescription = stringResource(R.string.remove),
                    onTrailingClick = { onDeleteHistoryItem(item) },
                )
            }
        }

        items(suggestions, key = { "suggestion:${it.text}" }) { suggestion ->
            FlowSuggestionRow(
                text = suggestion.text,
                leadingIcon = Icons.Rounded.Search,
                onClick = { onSubmit(suggestion.text) },
                query = query,
                leadingImageUrl = suggestion.entity?.thumbnailUrl,
                supportingText = suggestion.entity?.description,
                trailingIcon = Icons.Rounded.NorthWest,
                trailingContentDescription = stringResource(R.string.resize_fill),
                onTrailingClick = { onFill(suggestion.text) },
            )
        }
    }
}

private val HeaderStartPadding = 16.dp
private val HeaderEndPadding = 4.dp
