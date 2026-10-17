/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.OutlinedToggleButtonDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.aedev.flow.ui.components.shared.flowWindowWidth
import io.github.aedev.flow.utils.formatViewCount

private val HeroMaxHeight = 400.dp
private val StartingStrokeWidth = 2.dp
private const val COLLAPSED_BIO_LINES = 3

/**
 * The artist page header: a full-bleed portrait ending in the extra-extra-large bottom corners,
 * the name below it so no text ever sits on the photo, then the subscribe toggle and the play
 * split button. Everything reads the page scheme the portrait seeded. A null [onRadioClick] or
 * [onVideosClick] leaves that entry out of the play menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistHero(
    artist: ArtistDetails,
    isStartingQueue: Boolean,
    onFollowClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: (() -> Unit)?,
    onVideosClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrl = artist.thumbnailUrl.ifEmpty { artist.bannerUrl }
    val heroHeight = flowWindowWidth().coerceAtMost(HeroMaxHeight)
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

        artist.monthlyListenersText?.let { listeners ->
            Text(
                text = listeners,
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
            OutlinedToggleButton(
                checked = artist.isSubscribed,
                onCheckedChange = { onFollowClick() },
                shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
                colors =
                    OutlinedToggleButtonDefaults.outlinedToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Icon(
                    imageVector = if (artist.isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(if (artist.isSubscribed) R.string.subscribed else R.string.subscribe))
            }

            Spacer(modifier = Modifier.weight(1f))

            ArtistPlayButton(
                isStartingQueue = isStartingQueue,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
                onRadioClick = onRadioClick,
                onVideosClick = onVideosClick,
            )
        }
    }
}

/**
 * Play, with the other ways to start the artist behind the trailing half. While a fetched queue is
 * on its way the play icon turns into a spinner and further taps are ignored, so a second queue
 * never replaces the first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistPlayButton(
    isStartingQueue: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: (() -> Unit)?,
    onVideosClick: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuOpen) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "artistPlayMenuChevron",
    )

    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = { if (!isStartingQueue) onPlayClick() }) {
                    if (isStartingQueue) {
                        CircularProgressIndicator(
                            color = LocalContentColor.current,
                            strokeWidth = StartingStrokeWidth,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                        )
                    }
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.play))
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it && !isStartingQueue },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.more_options),
                        modifier =
                            Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ArtistPlayMenuItem(Icons.Rounded.Shuffle, stringResource(R.string.shuffle)) {
                menuOpen = false
                onShuffleClick()
            }
            if (onRadioClick != null) {
                ArtistPlayMenuItem(Icons.Rounded.Radio, stringResource(R.string.start_radio)) {
                    menuOpen = false
                    onRadioClick()
                }
            }
            if (onVideosClick != null) {
                ArtistPlayMenuItem(Icons.Rounded.SmartDisplay, stringResource(R.string.artist_play_videos)) {
                    menuOpen = false
                    onVideosClick()
                }
            }
        }
    }
}

@Composable
private fun ArtistPlayMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick,
    )
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
