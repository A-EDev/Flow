/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * Every artist portrait in the music surface wears this shape, so an artist is recognisable as
 * one before the label is read. Albums and playlists keep the theme's rounded rectangles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun musicArtistShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

/**
 * The shape behind a shuffle or radio action, distinct from both artists and collections.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun musicActionShape(): Shape = MaterialShapes.Cookie12Sided.toShape()
