/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.ui.components.music.common.musicWindowWidth
import io.github.aedev.flow.utils.formatViewCount

private val HeroMaxHeight = 400.dp
private const val COLLAPSED_BIO_LINES = 3

/**
 * The artist page header: a full-bleed portrait ending in the extra-extra-large bottom corners,
 * the name below it so no text ever sits on the photo, then the subscribe toggle and the play
 * group. Everything reads the page scheme the portrait seeded.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistHero(
    artist: ArtistDetails,
    onFollowClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrl = artist.thumbnailUrl.ifEmpty { artist.bannerUrl }
    val heroHeight = musicWindowWidth().coerceAtMost(HeroMaxHeight)
    val heroShape =
        MaterialTheme.shapes.extraExtraLarge.copy(
            topStart = CornerSize(0.dp),
            topEnd = CornerSize(0.dp),
        )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(heroShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Text(
            text = artist.name,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp),
        )

        if (artist.subscriberCount > 0) {
            Text(
                text = stringResource(R.string.subscribers_count_template, formatViewCount(artist.subscriberCount)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(
                checked = artist.isSubscribed,
                onCheckedChange = { onFollowClick() },
                shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                if (artist.isSubscribed) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(text = stringResource(if (artist.isSubscribed) R.string.subscribed else R.string.subscribe))
            }

            Spacer(modifier = Modifier.weight(1f))

            ButtonGroup(overflowIndicator = {}) {
                customItem({
                    val interaction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onPlayClick,
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = interaction,
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                        modifier = Modifier.animateWidth(interaction),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.play))
                    }
                }) {}
                customItem({
                    val interaction = remember { MutableInteractionSource() }
                    FilledTonalIconButton(
                        onClick = onShuffleClick,
                        shapes = IconButtonDefaults.shapes(),
                        interactionSource = interaction,
                        modifier = Modifier.animateWidth(interaction),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                        )
                    }
                }) {}
            }
        }
    }
}

/**
 * The expandable artist description.
 */
@Composable
fun ArtistBio(
    description: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_BIO_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .animateContentSize(),
    )
}
