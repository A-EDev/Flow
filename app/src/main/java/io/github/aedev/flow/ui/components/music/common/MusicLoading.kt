/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Full-surface loading state. Only ever composed while a surface has nothing else to show, so the
 * indicator morph runs while it is on screen and leaves the tree with it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

/**
 * Inline loading row for a feed continuation or a paged list tail.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicFeedProgress(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
