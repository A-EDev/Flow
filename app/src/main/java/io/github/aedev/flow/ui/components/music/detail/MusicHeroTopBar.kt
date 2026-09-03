/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBarDefaults
import io.github.aedev.flow.ui.theme.MusicScrimAffordance
import io.github.aedev.flow.ui.theme.MusicScrimContent

/**
 * The bar over a full-bleed hero: transparent with scrim-backed icons while the hero is on screen,
 * the page's surface with the title once it scrolls away. [actions] receive the icon colours for
 * the current state so every action reads on both backgrounds.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicHeroTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(IconButtonColors) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (showTitle) scheme.background else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "musicHeroTopBarContainer",
    )
    val iconColors =
        IconButtonDefaults.iconButtonColors(
            containerColor = if (showTitle) Color.Transparent else MusicScrimAffordance,
            contentColor = if (showTitle) scheme.onSurfaceVariant else MusicScrimContent,
        )

    TopAppBar(
        title = {
            AnimatedVisibility(visible = showTitle, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = title,
                    style = FlowTopBarDefaults.titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, colors = iconColors) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                )
            }
        },
        actions = { actions(iconColors) },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                scrolledContainerColor = containerColor,
                titleContentColor = scheme.onBackground,
            ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier,
    )
}
