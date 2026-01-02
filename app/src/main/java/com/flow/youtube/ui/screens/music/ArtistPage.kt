package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flow.youtube.ui.components.MusicQuickActionsSheet
import com.flow.youtube.ui.components.AddToPlaylistDialog
import com.flow.youtube.data.model.Video
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPage(
    artistDetails: ArtistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onAlbumClick: (MusicPlaylist) -> Unit,
    onArtistClick: (String) -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0 } }
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
            onViewAlbum = { /* TODO: Implement view album */ },
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
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Fading Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isScrolled) MaterialTheme.colorScheme.background.copy(alpha = 0.95f) 
                        else Color.Transparent
                    )
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isScrolled) MaterialTheme.colorScheme.onBackground else Color.White
                    )
                }
                
                if (isScrolled) {
                    Text(
                        text = artistDetails.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 56.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { _ ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for player
        ) {
            // Header Image & Info
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artistDetails.bannerUrl.ifEmpty { artistDetails.thumbnailUrl })
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.background
                                    ),
                                    startY = 0f
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = artistDetails.name,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (artistDetails.subscriberCount > 0) {
                            Text(
                                text = "${formatViews(artistDetails.subscriberCount)} Monthly Listeners",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onFollowClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (artistDetails.isSubscribed) 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else 
                                        Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                border = if (!artistDetails.isSubscribed) null else null, // Can add border if needed
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = if (artistDetails.isSubscribed) "Subscribed" else "Subscribe",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                if (artistDetails.isSubscribed && artistDetails.subscriberCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatViews(artistDetails.subscriberCount),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Play / Shuffle Buttons could go here or floating
                            IconButton(
                                onClick = { 
                                    if (artistDetails.topTracks.isNotEmpty()) {
                                        onTrackClick(artistDetails.topTracks.random(), artistDetails.topTracks.shuffled())
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            FilledIconButton(
                                onClick = { if (artistDetails.topTracks.isNotEmpty()) onTrackClick(artistDetails.topTracks.first(), artistDetails.topTracks) },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                            }
                        }
                    }
                }
            }

            // Top Songs
            if (artistDetails.topTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top songs",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { /* TODO: View All Songs */ }) {
                            Text("Play all", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                itemsIndexed(artistDetails.topTracks.take(5)) { index, track ->
                    MusicTrackRow(
                        index = index + 1,
                        track = track,
                        onClick = { onTrackClick(track, artistDetails.topTracks) },
                        onMenuClick = { 
                            selectedTrack = track
                            showBottomSheet = true
                        }
                    )
                }
            }

            // Singles & EPs
            if (artistDetails.singles.isNotEmpty()) {
                item { SectionHeader(title = "Singles & EPs") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artistDetails.singles) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }

            // Albums
            if (artistDetails.albums.isNotEmpty()) {
                item { SectionHeader(title = "Albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artistDetails.albums) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
            
            // Videos
            if (artistDetails.videos.isNotEmpty()) {
                item { SectionHeader(title = "Videos") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artistDetails.videos) { video ->
                            VideoCard(video = video, onClick = { onTrackClick(video, listOf(video)) })
                        }
                    }
                }
            }

            // Featured On
            if (artistDetails.featuredOn.isNotEmpty()) {
                item { SectionHeader(title = "Featured on") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artistDetails.featuredOn) { playlist ->
                            AlbumCard(album = playlist, onClick = { onAlbumClick(playlist) }, showAuthor = true)
                        }
                    }
                }
            }
            
            // Fans Also Like
            if (artistDetails.relatedArtists.isNotEmpty()) {
                item { SectionHeader(title = "Fans might also like") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(artistDetails.relatedArtists) { artist ->
                            RelatedArtistCard(
                                artist = artist,
                                onClick = { onArtistClick(artist.channelId) }
                            )
                        }
                    }
                }
            }

            // About
            if (artistDetails.description.isNotEmpty()) {
                item { SectionHeader(title = "About") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = artistDetails.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp)
    )
}


@Composable
fun AlbumCard(album: MusicPlaylist, onClick: () -> Unit, showAuthor: Boolean = false) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (showAuthor) "Playlist • ${album.author}" else "${album.author} • ${if (album.trackCount > 0) "${album.trackCount} tracks" else ""}".trimEnd(' ', '•', ' '),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VideoCard(video: MusicTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(220.dp)
                    .height(124.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        val viewsText = if (video.views > 0) "• ${formatViews(video.views)} views" else ""
        Text(
            text = "${video.artist} $viewsText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RelatedArtistCard(artist: ArtistDetails, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (artist.subscriberCount > 0) {
            Text(
                text = "${formatViews(artist.subscriberCount)} fans",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

