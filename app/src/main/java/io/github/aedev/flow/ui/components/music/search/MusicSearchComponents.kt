/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import io.github.aedev.flow.innertube.models.*
import io.github.aedev.flow.ui.screens.music.*

@Composable
fun MusicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit,
    onVoiceSearchClick: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        },
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.btn_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                cursorBrush =
                    androidx.compose.ui.graphics
                        .SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_music_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(
                onClick = onVoiceSearchClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = stringResource(R.string.voice_search_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
                .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { (label, filter) ->
            Surface(
                modifier = Modifier.clickable { onFilterClick(if (activeFilter == filter) null else filter) },
                shape = RoundedCornerShape(8.dp),
                color = if (activeFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor =
                    if (activeFilter ==
                        filter
                    ) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
fun SearchSuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ArrowOutward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
    }
}
