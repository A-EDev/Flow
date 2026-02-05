package com.flow.youtube.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.components.MusicQuickActionsSheet
import com.flow.youtube.ui.screens.music.components.*
import com.flow.youtube.ui.screens.music.tabs.*

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
    onMoodsClick: () -> Unit = {},
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
                    putExtra(Intent.EXTRA_TEXT, "Check out this song: ${selectedTrack!!.title} by ${selectedTrack!!.artist}\nhttps://music.youtube.com/watch?v=${selectedTrack!!.videoId}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share song"))
            }
        )
    }
    

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MUSIC",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, "Settings")
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
                uiState.isLoading && uiState.trendingSongs.isEmpty() -> {
                    LoadingContent()
                }
                
                uiState.error != null && uiState.trendingSongs.isEmpty() -> {
                    ErrorContent(
                        error = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    // Popular Artists
                    val popularArtists = remember(uiState.trendingSongs, uiState.newReleases) {
                        (uiState.trendingSongs + uiState.newReleases)
                            .distinctBy { it.artist }
                            .take(10)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // Moods & Genres Entry
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable(onClick = onMoodsClick),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "Moods & genres",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }

                        // Listen Again
                        if (uiState.listenAgain.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Listen again")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.listenAgain) { track ->
                                        AlbumCard(
                                            title = track.title,
                                            subtitle = track.artist,
                                            thumbnailUrl = track.thumbnailUrl,
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
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.homeChips) { chip ->
                                        val isSelected = uiState.selectedHomeChip?.title == chip.title
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { 
                                                if (isSelected) {
                                                    viewModel.setHomeChip(null)
                                                } else {
                                                    viewModel.setHomeChip(chip)
                                                }
                                            },
                                            label = { Text(chip.title) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter != null) {
                            // Filtered List View
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
                            // Quick Picks
                            if (uiState.forYouTracks.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Quick picks", 
                                        onPlayAll = {
                                            if (uiState.forYouTracks.isNotEmpty()) {
                                                onSongClick(uiState.forYouTracks.first(), uiState.forYouTracks, "quick_picks")
                                            }
                                        }
                                    )
                                    QuickPicksGrid(
                                        songs = uiState.forYouTracks.take(16),
                                        currentVideoId = currentTrack?.videoId,
                                        onSongClick = onSongClick,
                                        onMenuClick = { track ->
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }

                            // Recommended for you
                            if (uiState.recommendedTracks.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Recommended for you",
                                        onPlayAll = {
                                            if (uiState.recommendedTracks.isNotEmpty()) {
                                                onSongClick(uiState.recommendedTracks.first(), uiState.recommendedTracks, "recommended")
                                            }
                                        }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.recommendedTracks) { track ->
                                            AlbumCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onSongClick(track, uiState.recommendedTracks, "recommended") }
                                            )
                                        }
                                    }
                                }
                            }
                        
                            // Speed Dial (History)
                            if (uiState.history.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Listen again", subtitle = "Based on your history")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.history.distinctBy { it.videoId }.take(10)) { track ->
                                            SquircleHistoryItem(
                                                title = track.title,
                                                artist = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onSongClick(track, uiState.history, "history") }
                                            )
                                        }
                                    }
                                }
                            }

                            // Long Listens
                            if (uiState.longListens.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Long listens")
                                    QuickPicksGrid(
                                        songs = uiState.longListens.take(16),
                                        currentVideoId = currentTrack?.videoId,
                                        onSongClick = onSongClick,
                                        onMenuClick = { track ->
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }

                            // Music Videos
                            if (uiState.musicVideos.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Music Videos for you")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.musicVideos) { track ->
                                            VideoCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onVideoClick(track) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Genre Sections
                            uiState.genreTracks.forEach { (genre, tracks) ->
                                item {
                                    SectionHeader(title = "$genre Mix")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(tracks) { track ->
                                            AlbumCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onSongClick(track, tracks, genre) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic Home Sections (Smart Content)
                            uiState.dynamicSections.forEach { section ->
                                if (!section.title.contains("Quick picks", true) && 
                                    !section.title.contains("Music videos", true) &&
                                    !section.title.contains("Long listens", true) &&
                                    !section.title.contains("Mixed for you", true) &&
                                    !section.title.contains("Recommended", true) &&
                                    !section.title.contains("Listen again", true)) {
                                    
                                    item {
                                        SectionHeader(
                                            title = section.title, 
                                            subtitle = section.subtitle,
                                            onPlayAll = {
                                                if (section.tracks.isNotEmpty()) {
                                                    onSongClick(section.tracks.first(), section.tracks, section.title)
                                                }
                                            }
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(section.tracks) { track ->
                                                AlbumCard(
                                                    title = track.title,
                                                    subtitle = track.artist,
                                                    thumbnailUrl = track.thumbnailUrl,
                                                    onClick = { onSongClick(track, section.tracks, section.title) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // New Releases
                            if (uiState.newReleases.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "New releases")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.newReleases.take(10)) { track ->
                                            AlbumCard(
                                                title = track.title,
                                                subtitle = "Single • ${track.artist}",
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onSongClick(track, uiState.newReleases, "new_releases") }
                                            )
                                        }
                                    }
                                }
                            }

                            // Music Videos
                            if (uiState.musicVideos.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Music videos", subtitle = "Recommended for you")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.musicVideos) { track ->
                                            VideoCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onVideoClick(track) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Charts
                            if (uiState.trendingSongs.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Charts", onPlayAll = {
                                        if (uiState.trendingSongs.isNotEmpty()) {
                                            onSongClick(uiState.trendingSongs.first(), uiState.trendingSongs, "charts")
                                        }
                                    })
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.trendingSongs.take(6)) { track ->
                                            AlbumCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onSongClick(track, uiState.trendingSongs, "charts") }
                                            )
                                        }
                                    }
                                }
                            }

                            if (popularArtists.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Popular Artists")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(popularArtists) { track ->
                                            ArtistCircleItem(
                                                artist = track.artist,
                                                thumbnailUrl = track.thumbnailUrl,
                                                onClick = { onArtistClick(track.channelId) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Long Listens
                            if (uiState.longListens.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Long listens", subtitle = "Deep dives and albums")
                                    QuickPicksGrid(
                                        songs = uiState.longListens.take(12),
                                        currentVideoId = currentTrack?.videoId,
                                        onSongClick = onSongClick,
                                        onMenuClick = { track ->
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }

                            // Mixed for you (Official Playlists)
                            if (uiState.featuredPlaylists.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Mixed for you")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.featuredPlaylists) { playlist ->
                                            AlbumCard(
                                                title = playlist.title,
                                                subtitle = playlist.author,
                                                thumbnailUrl = playlist.thumbnailUrl,
                                                onClick = { onAlbumClick(playlist.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            // From the Community (Genre Playlists)
                            if (uiState.genreTracks.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "From the community")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(uiState.genreTracks.keys.take(5).toList()) { genre ->
                                            val tracks = uiState.genreTracks[genre] ?: emptyList()
                                            CommunityCard(
                                                title = genre,
                                                subtitle = "Community Playlist",
                                                tracks = tracks.take(3),
                                                onCardClick = { onAlbumClick("community_$genre") },
                                                onPlayClick = {
                                                    if (tracks.isNotEmpty()) {
                                                        onSongClick(tracks.first(), tracks, "community_$genre")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Continuation Loader
                            if (uiState.homeContinuation != null) {
                                item {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMoreHomeContent()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
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
