/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.Dimensions

enum class FlowWindowWidth {
    Compact,
    Medium,
    Expanded,
}

private val MediumWindowWidth = 600.dp
private val ExpandedWindowWidth = 840.dp
private val MinLaneItemWidth = 240.dp
private val MinHeroArtworkSize = 180.dp
private val MaxHeroArtworkSize = 240.dp
private const val HERO_ARTWORK_FRACTION = 0.55f

fun Dp.toFlowWindowWidth(): FlowWindowWidth =
    when {
        this < MediumWindowWidth -> FlowWindowWidth.Compact
        this < ExpandedWindowWidth -> FlowWindowWidth.Medium
        else -> FlowWindowWidth.Expanded
    }

/**
 * Width available to an adaptive section, measured from the window rather than the display so
 * split-screen and freeform windows lay out like the narrow devices they behave as.
 */
@Composable
fun flowWindowWidth(): Dp {
    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width
    return with(density) { width.toDp() }
}

@Composable
fun flowWindowWidthClass(): FlowWindowWidth = flowWindowWidth().toFlowWindowWidth()

/**
 * Width of one card in a horizontally scrolling lane of wide items: fills a phone with [peek] of
 * the next card showing, and stops growing at [maxWidth] on wider windows.
 */
@Composable
fun flowLaneItemWidth(
    maxWidth: Dp,
    peek: Dp = 40.dp,
): Dp = flowLaneItemWidthFor(flowWindowWidth(), maxWidth, peek)

fun flowLaneItemWidthFor(
    windowWidth: Dp,
    maxWidth: Dp,
    peek: Dp,
): Dp = (windowWidth - Dimensions.ContentPaddingHorizontal * 2 - peek).coerceIn(MinLaneItemWidth, maxWidth)

@Composable
fun flowGridColumns(
    compact: Int,
    medium: Int,
    expanded: Int,
): Int =
    when (flowWindowWidthClass()) {
        FlowWindowWidth.Compact -> compact
        FlowWindowWidth.Medium -> medium
        FlowWindowWidth.Expanded -> expanded
    }

@Composable
fun flowGridCellWidth(
    columns: Int,
    gap: Dp = Dimensions.ItemSpacing,
): Dp = flowGridCellWidthFor(flowWindowWidth(), columns, gap)

fun flowGridCellWidthFor(
    windowWidth: Dp,
    columns: Int,
    gap: Dp,
): Dp = (windowWidth - Dimensions.ContentPaddingHorizontal * 2 - gap * (columns - 1)) / columns

/**
 * Artwork size for a collection or artist header: over half a phone, capped on wider windows so a
 * tablet header stays a header rather than a poster.
 */
@Composable
fun flowHeroArtworkSize(): Dp = flowHeroArtworkSizeFor(flowWindowWidth())

fun flowHeroArtworkSizeFor(windowWidth: Dp): Dp = (windowWidth * HERO_ARTWORK_FRACTION).coerceIn(MinHeroArtworkSize, MaxHeroArtworkSize)
