package io.github.aedev.flow.ui.screens.music


import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import io.github.aedev.flow.ui.TabScrollEventBus
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.screens.music.components.*
import io.github.aedev.flow.ui.screens.music.tabs.*
import io.github.aedev.flow.ui.theme.Dimensions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

private fun MusicTrack.isAudioMusicCandidate(): Boolean {
    val usableDuration = duration == 0 || duration in 30..1200
    return itemType == MusicItemType.SONG && !isVideoSong && videoId.isNotBlank() && usableDuration
}

private fun List<MusicTrack>.audioMusicOnly(): List<MusicTrack> =
    filter { it.isAudioMusicCandidate() }.distinctBy { it.videoId }

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
    onMoodsClick: (io.github.aedev.flow.innertube.pages.MoodAndGenres.Item?) -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val musicListState = rememberLazyListState()
    val quickPicksGridState = rememberLazyGridState()

    val sectionOrder = remember(uiState.sessionSeed) {
        val defaultOrder = HomeSectionType.values().toList()
        val anchored = listOf(
            HomeSectionType.QUICK_PICKS,
            HomeSectionType.FROM_COMMUNITY,
            HomeSectionType.DAILY_DISCOVER
        )
        val dynamicPool = defaultOrder - anchored
        anchored + dynamicPool.shuffled(java.util.Random(uiState.sessionSeed))
    }

    // Scroll to top and refresh when tapping the music tab while already on this screen
    LaunchedEffect(Unit) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "music" }
            .collectLatest {
                musicListState.animateScrollToItem(0)
                viewModel.refresh()
            }
    }
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = { channelId ->
                if (channelId.isNotEmpty()) {
                    onArtistClick(channelId)
                }
            },
            onViewAlbum = { albumId ->
                if (albumId.isNotEmpty()) {
                    onAlbumClick(albumId)
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isInitialLoading = uiState.isLoading && uiState.trendingSongs.isEmpty() && uiState.dynamicSections.isEmpty()

            when {
                isInitialLoading -> {
                    MusicScreenShimmerLoading()
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

                    val speedDialTracks = remember(uiState.history, uiState.forYouTracks, uiState.listenAgain) {
                        (uiState.history + uiState.forYouTracks + uiState.listenAgain)
                            .audioMusicOnly()
                            .take(26)
                    }
                    val quickPickTracks = remember(uiState.forYouTracks) {
                        uiState.forYouTracks.audioMusicOnly().take(20)
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = musicListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = 80.dp
                            )
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
                            if (speedDialTracks.isNotEmpty()) {
                                item {
                                    SpeedDialSection(
                                        speedDialTracks = speedDialTracks,
                                        onSongClick = onSongClick
                                    )
                                }
                            }

                            sectionOrder.forEach { sectionType ->
                                when (sectionType) {
                                    HomeSectionType.DAILY_DISCOVER -> {
                                        if (uiState.dailyDiscover.isNotEmpty()) {
                                            item {
                                                val discoverTracks = uiState.dailyDiscover.map { it.recommendation }.audioMusicOnly()
                                                SectionHeader(
                                                    title = stringResource(R.string.section_daily_discover),
                                                    onPlayAll = discoverTracks.firstOrNull()?.let { first ->
                                                        { onSongClick(first, discoverTracks, "daily_discover") }
                                                    }
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(336.dp)
                                                        .padding(bottom = 16.dp)
                                                ) {
                                                    items(uiState.dailyDiscover.filter { it.recommendation.isAudioMusicCandidate() }) { item ->
                                                        DailyDiscoverCard(
                                                            item = item,
                                                            onClick = {
                                                                onSongClick(item.recommendation, discoverTracks, "daily_discover")
                                                            },
                                                            onLongClick = {
                                                                selectedTrack = item.recommendation
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.QUICK_PICKS -> {
                                        if (quickPickTracks.isNotEmpty()) {
                                            item {
                                                SectionHeader(
                                                    title = stringResource(R.string.section_quick_picks),
                                                    onPlayAll = quickPickTracks.firstOrNull()?.let { first ->
                                                        { onSongClick(first, quickPickTracks, "quick_picks") }
                                                    }
                                                )
                                                LazyHorizontalGrid(
                                                    rows = GridCells.Fixed(4),
                                                    state = quickPicksGridState,
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    items(quickPickTracks) { track ->
                                                        ListItem(
                                                            title = track.title,
                                                            subtitle = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            isPlaying = currentTrack?.videoId == track.videoId,
                                                            onClick = { onSongClick(track, quickPickTracks, "quick_picks") },
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
                                    }
                                    HomeSectionType.FROM_COMMUNITY -> {
                                        if (uiState.communityPlaylists.isNotEmpty()) {
                                            item {
                                                CommunityPlaylistsSection(
                                                    playlists = uiState.communityPlaylists,
                                                    onPlaylistClick = { onAlbumClick(it.playlist.id) },
                                                    onTrackClick = { track, tracks ->
                                                        onSongClick(track, tracks, "from_the_community")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.RECOMMENDED -> {
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
                                    }
                                    HomeSectionType.SIMILAR_TO -> {
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
                                    }
                                    HomeSectionType.LIVE_PERFORMANCES -> {
                                        if (uiState.livePerformances.isNotEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_live_performances),
                                                    tracks = uiState.livePerformances,
                                                    onPlayAll = {
                                                        uiState.livePerformances.firstOrNull()?.let {
                                                            onSongClick(it, uiState.livePerformances, "live_performances")
                                                        }
                                                    },
                                                    onTrackClick = { track -> onSongClick(track, uiState.livePerformances, "live_performances") },
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.MUSIC_VIDEOS_FOR_YOU -> {
                                        val videosForYou = uiState.musicVideosForYou.ifEmpty { uiState.musicVideos }
                                        if (videosForYou.isNotEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_music_videos_for_you),
                                                    tracks = videosForYou,
                                                    onPlayAll = { videosForYou.firstOrNull()?.let(onVideoClick) },
                                                    onTrackClick = onVideoClick,
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.MUSIC_VIDEOS -> {
                                        if (uiState.musicVideos.isNotEmpty() && uiState.musicVideosForYou.isEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_music_videos),
                                                    tracks = uiState.musicVideos,
                                                    onPlayAll = { uiState.musicVideos.firstOrNull()?.let(onVideoClick) },
                                                    onTrackClick = onVideoClick,
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.GENRES -> {
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
                                    }
                                    HomeSectionType.DYNAMIC_HOME -> {
                                        uiState.dynamicSections.forEach { section ->
                                            if (!section.title.contains("Quick picks", true) && 
                                                !section.title.contains("Music videos", true) &&
                                                !section.title.contains("Music videos for you", true) &&
                                                !section.title.contains("Live performances", true) &&
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
                                    }
                                    HomeSectionType.TOP_ALBUMS -> {
                                        if (uiState.topAlbums.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_top_albums))
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
                                    }
                                    HomeSectionType.NEW_RELEASES -> {
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
                                    }
                                    HomeSectionType.CHARTS -> {
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
                                    }
                                    HomeSectionType.POPULAR_ARTISTS -> {
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
                                    }
                                    HomeSectionType.MIXED_FOR_YOU -> {
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
                                    }
                                    HomeSectionType.MOODS_AND_GENRES -> {
                                        if (uiState.moodsAndGenres.isNotEmpty()) {
                                            item {
                                                NavigationTitle(
                                                    title = stringResource(R.string.section_mood_and_genres),
                                                    onClick = { onMoodsClick(null) },
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                                
                                                val moodItems = remember(uiState.moodsAndGenres) {
                                                    uiState.moodsAndGenres.flatMap { it.items }
                                                }
                                                
                                                val rows = 4
                                                val moodButtonWidth = ((LocalConfiguration.current.screenWidthDp.dp - 36.dp) / 2)
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
                                                            modifier = Modifier.width(moodButtonWidth)
                                                        )
                                                    }
                                                }
                                            }
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
}

@Composable
fun CommunityPlaylistsSection(
    playlists: List<CommunityMusicPlaylist>,
    onPlaylistClick: (CommunityMusicPlaylist) -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.section_from_the_community))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(playlists) { item ->
                CommunityPlaylistCard(
                    item = item,
                    onPlaylistClick = { onPlaylistClick(item) },
                    onTrackClick = { track -> onTrackClick(track, item.tracks) }
                )
            }
        }
    }
}

@Composable
fun CommunityPlaylistCard(
    item: CommunityMusicPlaylist,
    onPlaylistClick: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(328.dp)
            .height(420.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onPlaylistClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MosaicThumbnail(
                    tracks = item.tracks,
                    modifier = Modifier.size(104.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.playlist.author.ifBlank {
                            item.playlist.trackCount.takeIf { it > 0 }?.let { "$it tracks" } ?: ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                item.tracks.take(3).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onTrackClick(track) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                FilledTonalIconButton(onClick = { item.tracks.firstOrNull()?.let(onTrackClick) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_all))
                }
                FilledTonalIconButton(onClick = onPlaylistClick) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun MosaicThumbnail(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(2) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(2) { col ->
                        val track = tracks.getOrNull(row * 2 + col)
                        AsyncImage(
                            model = track?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaTrackListSection(
    title: String,
    tracks: List<MusicTrack>,
    onPlayAll: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, onPlayAll = onPlayAll)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(336.dp)
                .padding(bottom = 12.dp)
        ) {
            items(tracks.take(16)) { track ->
                WideMediaTrackItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onMenuClick = { onTrackMenu(track) },
                    modifier = Modifier.width(360.dp)
                )
            }
        }
    }
}

@Composable
fun WideMediaTrackItem(
    track: MusicTrack,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.recommendation.highResThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f),
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val messages = listOf(
                    R.string.daily_discover_sounds_like,
                    R.string.daily_discover_because_you_listen_to,
                    R.string.daily_discover_similar_to,
                    R.string.daily_discover_for_fans_of,
                    R.string.daily_discover_based_on
                )
                val seedMessage = remember(item.seed.videoId) {
                    val index = Math.abs(item.seed.videoId.hashCode()) % messages.size
                    messages[index]
                }
                val seedDescription = "${item.seed.title} • ${item.seed.artist}"
                
                Column {
                    Text(
                        text = stringResource(R.string.daily_discover_subtitle) + stringResource(seedMessage, seedDescription).take(0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.seed.title} • ${item.seed.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                Column {
                    Text(
                        text = item.recommendation.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.recommendation.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

enum class HomeSectionType {
    DAILY_DISCOVER,
    QUICK_PICKS,
    FROM_COMMUNITY,
    RECOMMENDED,
    SIMILAR_TO,
    LIVE_PERFORMANCES,
    MUSIC_VIDEOS_FOR_YOU,
    MUSIC_VIDEOS,
    GENRES,
    DYNAMIC_HOME,
    TOP_ALBUMS,
    NEW_RELEASES,
    CHARTS,
    POPULAR_ARTISTS,
    MIXED_FOR_YOU,
    MOODS_AND_GENRES
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialSection(
    speedDialTracks: List<MusicTrack>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (speedDialTracks.isEmpty()) return

    val pageCount = remember(speedDialTracks) {
        1 + ((speedDialTracks.size - 8).coerceAtLeast(0) + 8) / 9
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val horizontalPadding = 24.dp
    val gap = 8.dp
    val itemSize = (screenWidth - horizontalPadding - gap * 2) / 3

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp)
    ) {
        SectionTitle(title = stringResource(R.string.section_speed_dial))
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemSize * 3 + gap * 2),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp
        ) { page ->
            val pageTracks = if (page == 0) {
                speedDialTracks.take(8)
            } else {
                speedDialTracks.drop(8 + (page - 1) * 9).take(9)
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                for (rowIndex in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        for (colIndex in 0 until 3) {
                            val slotIndex = rowIndex * 3 + colIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                if (page == 0 && slotIndex == 8) {
                                    RandomizeSpeedDialCard(
                                        onClick = {
                                            val shuffled = speedDialTracks.shuffled()
                                            if (shuffled.isNotEmpty()) {
                                                onSongClick(shuffled.first(), shuffled, "speed_dial_shuffle")
                                            }
                                        }
                                    )
                                } else {
                                    pageTracks.getOrNull(slotIndex)?.let { track ->
                                        SpeedDialArtworkCard(
                                            track = track,
                                            onClick = { onSongClick(track, speedDialTracks, "speed_dial") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (pageCount > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedDialArtworkCard(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = track.highResThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun RandomizeSpeedDialCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
            val dotSize = 14.dp
            val dotPadding = 28.dp
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.Center,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).forEach { alignment ->
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .padding(dotPadding)
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
fun ShufflePlayCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.shuffle_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.shuffle_play),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.feeling_lucky),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpeedDialTrackCard(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemThumbnail(
                thumbnailUrl = track.thumbnailUrl,
                size = 40.dp,
                shape = RoundedCornerShape(8.dp)
            )
            
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

