/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.models.*
import io.github.aedev.flow.ui.screens.music.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopResultCard(
    item: YTItem,
    onClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
) {
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
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
                            .size(100.dp)
                            .clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle =
                        when (item) {
                            is ArtistItem -> stringResource(R.string.subtitle_artist)
                            is SongItem -> stringResource(R.string.subtitle_song_prefix, item.artists.joinToString { it.name })
                            is AlbumItem -> stringResource(R.string.subtitle_album_template, item.artists?.joinToString { it.name } ?: "")
                            is PlaylistItem -> stringResource(R.string.subtitle_playlist_template, item.author?.name ?: "")
                        }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item is ArtistItem) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onShuffleClick,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.shuffle), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRadioClick,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.radio), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
