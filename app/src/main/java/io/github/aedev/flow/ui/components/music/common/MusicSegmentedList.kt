/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Vertical gap between the rows of a segmented track list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val MusicSegmentedGap: Dp = ListItemDefaults.SegmentedGap

/**
 * Shape of row [index] in a segmented list of [count] rows: the outer rows carry the group's
 * rounded ends, the middle rows the small corners. The playing row keeps the group shape and is
 * told apart by its artwork colours instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun musicSegmentShape(
    index: Int,
    count: Int,
): Shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape
