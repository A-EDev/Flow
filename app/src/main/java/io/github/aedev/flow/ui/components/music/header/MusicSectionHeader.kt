/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.Dimensions

/**
 * Trailing affordance of a music section header. Each shelf offers at most one.
 */
sealed interface MusicSectionAction {
    val onClick: () -> Unit

    data class PlayAll(
        override val onClick: () -> Unit,
    ) : MusicSectionAction

    data class SeeAll(
        override val onClick: () -> Unit,
    ) : MusicSectionAction

    data class Navigate(
        override val onClick: () -> Unit,
    ) : MusicSectionAction
}

/**
 * The single header for every music shelf, page section and lane. Replaces the four competing
 * headers the music surface used to carry, which disagreed on type scale, padding and colour.
 */
@Composable
fun MusicSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: MusicSectionAction? = null,
) {
    val navigate = action as? MusicSectionAction.Navigate

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (navigate != null) Modifier.clickable(onClick = navigate.onClick) else Modifier,
                ).padding(
                    horizontal = Dimensions.ContentPaddingHorizontal,
                    vertical = Dimensions.ContentPaddingVertical,
                ),
    ) {
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
            ) {
                leading?.invoke()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (action) {
                is MusicSectionAction.PlayAll -> {
                    PlayAllPill(onClick = action.onClick)
                }

                is MusicSectionAction.SeeAll -> {
                    SeeAllButton(onClick = action.onClick)
                }

                is MusicSectionAction.Navigate -> {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = stringResource(R.string.ui_navigate),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                null -> {
                    Unit
                }
            }
        }
    }
}

@Composable
private fun PlayAllPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.action_play_all),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Text(
            text = stringResource(R.string.action_view_all),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
