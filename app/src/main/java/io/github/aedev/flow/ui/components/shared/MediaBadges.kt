/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatDuration

private val BadgeSpacing = 4.dp
private val DurationBadgeHorizontalPadding = 5.dp
private val DurationBadgeVerticalPadding = 2.dp

@Composable
fun DurationBadge(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.padding(
                    horizontal = DurationBadgeHorizontalPadding,
                    vertical = DurationBadgeVerticalPadding,
                ),
        )
    }
}

/**
 * Marks a track as explicit. The only explicit badge in the app.
 */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
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
fun DownloadedBadge(modifier: Modifier = Modifier) {
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
fun ChartRankBadge(
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
