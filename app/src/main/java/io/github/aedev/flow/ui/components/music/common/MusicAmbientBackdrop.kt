/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.musicplayer.BlurredArtworkLayer

private val BackdropHeight = 480.dp
private const val BACKDROP_ALPHA = 0.55f
private const val FADE_START = 0.35f

/**
 * The ambient band behind a collection page header: the player's blurred artwork layer, dissolving
 * into the page background before the tracklist starts. The blur is a static layer that only
 * re-renders when the artwork changes, and the content scrolls over it rather than moving it.
 */
@Composable
fun MusicAmbientBackdrop(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BackdropHeight),
    ) {
        BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = BACKDROP_ALPHA)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            FADE_START to background.copy(alpha = 0f),
                            1f to background,
                        ),
                    ),
        )
    }
}
