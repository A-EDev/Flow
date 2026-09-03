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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.music.card.MusicHeroCaptionHeight
import io.github.aedev.flow.ui.components.music.common.MusicSegmentedGap
import io.github.aedev.flow.ui.components.music.common.musicLaneItemWidth
import io.github.aedev.flow.ui.components.music.common.musicSegmentShape
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.theme.Dimensions

private val HeroLaneMaxWidth = 400.dp
private val HeroLanePeek = 40.dp
private val HeroLaneItemSpacing = 8.dp
private val TrackShelfColumnSpacing = 8.dp

/**
 * A titled horizontal lane of cards — the shape almost every music shelf shares.
 *
 * [key] is required rather than defaulted: the same video id appears in several shelves at once, so
 * a lane that keys on a bare id collides with its neighbours and crashes the feed. Namespace it.
 */
@Composable
fun <T> MusicShelf(
    title: String?,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: MusicSectionAction? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val uniqueItems = remember(items) { items.distinctBy(key) }
    if (uniqueItems.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            MusicSectionHeader(
                title = title,
                subtitle = subtitle,
                leading = leading,
                action = action,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
        ) {
            items(items = uniqueItems, key = key) { item -> itemContent(item) }
        }
    }
}

/**
 * A titled hero lane on the multi-browse carousel: one item in focus at over four fifths of a
 * phone, the next peeking in. [itemContent] runs in the carousel item scope so it can mask its
 * artwork, and receives a caption alpha to read in the draw phase so labels fade as an item
 * shrinks into a preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MusicHeroLane(
    title: String?,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: MusicSectionAction? = null,
    artAspectRatio: Float = 1f,
    itemContent: @Composable CarouselItemScope.(item: T, captionAlpha: () -> Float) -> Unit,
) {
    val uniqueItems = remember(items) { items.distinctBy(key) }
    if (uniqueItems.isEmpty()) return
    val itemWidth = musicLaneItemWidth(maxWidth = HeroLaneMaxWidth, peek = HeroLanePeek)
    val carouselState = rememberCarouselState { uniqueItems.size }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            MusicSectionHeader(
                title = title,
                subtitle = subtitle,
                leading = leading,
                action = action,
            )
        }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = itemWidth,
            itemSpacing = HeroLaneItemSpacing,
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimensions.ContentPaddingVertical)
                    .height(itemWidth / artAspectRatio + MusicHeroCaptionHeight),
        ) { index ->
            itemContent(uniqueItems[index], captionAlpha())
        }
    }
}

private fun CarouselItemScope.captionAlpha(): () -> Float =
    {
        val info = carouselItemDrawInfo
        val range = info.maxSize - info.minSize
        if (range <= 0f) 1f else ((info.size - info.minSize) / range).coerceIn(0f, 1f)
    }

/**
 * A titled lane of track rows stacked [rows] deep and scrolled horizontally — the Quick Picks and
 * Charts shape. Every column is one grouped list: [itemContent] receives the segmented shape for
 * the row's position in its column.
 */
@Composable
fun <T> MusicTrackShelf(
    title: String?,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: MusicSectionAction? = null,
    rows: Int = 4,
    rowHeight: Dp = Dimensions.ListItemHeight,
    state: LazyGridState = rememberLazyGridState(),
    itemContent: @Composable (item: T, shape: Shape) -> Unit,
) {
    val uniqueItems = remember(items) { items.distinctBy(key) }
    if (uniqueItems.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            MusicSectionHeader(title = title, subtitle = subtitle, action = action)
        }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TrackShelfColumnSpacing),
            verticalArrangement = Arrangement.spacedBy(MusicSegmentedGap),
            modifier =
                Modifier
                    .height(rowHeight * rows + MusicSegmentedGap * (rows - 1))
                    .fillMaxWidth(),
        ) {
            itemsIndexed(items = uniqueItems, key = { _, item -> key(item) }) { index, item ->
                val columnStart = (index / rows) * rows
                val rowsInColumn = minOf(rows, uniqueItems.size - columnStart)
                itemContent(item, musicSegmentShape(index = index - columnStart, count = rowsInColumn))
            }
        }
    }
}
