/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Which container a mood tile sits on. Tiles cycle through the three so a grid of them reads as
 * a set of distinct destinations instead of a wall of identical pills.
 */
enum class MusicMoodTone {
    Primary,
    Secondary,
    Tertiary,
    ;

    companion object {
        fun forIndex(index: Int): MusicMoodTone = entries[index % entries.size]
    }
}

/**
 * A mood or genre destination. One shape family with the rest of the surface's buttons, at the
 * medium button height so a row of them is tappable without dominating the feed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicMoodButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MusicMoodTone = MusicMoodTone.Secondary,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            MusicMoodTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
            MusicMoodTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
            MusicMoodTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        }
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
