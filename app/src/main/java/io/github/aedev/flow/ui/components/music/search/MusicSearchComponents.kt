/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.theme.Dimensions

/**
 * The search screen bar: one Material search pill holding back, the query, clear and voice
 * search, on the page background rather than inside an app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit,
    onVoiceSearchClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            expanded = true,
            onExpandedChange = {},
            placeholder = {
                Text(
                    text = stringResource(R.string.search_music_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.btn_back),
                    )
                }
            },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearClick) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    }
                    IconButton(onClick = onVoiceSearchClick) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.voice_search_cd),
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
        )
    }
}

@Composable
fun SearchFilterChips(
    activeFilter: SearchFilter?,
    onFilterClick: (SearchFilter?) -> Unit,
) {
    val filters =
        listOf(
            stringResource(R.string.filter_albums) to SearchFilter.FILTER_ALBUM,
            stringResource(R.string.tab_videos) to SearchFilter.FILTER_VIDEO,
            stringResource(R.string.filter_songs) to SearchFilter.FILTER_SONG,
            stringResource(R.string.filter_community_playlists) to SearchFilter.FILTER_COMMUNITY_PLAYLIST,
        )

    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.first }) { (label, filter) ->
            val selected = activeFilter == filter
            FlowFilterChip(
                label = label,
                selected = selected,
                onClick = { onFilterClick(if (selected) null else filter) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchSuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
            )
        },
    ) {
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
