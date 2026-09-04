/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.flowHeroArtworkSize

private val DownloadRingSize = 64.dp

/**
 * The playlist page bar over the ambient header: back, the title and a Play button once the header
 * has scrolled away, the add-songs toggle on user playlists, and an overflow menu for the library
 * and share actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistTopBar(
    showTitle: Boolean,
    title: String,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit,
    showSearchToggle: Boolean,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    onMergeClick: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    MusicHeroTopBar(
        title = title,
        showTitle = showTitle,
        onBack = onBackClick,
    ) { iconColors ->
        AnimatedVisibility(visible = showTitle, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            FilledIconButton(
                onClick = onPlayClick,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play_all),
                )
            }
        }
        if (showSearchToggle) {
            IconButton(onClick = onSearchToggle, colors = iconColors) {
                Icon(
                    imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription =
                        if (searchActive) {
                            stringResource(R.string.ui_close_search)
                        } else {
                            stringResource(R.string.ui_add_songs)
                        },
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, colors = iconColors) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (onSaveToggle != null) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (isSaved) R.string.ui_remove_from_library else R.string.ui_save_to_library))
                        },
                        leadingIcon = {
                            Icon(if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onSaveToggle()
                        },
                    )
                }
                if (onMergeClick != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_all_to_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMergeClick()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShareClick()
                    },
                )
            }
        }
    }
}

/**
 * Centered artwork, title block and the action row: shuffle, a full-width Play button, the save
 * toggle and download with its progress ring, all at the medium size on the page's own scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistHeader(
    playlistDetails: PlaylistDetails,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
) {
    val artworkSize = flowHeroArtworkSize()
    val iconButtonSize = IconButtonDefaults.mediumContainerSize()
    val playHeight = ButtonDefaults.MediumContainerHeight

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(artworkSize),
        ) {
            AsyncImage(
                model = playlistDetails.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = playlistDetails.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(6.dp))

        val authorId = playlistDetails.authorId
        Text(
            text = playlistDetails.metadataLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .then(if (authorId != null) Modifier.clickable { onArtistClick(authorId) } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )

        playlistDetails.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onShuffleClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(iconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }

            Button(
                onClick = onPlayClick,
                shapes = ButtonDefaults.shapesFor(playHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(playHeight, hasStartIcon = true),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = playHeight),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(playHeight)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playHeight)))
                Text(
                    text = stringResource(R.string.play),
                    style = ButtonDefaults.textStyleFor(playHeight),
                )
            }

            if (onSaveToggle != null) {
                FilledIconToggleButton(
                    checked = isSaved,
                    onCheckedChange = { onSaveToggle() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(iconButtonSize),
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription =
                            stringResource(if (isSaved) R.string.ui_remove_from_library else R.string.ui_save_to_library),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }

            Box(contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(iconButtonSize),
                ) {
                    Icon(
                        imageVector = if (isDownloading) Icons.Outlined.Downloading else Icons.Outlined.Download,
                        contentDescription = stringResource(R.string.download),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
                if (isDownloading) {
                    CircularWavyProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(DownloadRingSize),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PlaylistDetails.metadataLine(): String {
    val separator = stringResource(R.string.metadata_separator)
    val parts =
        listOfNotNull(
            author.takeIf { it.isNotBlank() },
            trackCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.songs_count_template, it, it) },
            durationText,
            dateText,
        )
    return parts.joinToString(" $separator ")
}

/**
 * The add-songs field of a user playlist, on the Material search input so it matches the search
 * screen. Expands into the results below it inside the same list.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = searchActive) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.ui_close_search),
                )
            }
        }
        FlowSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder =
                if (searchActive) {
                    stringResource(R.string.ui_search_songs_to_add)
                } else {
                    stringResource(R.string.ui_add_songs_to_playlist)
                },
            modifier = Modifier.weight(1f),
            onSearch = onSearch,
            onClear = onClear,
            expanded = searchActive,
            onExpandedChange = { expanded -> if (expanded) onActivate() },
            focusRequester = focusRequester,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            LoadingIndicator()
        }
        Text(
            text =
                listOfNotNull(
                    pluralStringResource(R.plurals.songs_count_template, trackCount, trackCount),
                    durationText,
                ).joinToString(" ${stringResource(R.string.metadata_separator)} "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
