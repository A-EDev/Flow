/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.screens.music.*
import io.github.aedev.flow.ui.theme.MusicScrim
import io.github.aedev.flow.ui.theme.MusicScrimAffordance
import io.github.aedev.flow.ui.theme.MusicScrimContent
import io.github.aedev.flow.ui.theme.musicScrimContent

@Composable
internal fun PlaylistTopBar(
    showTitle: Boolean,
    title: String,
    onBackClick: () -> Unit,
    showSearchToggle: Boolean,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    showSaveButton: Boolean = false,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    showMergeButton: Boolean = false,
    onMergeClick: (() -> Unit)? = null,
    onBottomPositioned: (Int) -> Unit,
) {
    val bgColor =
        if (showTitle) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        } else {
            Color.Transparent
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onBottomPositioned(
                        (coordinates.positionInRoot().y + coordinates.size.height).toInt(),
                    )
                },
        color = bgColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    tint = MusicScrimContent,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (showTitle) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MusicScrimContent,
                    )
                }
            }

            if (showSearchToggle) {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription =
                            if (searchActive) {
                                stringResource(
                                    R.string.ui_close_search,
                                )
                            } else {
                                stringResource(R.string.ui_add_songs)
                            },
                        tint = MusicScrimContent,
                    )
                }
            }
            if (showSaveButton || showMergeButton) {
                Row {
                    if (showMergeButton && onMergeClick != null) {
                        IconButton(onClick = onMergeClick) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription =
                                    androidx.compose.ui.res.stringResource(
                                        io.github.aedev.flow.R.string.add_all_to_playlist,
                                    ),
                                tint = MusicScrimContent,
                            )
                        }
                    }
                    if (showSaveButton && onSaveToggle != null) {
                        IconButton(onClick = onSaveToggle) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription =
                                    if (isSaved) {
                                        stringResource(
                                            R.string.ui_remove_from_library,
                                        )
                                    } else {
                                        stringResource(R.string.ui_save_to_library)
                                    },
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MusicScrimContent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaylistCenteredHeader(
    playlistDetails: PlaylistDetails,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onTitleBottomPositioned: (Int) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Large centered artwork
        Surface(
            modifier =
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
        ) {
            AsyncImage(
                model = playlistDetails.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Title
        Text(
            text = playlistDetails.title,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 30.sp,
                ),
            color = MusicScrimContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .onGloballyPositioned { coordinates ->
                        onTitleBottomPositioned(
                            (coordinates.positionInRoot().y + coordinates.size.height).toInt(),
                        )
                    },
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Author
        Text(
            text = playlistDetails.author,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = musicScrimContent(0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .then(
                        if (playlistDetails.authorId != null) {
                            Modifier.clickable { onArtistClick(playlistDetails.authorId) }
                        } else {
                            Modifier
                        },
                    ),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata line
        val meta =
            buildString {
                playlistDetails.trackCount.takeIf { it > 0 }?.let {
                    append(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.songs_count_template,
                            it,
                            it,
                        ),
                    )
                }
                playlistDetails.durationText?.let {
                    if (isNotEmpty()) append(" ${stringResource(R.string.metadata_separator)} ")
                    append(it)
                }
                playlistDetails.dateText?.let {
                    if (isNotEmpty()) append(" ${stringResource(R.string.metadata_separator)} ")
                    append(it)
                }
            }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = musicScrimContent(0.45f),
                maxLines = 1,
            )
        }

        // Description
        playlistDetails.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = musicScrimContent(0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Actions row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Download with progress ring
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(MusicScrimAffordance, CircleShape),
                ) {
                    Icon(
                        imageVector = if (isDownloading) Icons.Outlined.Downloading else Icons.Outlined.Download,
                        contentDescription = stringResource(R.string.download),
                        tint = MusicScrimContent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 2.5.dp,
                        color = MusicScrimContent,
                    )
                }
            }

            // Play all
            Button(
                onClick = onPlayClick,
                modifier =
                    Modifier
                        .height(52.dp)
                        .width(160.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MusicScrimContent,
                        contentColor = MusicScrim,
                    ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.play_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            // Shuffle
            IconButton(
                onClick = onShuffleClick,
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(MusicScrimAffordance, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    tint = MusicScrimContent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun PlaylistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    searchActive: Boolean,
    onActivate: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = searchActive) {
                IconButton(onClick = onToggleSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.ui_close_search),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // Pill-shaped search field
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(if (!searchActive) Modifier.clickable { onActivate() } else Modifier)
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!searchActive) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text =
                                    if (searchActive) {
                                        stringResource(
                                            R.string.ui_search_songs_to_add,
                                        )
                                    } else {
                                        stringResource(R.string.ui_add_songs_to_playlist)
                                    },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.ui_clear),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaylistFooter(
    trackCount: Int,
    durationText: String?,
    isLoadingMore: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(
            text =
                buildString {
                    append(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.songs_count_template,
                            trackCount,
                            trackCount,
                        ),
                    )
                    durationText?.let { append(" ${stringResource(R.string.metadata_separator)} $it") }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
        )
    }
}
