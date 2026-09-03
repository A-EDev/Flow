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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBarDefaults
import io.github.aedev.flow.ui.components.music.common.musicHeroArtworkSize

private val DownloadRingSize = 64.dp

/**
 * The playlist page bar. The title and a Play button arrive once the header has scrolled away,
 * so the primary action is never further than the bar while the listener is deep in the list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistTopBar(
    showTitle: Boolean,
    title: String,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    showSearchToggle: Boolean,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    showSaveButton: Boolean = false,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    showMergeButton: Boolean = false,
    onMergeClick: (() -> Unit)? = null,
) {
    FlowTopBar(
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
        onBack = onBackClick,
        actions = {
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
                IconButton(onClick = onSearchToggle) {
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
            if (showMergeButton && onMergeClick != null) {
                IconButton(onClick = onMergeClick) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.add_all_to_playlist),
                    )
                }
            }
            if (showSaveButton && onSaveToggle != null) {
                IconButton(onClick = onSaveToggle) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription =
                            if (isSaved) {
                                stringResource(R.string.ui_remove_from_library)
                            } else {
                                stringResource(R.string.ui_save_to_library)
                            },
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/**
 * Centered artwork, title block and the three collection actions, on the page's own scheme.
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
) {
    val artworkSize = musicHeroArtworkSize()

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

        val authorId = playlistDetails.authorId
        if (authorId != null) {
            TextButton(
                onClick = { onArtistClick(authorId) },
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
                modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Text(
                    text = playlistDetails.author,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playlistDetails.author,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val meta = playlistDetails.metadataLine()
        if (meta.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        playlistDetails.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
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

            val playHeight = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onPlayClick,
                shapes = ButtonDefaults.shapesFor(playHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(playHeight, hasStartIcon = true),
                modifier = Modifier.heightIn(min = playHeight),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(playHeight)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playHeight)))
                Text(
                    text = stringResource(R.string.play_all),
                    style = ButtonDefaults.textStyleFor(playHeight),
                )
            }

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
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PlaylistDetails.metadataLine(): String {
    val separator = stringResource(R.string.metadata_separator)
    val parts =
        listOfNotNull(
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
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            expanded = searchActive,
            onExpandedChange = { expanded -> if (expanded) onActivate() },
            placeholder = {
                Text(
                    text =
                        if (searchActive) {
                            stringResource(R.string.ui_search_songs_to_add)
                        } else {
                            stringResource(R.string.ui_add_songs_to_playlist)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                )
            },
            trailingIcon =
                if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
