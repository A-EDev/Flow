/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.ui.theme.MusicScrimContent
import io.github.aedev.flow.ui.theme.MusicScrimThumbnailActive
import io.github.aedev.flow.ui.theme.MusicScrimThumbnailIndex

/**
 * The single artwork surface for the music library. [shape] covers the circular artist variant,
 * so there is no separate artist thumbnail to keep in sync.
 */
@Composable
fun MusicThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.ListThumbnailSize,
    shape: Shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    albumIndex: Int? = null,
    thumbnailRatio: Float = 1f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isActive || isPlaying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MusicScrimThumbnailActive),
            ) {
                if (isPlaying) {
                    PlayingWaveform()
                }
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.ui_selected),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size / 2),
                )
            }
        }

        if (albumIndex != null && !isActive && !isPlaying && !isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MusicScrimThumbnailIndex),
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MusicScrimContent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Two-by-two artwork grid standing in for a collection that has no cover of its own.
 */
@Composable
fun MusicMosaicThumbnail(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(2) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(2) { col ->
                        AsyncImage(
                            model = tracks.getOrNull(row * 2 + col)?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
