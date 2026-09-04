/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.ArtworkLiveBadge
import io.github.aedev.flow.ui.theme.ArtworkScrimContent
import io.github.aedev.flow.ui.theme.artworkScrim
import io.github.aedev.flow.utils.formatDuration

private val BadgeSpacing = 4.dp
private val BadgeHorizontalPadding = 5.dp
private val BadgeVerticalPadding = 2.dp
private const val BADGE_SCRIM_ALPHA = 0.72f
private val BadgeIconSize = 13.dp
private val BadgeIconPadding = 4.dp

@Composable
fun DurationBadge(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    MediaTextBadge(text = formatDuration(seconds), modifier = modifier)
}

@Composable
fun VideoStatusBadge(
    isLive: Boolean,
    isUpcoming: Boolean,
    durationSeconds: Int,
    modifier: Modifier = Modifier,
) {
    when {
        isUpcoming -> {
            MediaTextBadge(
                text = stringResource(R.string.status_upcoming),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = BADGE_SCRIM_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        }

        isLive -> {
            MediaTextBadge(
                text = stringResource(R.string.status_live),
                modifier = modifier,
                containerColor = ArtworkLiveBadge.copy(alpha = BADGE_SCRIM_ALPHA),
                contentColor = ArtworkScrimContent,
            )
        }

        durationSeconds > 0 -> {
            DurationBadge(seconds = durationSeconds, modifier = modifier)
        }
    }
}

@Composable
fun MediaTextBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = artworkScrim(BADGE_SCRIM_ALPHA),
    contentColor: Color = ArtworkScrimContent,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = BadgeHorizontalPadding,
                    vertical = BadgeVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(BadgeSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(BadgeIconSize),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun MediaIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = artworkScrim(BADGE_SCRIM_ALPHA),
        contentColor = ArtworkScrimContent,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .padding(BadgeIconPadding)
                    .size(BadgeIconSize),
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
