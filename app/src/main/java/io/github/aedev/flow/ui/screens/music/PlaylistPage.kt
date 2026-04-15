package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.foundation.lazy.items
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPage(
    playlistDetails: PlaylistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onArtistClick: (String) -> Unit,
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isUserPlaylist: Boolean = false,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playlistsViewModel: MusicPlaylistsViewModel = hiltViewModel()
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Download
    val downloadProgress by playlistsViewModel.playlistDownloadProgress.collectAsState()
    val isDownloading by playlistsViewModel.isDownloadingPlaylist.collectAsState()

    val searchResults by playlistsViewModel.trackSearchResults.collectAsState()
    val isSearchingTracks by playlistsViewModel.isSearchingTracks.collectAsState()
    val addedTrackIds by playlistsViewModel.addedTrackIds.collectAsState()
    val locallyAddedTracks by playlistsViewModel.locallyAddedTracks.collectAsState()
    val deletedTrackIds = remember { mutableStateOf(emptySet<String>()) }
    val displayTracks = remember(playlistDetails.tracks, locallyAddedTracks, deletedTrackIds.value) {
        val existing = playlistDetails.tracks.map { it.videoId }.toHashSet()
        val all = playlistDetails.tracks + locallyAddedTracks.filter { it.videoId !in existing }
        all.filter { it.videoId !in deletedTrackIds.value }
    }

    var showSearchPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var showMergeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(350L)
            playlistsViewModel.searchTracks(searchQuery)
        } else {
            playlistsViewModel.clearTrackSearch()
        }
    }

    LaunchedEffect(showSearchPanel) {
        if (showSearchPanel) {
            delay(100L)
            searchFocusRequester.requestFocus()
            scrollState.animateScrollToItem(1)
        }
    }

    // Infinite scroll
    val reachedBottom by remember {
        derivedStateOf {
            val last = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            last?.index != 0 && last?.index == scrollState.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(reachedBottom) {
        if (reachedBottom && playlistDetails.continuation != null) onLoadMore()
    }

    // Bottom sheet
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = {
                if (selectedTrack!!.channelId.isNotEmpty()) onArtistClick(selectedTrack!!.channelId)
            },
            onViewAlbum = {},
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(
                            R.string.share_message_template,
                            selectedTrack!!.title,
                            selectedTrack!!.artist,
                            selectedTrack!!.videoId
                        )
                    )
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
            }
        )
    }

    val isScrolled = scrollState.firstVisibleItemIndex > 0

    Box(modifier = modifier.fillMaxSize()) {

        // ── Ambient blurred background (same as music player) ──────────────
        AsyncImage(
            model = playlistDetails.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .blur(120.dp),
            alpha = 0.65f,
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 480.dp)
                .background(MaterialTheme.colorScheme.background)
        )

        // ── Main content ──────────────────────────────────────────────────
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                PlaylistTopBar(
                    isScrolled = isScrolled,
                    title = playlistDetails.title,
                    onBackClick = onBackClick,
                    showSearchToggle = isUserPlaylist,
                    searchActive = showSearchPanel,
                    onSearchToggle = {
                        showSearchPanel = !showSearchPanel
                        if (!showSearchPanel) {
                            searchQuery = ""
                            playlistsViewModel.clearTrackSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    showSaveButton = !isUserPlaylist,
                    isSaved = isSaved,
                    onSaveToggle = onSaveToggle,
                    showMergeButton = !isUserPlaylist,
                    onMergeClick = { showMergeDialog = true }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {

                // ── HEADER: centered poster + metadata + actions ───────────
                item {
                    PlaylistCenteredHeader(
                        playlistDetails = playlistDetails,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onPlayClick = {
                            if (displayTracks.isNotEmpty()) {
                                onTrackClick(displayTracks.first(), displayTracks)
                            }
                        },
                        onShuffleClick = {
                            if (displayTracks.isNotEmpty()) {
                                val shuffled = displayTracks.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            }
                        },
                        onDownloadClick = {
                            if (!isDownloading) playlistsViewModel.downloadPlaylistTracks(playlistDetails)
                        },
                        onArtistClick = onArtistClick
                    )
                }

                // ── SEARCH BAR (user playlists only, inline in list) ──────
                if (isUserPlaylist) {
                    item {
                        PlaylistSearchBar(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                if (!showSearchPanel && it.isNotBlank()) showSearchPanel = true
                            },
                            onSearch = { keyboardController?.hide() },
                            onClear = { searchQuery = ""; playlistsViewModel.clearTrackSearch() },
                            focusRequester = searchFocusRequester,
                            searchActive = showSearchPanel,
                            onActivate = { showSearchPanel = true },
                            onToggleSearch = {
                                showSearchPanel = !showSearchPanel
                                if (!showSearchPanel) {
                                    searchQuery = ""
                                    playlistsViewModel.clearTrackSearch()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    }
                }

                // ── SEARCH RESULTS (when search is active) ─────────────────
                if (showSearchPanel && isUserPlaylist) {
                    if (isSearchingTracks) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else if (searchResults.isNotEmpty()) {
                        itemsIndexed(searchResults) { _, track ->
                            SearchResultTrackRow(
                                track = track,
                                isAdded = addedTrackIds.contains(track.videoId),
                                onAddClick = {
                                    playlistsViewModel.addTrackToPlaylist(playlistDetails.id, track)
                                },
                                onClick = { onTrackClick(track, listOf(track)) }
                            )
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    // ── TRACK LIST ─────────────────────────────────────────
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        )
                    }
                    itemsIndexed(displayTracks, key = { _, t -> t.videoId }) { index, track ->
                        PlaylistTrackRow(
                            index = index + 1,
                            track = track,
                            onClick = { onTrackClick(track, displayTracks) },
                            onMenuClick = { selectedTrack = track; showBottomSheet = true },
                            onDeleteClick = if (isUserPlaylist) {{
                                deletedTrackIds.value = deletedTrackIds.value + track.videoId
                                playlistsViewModel.removeTrackFromPlaylist(playlistDetails.id, track.videoId)
                            }} else null
                        )
                    }
                    item {
                        PlaylistFooter(
                            trackCount = playlistDetails.trackCount,
                            durationText = playlistDetails.durationText,
                            isLoadingMore = playlistDetails.continuation != null
                        )
                    }
                }
            }
        }
    }

    if (showMergeDialog) {
        MusicMergeIntoPlaylistDialog(
            tracks = playlistDetails.tracks,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showMergeDialog = false }
        )
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTopBar(
    isScrolled: Boolean,
    title: String,
    onBackClick: () -> Unit,
    showSearchToggle: Boolean,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    showSaveButton: Boolean = false,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    showMergeButton: Boolean = false,
    onMergeClick: (() -> Unit)? = null
) {
    val bgColor = if (isScrolled)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.btn_back),
                tint = Color.White
            )
        }
        if (isScrolled) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
            )
        }
        if (showSearchToggle) {
            IconButton(
                onClick = onSearchToggle,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchActive) "Close search" else "Add songs",
                    tint = Color.White
                )
            }
        }
        if (showSaveButton || showMergeButton) {
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (showMergeButton && onMergeClick != null) {
                    IconButton(onClick = onMergeClick) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = androidx.compose.ui.res.stringResource(io.github.aedev.flow.R.string.add_all_to_playlist),
                            tint = Color.White
                        )
                    }
                }
                if (showSaveButton && onSaveToggle != null) {
                    IconButton(onClick = onSaveToggle) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from library" else "Save to library",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
        }
    }
}

// ─── Centered Playlist Header ─────────────────────────────────────────────────

@Composable
private fun PlaylistCenteredHeader(
    playlistDetails: PlaylistDetails,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large centered artwork
        Surface(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp
        ) {
            AsyncImage(
                model = playlistDetails.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Title
        Text(
            text = playlistDetails.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Author
        Text(
            text = playlistDetails.author,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .then(
                    if (playlistDetails.authorId != null)
                        Modifier.clickable { onArtistClick(playlistDetails.authorId) }
                    else Modifier
                )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata line
        val meta = buildString {
            playlistDetails.trackCount.takeIf { it > 0 }?.let { append("$it songs") }
            playlistDetails.durationText?.let { if (isNotEmpty()) append("  ·  "); append(it) }
            playlistDetails.dateText?.let { if (isNotEmpty()) append("  ·  "); append(it) }
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1
            )
        }

        // Description
        playlistDetails.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Actions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download with progress ring
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDownloading) Icons.Outlined.Downloading else Icons.Outlined.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                }
            }

            // Play all
            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .height(52.dp)
                    .width(160.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play all",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Shuffle
            IconButton(
                onClick = onShuffleClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Inline Search Bar ───────────────────────────────────────

@Composable
private fun PlaylistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    searchActive: Boolean,
    onActivate: () -> Unit,
    onToggleSearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = searchActive) {
                IconButton(onClick = onToggleSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Pill-shaped search field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .then(if (!searchActive) Modifier.clickable { onActivate() } else Modifier)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!searchActive) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = if (searchActive) "Search songs to add…" else "Add songs to playlist",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Track Row ────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: MusicTrack,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.width(22.dp),
            maxLines = 1
        )
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track.duration > 0) {
            Text(
                text = formatTrackDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from playlist",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Search Result Row ────────────────────────────────────────────────────────

@Composable
private fun SearchResultTrackRow(
    track: MusicTrack,
    isAdded: Boolean,
    onAddClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isAdded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Added",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        } else {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Outlined.AddCircle,
                    contentDescription = "Add to playlist",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ─── Footer ───────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistFooter(
    trackCount: Int,
    durationText: String?,
    isLoadingMore: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(
            text = buildString {
                append("$trackCount songs")
                durationText?.let { append("  ·  $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatTrackDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

// ─── Merge Into Playlist Dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicMergeIntoPlaylistDialog(
    tracks: List<MusicTrack>,
    playlistsViewModel: MusicPlaylistsViewModel,
    onDismiss: () -> Unit
) {
    val playlists by playlistsViewModel.userCreatedMusicPlaylists.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(io.github.aedev.flow.R.string.merge_playlist_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider()

            if (playlists.isEmpty()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(io.github.aedev.flow.R.string.merge_playlist_no_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = playlists,
                        key = { it.id }
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playlistsViewModel.mergeTracksIntoPlaylist(playlist.id, tracks)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                if (playlist.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = playlist.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = androidx.compose.ui.res.stringResource(
                                        io.github.aedev.flow.R.string.songs_count_template,
                                        playlist.videoCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
