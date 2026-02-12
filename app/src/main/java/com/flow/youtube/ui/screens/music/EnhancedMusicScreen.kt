package com.flow.youtube.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.screens.music.components.*
import com.flow.youtube.ui.screens.music.tabs.*
import com.flow.youtube.ui.theme.Dimensions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EnhancedMusicScreen(
    onBackClick: () -> Unit,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onVideoClick: (MusicTrack) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onMoodsClick: (com.flow.youtube.innertube.pages.MoodAndGenres.Item?) -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = { 
                if (selectedTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(selectedTrack!!.channelId)
                }
            },
            onViewAlbum = { 
                if (selectedTrack!!.album.isNotEmpty()) {
                    onAlbumClick(selectedTrack!!.album)
                }
            },
            onShare = { 
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, selectedTrack!!.title, selectedTrack!!.artist, selectedTrack!!.videoId))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            }
        )
    }
    

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_title_music),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.search))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    HomeShimmerLoading()
                }
                
                uiState.error != null && uiState.trendingSongs.isEmpty() -> {
                    ErrorContent(
                        error = uiState.error ?: stringResource(R.string.error_occurred),
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    val popularArtists = remember(uiState.trendingSongs, uiState.newReleases) {
                        (uiState.trendingSongs + uiState.newReleases)
                            .distinctBy { it.artist }
                            .take(10)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Listen Again
                        if (uiState.listenAgain.isNotEmpty()) {
                            item {
                                NavigationTitle(title = stringResource(R.string.section_listen_again))
                                val listenThumbnailHeight = currentGridThumbnailHeight()
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.listenAgain) { track ->
                                        GridItem(
                                            title = track.title,
                                            subtitle = track.artist,
                                            thumbnailUrl = track.thumbnailUrl,
                                            thumbnailHeight = listenThumbnailHeight,
                                            onClick = { onSongClick(track, uiState.listenAgain, "listen_again") }
                                        )
                                    }
                                }
                            }
                        }

                        // Home Chips
                        if (uiState.homeChips.isNotEmpty()) {
                            item {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.homeChips) { chip ->
                                        val isChipSelected = uiState.selectedHomeChip?.title == chip.title
                                        ContentFilterChip(
                                            title = chip.title,
                                            isSelected = isChipSelected,
                                            onClick = { 
                                                if (isChipSelected) {
                                                    viewModel.setHomeChip(null)
                                                } else {
                                                    viewModel.setHomeChip(chip)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter != null) {
                            if (uiState.isSearching) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                items(uiState.allSongs) { track ->
                                    MusicTrackRow(
                                        track = track,
                                        isPlaying = currentTrack?.videoId == track.videoId,
                                        onClick = { onSongClick(track, uiState.allSongs, uiState.selectedFilter) },
                                        onMenuClick = {
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }
                        } else {
                            if (uiState.forYouTracks.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_quick_picks))
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(4),
                                        state = rememberLazyGridState(),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                            .fillMaxWidth()
                                    ) {
                                        items(uiState.forYouTracks.take(20)) { track ->
                                            ListItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                isPlaying = currentTrack?.videoId == track.videoId,
                                                onClick = { onSongClick(track, uiState.forYouTracks, "quick_picks") },
                                                onLongClick = {
                                                    selectedTrack = track
                                                    showBottomSheet = true
                                                },
                                                modifier = Modifier.width(320.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Recommended for you
                            if (uiState.recommendedTracks.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_recommended))
                                    val thumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.recommendedTracks) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = thumbnailHeight,
                                                onClick = { onSongClick(track, uiState.recommendedTracks, "recommended") }
                                            )
                                        }
                                    }
                                }
                            }
                        
                            // History / Speed Dial
                            if (uiState.history.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_recently_played))
                                    val historyThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.history.distinctBy { it.videoId }.take(10)) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = historyThumbnailHeight,
                                                onClick = { onSongClick(track, uiState.history, "history") }
                                            )
                                        }
                                    }
                                }
                            }

                            // Similar To & Vibes (New Sections)
                            uiState.similarToSections.forEach { section ->
                                item {
                                    if (section.label != null) {
                                        NavigationTitle(
                                            title = section.title,
                                            label = section.label,
                                            thumbnail = {
                                                if (section.thumbnailUrl != null) {
                                                    if (section.isArtistSeed) {
                                                        ArtistThumbnail(
                                                            thumbnailUrl = section.thumbnailUrl,
                                                            size = 40.dp
                                                        )
                                                    } else {
                                                        ItemThumbnail(
                                                            thumbnailUrl = section.thumbnailUrl,
                                                            size = 40.dp,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = if (!section.seedId.isNullOrBlank()) {
                                                {
                                                    if (section.isArtistSeed) {
                                                        onArtistClick(section.seedId)
                                                    } else {
                                                        // For non-artist seeds, we could navigate to details/album
                                                        // For now we only enable navigation for artists specifically 
                                                        // if that's what's supported reliably
                                                    }
                                                }
                                            } else null
                                        )
                                    } else {
                                        SectionTitle(title = section.title, subtitle = section.subtitle)
                                    }

                                    val sectionThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(section.tracks) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = sectionThumbnailHeight,
                                                onClick = { 
                                                    when (track.itemType) {
                                                        MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                        else -> onSongClick(track, section.tracks, section.title)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Music Videos
                            if (uiState.musicVideos.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_music_videos))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.musicVideos) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = currentGridThumbnailHeight(),
                                                aspectRatio = 16f / 9f,
                                                onClick = { onVideoClick(track) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Genre Sections
                            uiState.genreTracks.entries.take(3).forEach { (genre, tracks) ->
                                item {
                                    SectionTitle(title = stringResource(R.string.genre_mix_template, genre))
                                    val genreThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(tracks) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = genreThumbnailHeight,
                                                onClick = { onSongClick(track, tracks, genre) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic Home Sections
                            uiState.dynamicSections.take(5).forEach { section ->
                                if (!section.title.contains("Quick picks", true) && 
                                    !section.title.contains("Music videos", true) &&
                                    !section.title.contains("Long listens", true) &&
                                    !section.title.contains("Mixed for you", true) &&
                                    !section.title.contains("Recommended", true) &&
                                    !section.title.contains("Listen again", true)) {
                                    
                                    item {
                                        SectionTitle(title = section.title)
                                        val sectionThumbnailHeight = currentGridThumbnailHeight()
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(section.tracks) { track ->
                                                GridItem(
                                                    title = track.title,
                                                    subtitle = track.artist,
                                                    thumbnailUrl = track.thumbnailUrl,
                                                    thumbnailHeight = sectionThumbnailHeight,
                                                    onClick = { 
                                                    when (track.itemType) {
                                                        MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                        else -> onSongClick(track, section.tracks, section.title)
                                                    }
                                                }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Top Albums (New Section)
                            if (uiState.topAlbums.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_top_albums)) // Ensure string resource exists or use literal for now "Top Albums"
                                    val albumThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.topAlbums) { album ->
                                            GridItem(
                                                title = album.title,
                                                subtitle = album.author,
                                                thumbnailUrl = album.thumbnailUrl,
                                                thumbnailHeight = albumThumbnailHeight,
                                                onClick = { onAlbumClick(album.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            // New Releases
                            if (uiState.newReleases.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_new_releases))
                                    val newReleaseThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.newReleases.take(10)) { track ->
                                            GridItem(
                                                title = track.title,
                                                subtitle = stringResource(R.string.subtitle_single_template, track.artist),
                                                thumbnailUrl = track.thumbnailUrl,
                                                thumbnailHeight = newReleaseThumbnailHeight,
                                                onClick = { 
                                                    when (track.itemType) {
                                                        MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                        else -> onSongClick(track, uiState.newReleases, "new_releases")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Charts
                            if (uiState.trendingSongs.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.trending))
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(4),
                                        state = rememberLazyGridState(),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                            .fillMaxWidth()
                                    ) {
                                        items(uiState.trendingSongs.take(20).size) { index ->
                                            val track = uiState.trendingSongs[index]
                                            ChartTrackItem(
                                                rank = index + 1,
                                                title = track.title,
                                                artist = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                isPlaying = currentTrack?.videoId == track.videoId,
                                                onClick = { onSongClick(track, uiState.trendingSongs, "charts") },
                                                onLongClick = {
                                                    selectedTrack = track
                                                    showBottomSheet = true
                                                },
                                                modifier = Modifier.width(280.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Popular Artists
                            if (popularArtists.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_popular_artists))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(popularArtists) { track ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .clickable { onArtistClick(track.channelId) }
                                            ) {
                                                ArtistThumbnail(
                                                    thumbnailUrl = track.thumbnailUrl,
                                                    size = 100.dp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = track.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Mixed for you 
                            if (uiState.featuredPlaylists.isNotEmpty()) {
                                item {
                                    SectionTitle(title = stringResource(R.string.section_mixed_for_you))
                                    val playlistThumbnailHeight = currentGridThumbnailHeight()
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.featuredPlaylists) { playlist ->
                                            GridItem(
                                                title = playlist.title,
                                                subtitle = playlist.author,
                                                thumbnailUrl = playlist.thumbnailUrl,
                                                thumbnailHeight = playlistThumbnailHeight,
                                                onClick = { onAlbumClick(playlist.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.moodsAndGenres.isNotEmpty()) {
                                item {
                                    NavigationTitle(
                                        title = stringResource(R.string.section_moods_and_genres),
                                        onClick = { onMoodsClick(null) },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                    
                                    val moodItems = remember(uiState.moodsAndGenres) {
                                        uiState.moodsAndGenres.flatMap { it.items }
                                    }
                                    
                                    val rows = 4
                                    val gridHeight = (Dimensions.MoodButtonHeight * rows) + (8.dp * (rows - 1))
                                    
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(rows),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .height(gridHeight)
                                            .fillMaxWidth()
                                    ) {
                                        items(moodItems) { item ->
                                            MoodAndGenresButton(
                                                title = item.title,
                                                onClick = { onMoodsClick(item) },
                                                modifier = Modifier.width(190.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.homeContinuation != null) {
                                item {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMoreHomeContent()
                                    }
                                    Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (uiState.isMoreLoading) {
                                                Modifier.padding(16.dp)
                                            } else {
                                                Modifier.height(0.dp)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isMoreLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
