/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val BadgeSpacing = 4.dp

/**
 * Marks a track as explicit. The only explicit badge in the app.
 */
@Composable
fun MusicExplicitBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_explicit),
        contentDescription = stringResource(R.string.label_explicit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(18.dp),
    )
}

/**
 * Marks an item as available offline. The only downloaded badge in the app.
 */
@Composable
fun MusicDownloadedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.OfflinePin,
        contentDescription = stringResource(R.string.status_downloaded),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(18.dp),
    )
}

/**
 * Chart position, emphasised for the top three.
 */
@Composable
fun MusicChartRankBadge(
    position: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.chart_position_template, position),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color =
            if (position <= 3) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = modifier.padding(end = BadgeSpacing),
    )
}
