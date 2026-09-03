/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.ui.components.music.common.musicArtistShape
import io.github.aedev.flow.ui.components.music.common.musicHeroArtworkSize
import io.github.aedev.flow.utils.formatViewCount

private const val COLLAPSED_BIO_LINES = 3

/**
 * The artist page header: the portrait in the artist shape, the name, and the subscribe, shuffle
 * and play controls, all on the page scheme the portrait seeded.
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
    val portraitSize = musicHeroArtworkSize()
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = musicArtistShape(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(portraitSize),
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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (artist.subscriberCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subscribers_count_template, formatViewCount(artist.subscriberCount)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(
                checked = artist.isSubscribed,
                onCheckedChange = { onFollowClick() },
                shapes = ToggleButtonDefaults.shapesFor(buttonHeight),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                modifier = Modifier.heightIn(min = buttonHeight),
            ) {
                Text(
                    text = stringResource(if (artist.isSubscribed) R.string.subscribed else R.string.subscribe),
                    style = ButtonDefaults.textStyleFor(buttonHeight),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            FilledTonalIconButton(
                onClick = onShuffleClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }

            FilledIconButton(
                onClick = onPlayClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
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
