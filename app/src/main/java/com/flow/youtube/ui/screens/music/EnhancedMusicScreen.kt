package com.flow.youtube.ui.screens.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import kotlin.math.absoluteValue
import com.flow.youtube.data.recommendation.MusicSection
import com.flow.youtube.ui.components.MusicQuickActionsSheet
import com.flow.youtube.ui.components.AddToPlaylistDialog
import com.flow.youtube.data.model.Video
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

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
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
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
    
    val categories = listOf("Energize", "Relax", "Feel good", "Workout", "Party", "Focus", "Sleep", "Romance", "Commute")

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
                )
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

                        // Categories Chips
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categories) { category ->
                                    val isSelected = uiState.selectedFilter == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { 
                                            if (isSelected) {
                                                viewModel.setFilter(null)
                                            } else {
                                                viewModel.setFilter(category)
                                            }
                                        },
                                        label = { Text(category) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
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
                                        onClick = { onSongClick(track, uiState.allSongs, uiState.selectedFilter) },
                                        onMenuClick = {
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }
                        } else 
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
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    name: String,
    handle: String,
    thumbnailUrl: String?,
    tracks: List<MusicTrack>
) {
    Surface(
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            tracks.forEach { track ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(track.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                        Text("${formatViews(track.views)} views", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = { /* Options */ }) {
                        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onPlayAll: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onPlayAll != null) {
                Surface(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Play all", 
                            color = MaterialTheme.colorScheme.onPrimary, 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPicksGrid(
    songs: List<MusicTrack>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onMenuClick: (MusicTrack) -> Unit
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        modifier = Modifier
            .height(280.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(songs) { track ->
            QuickPickItem(
                track = track, 
                onClick = { onSongClick(track, songs, "quick_picks") },
                onMenuClick = { onMenuClick(track) }
            )
        }
    }
}

@Composable
fun QuickPickItem(
    track: MusicTrack,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .clickable(onClick = onClick),
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${formatViews(track.views)} plays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun ArtistCircleItem(
    artist: String,
    thumbnailUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SquircleHistoryItem(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CommunityCard(
    title: String,
    subtitle: String,
    tracks: List<MusicTrack>,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(320.dp)
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                // Clickable header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick() }
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 2x2 Grid of thumbnails
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
                        val thumbs = tracks.map { it.thumbnailUrl }
                        Column {
                            Row {
                                AsyncImage(thumbs.getOrNull(0), null, Modifier.size(40.dp), contentScale = ContentScale.Crop)
                                AsyncImage(thumbs.getOrNull(1), null, Modifier.size(40.dp), contentScale = ContentScale.Crop)
                            }
                            Row {
                                AsyncImage(thumbs.getOrNull(2), null, Modifier.size(40.dp), contentScale = ContentScale.Crop)
                                AsyncImage(thumbs.getOrNull(3) ?: thumbs.getOrNull(0), null, Modifier.size(40.dp), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                
                // Track previews (non-clickable, just preview)
                tracks.take(3).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(track.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
            
            // Play button in bottom right corner
            Surface(
                onClick = onPlayClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistPreviewCard(
    title: String,
    subtitle: String,
    tracks: List<MusicTrack>,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 2x2 grid of track thumbnails
            if (tracks.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))) {
                    Column {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                tracks.getOrNull(0)?.thumbnailUrl,
                                null,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            AsyncImage(
                                tracks.getOrNull(1)?.thumbnailUrl,
                                null,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                tracks.getOrNull(2)?.thumbnailUrl,
                                null,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            AsyncImage(
                                tracks.getOrNull(3)?.thumbnailUrl ?: tracks.getOrNull(0)?.thumbnailUrl,
                                null,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoverTab(
    trendingSongs: List<MusicTrack>,
    genreTracks: Map<String, List<MusicTrack>>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp), // Space for mini player
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero section - Featured/Top picks
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Top Picks for You",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(trendingSongs.take(5)) { track ->
                        FeaturedTrackCard(
                            track = track,
                            onClick = { onSongClick(track, trendingSongs, "top_picks") },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }

        // Genre sections
        genreTracks.forEach { (genre, tracks) ->
            if (tracks.isNotEmpty()) {
                item {
                    GenreSection(
                        genre = genre,
                        tracks = tracks,
                        onSongClick = onSongClick,
                        onSeeAllClick = { onGenreClick(genre) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingTab(
    songs: List<MusicTrack>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit,
    onMenuClick: (MusicTrack) -> Unit
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No tracks available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp), // Space for mini player
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(songs.size) { index ->
            TrendingTrackCard(
                track = songs[index],
                rank = index + 1,
                onClick = { onSongClick(songs[index], songs, "trending") },
                onArtistClick = onArtistClick,
                onMenuClick = { onMenuClick(songs[index]) }
            )
        }
    }
}

@Composable
private fun GenresTab(
    genres: List<String>,
    genreTracks: Map<String, List<MusicTrack>>,
    onGenreClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(genres) { genre ->
            GenreCard(
                genre = genre,
                trackCount = genreTracks[genre]?.size ?: 0,
                onClick = { onGenreClick(genre) }
            )
        }
    }
}

@Composable
private fun SearchResults(
    songs: List<MusicTrack>,
    isSearching: Boolean,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isSearching -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            songs.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs.size) { index ->
                        CompactTrackCard(
                            track = songs[index],
                            onClick = { onSongClick(songs[index], songs, "search_results") },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedTrackCard(
    track: MusicTrack,
    onClick: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .width(180.dp)
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp
    ) {
        Box {
            // Background image
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Play button
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Track info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                            Modifier.clickable { onArtistClick(track.channelId) }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreSection(
    genre: String,
    tracks: List<MusicTrack>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onSeeAllClick) {
                Text("See All")
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(tracks.take(10)) { track ->
                GenreTrackCard(
                    track = track,
                    onClick = { onSongClick(track, tracks, "genre_$genre") }
                )
            }
        }
    }
}

@Composable
private fun GenreTrackCard(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column {
            // Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Info
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrendingTrackCard(
    track: MusicTrack,
    rank: Int,
    onClick: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
            
            // Artwork
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null
                )
            }
            
            // Track info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                        Modifier.clickable { onArtistClick(track.channelId) }
                    } else {
                        Modifier
                    }
                )
            }
            
            // Play button
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CompactTrackCard(
    track: MusicTrack,
    onClick: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                        Modifier.clickable { onArtistClick(track.channelId) }
                    } else {
                        Modifier
                    }
                )
            }
            
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: String,
    trackCount: Int,
    onClick: () -> Unit
) {
    val gradientColors = remember(genre) {
        // Generate unique gradient colors for each genre based on hash
        val hash1 = genre.hashCode() and 0xFFFFFF
        val hash2 = genre.reversed().hashCode() and 0xFFFFFF
        listOf(
            Color(0xFF000000 or hash1.toLong()),
            Color(0xFF000000 or hash2.toLong())
        )
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(gradientColors))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun VideoCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MusicTrackRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = "Song • ${track.artist}${if (track.views > 0) " • ${formatViews(track.views)} plays" else ""}"
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color.Red)
            Text(
                text = "Loading music...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Red
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Retry")
            }
        }
    }
}
