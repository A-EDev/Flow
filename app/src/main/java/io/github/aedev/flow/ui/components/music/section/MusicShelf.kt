/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.theme.Dimensions

/**
 * A titled horizontal lane of cards — the shape almost every music shelf shares.
 *
 * [key] is required rather than defaulted: the same video id appears in several shelves at once, so
 * a lane that keys on a bare id collides with its neighbours and crashes the feed. Namespace it.
 */
@Composable
fun <T> MusicShelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: MusicSectionAction? = null,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(
            title = title,
            subtitle = subtitle,
            leading = leading,
            action = action,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
        ) {
            items(items = items, key = key) { item -> itemContent(item) }
        }
    }
}

/**
 * A titled lane of track rows stacked [rows] deep and scrolled horizontally — the Quick Picks and
 * Charts shape.
 */
@Composable
fun <T> MusicTrackShelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: MusicSectionAction? = null,
    rows: Int = 4,
    rowHeight: Dp = Dimensions.ListItemHeight,
    state: LazyGridState = rememberLazyGridState(),
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(title = title, subtitle = subtitle, action = action)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .height(rowHeight * rows + 12.dp)
                    .fillMaxWidth(),
        ) {
            items(items = items, key = key) { item -> itemContent(item) }
        }
    }
}
