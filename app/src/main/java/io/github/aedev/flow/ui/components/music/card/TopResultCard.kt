/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.ui.components.music.common.musicArtistShape

private val TopResultArtworkSize = 96.dp

/**
 * The first search hit, given a card of its own. Artists also get shuffle and radio actions as a
 * button group whose pressed member widens.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopResultCard(
    item: YTItem,
    onClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(TopResultArtworkSize)
                            .clip(if (item is ArtistItem) musicArtistShape() else MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.topResultSubtitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item is ArtistItem) {
                Spacer(modifier = Modifier.height(20.dp))
                ButtonGroup(
                    overflowIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    customItem({
                        val interaction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onShuffleClick,
                            shapes = ButtonDefaults.shapes(),
                            interactionSource = interaction,
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .animateWidth(interaction),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.shuffle))
                        }
                    }) {}
                    customItem({
                        val interaction = remember { MutableInteractionSource() }
                        FilledTonalButton(
                            onClick = onRadioClick,
                            shapes = ButtonDefaults.shapes(),
                            interactionSource = interaction,
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .animateWidth(interaction),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.radio))
                        }
                    }) {}
                }
            }
        }
    }
}

@Composable
private fun YTItem.topResultSubtitle(): String =
    when (this) {
        is ArtistItem -> stringResource(R.string.subtitle_artist)
        is SongItem -> stringResource(R.string.subtitle_song_prefix, artists.joinToString { it.name })
        is AlbumItem -> stringResource(R.string.subtitle_album_template, artists?.joinToString { it.name } ?: "")
        is PlaylistItem -> stringResource(R.string.subtitle_playlist_template, author?.name ?: "")
    }
