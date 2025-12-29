package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPage(
    artistDetails: ArtistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onAlbumClick: (MusicPlaylist) -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp) // Space for player
            ) {
                // Header Image & Info
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        AsyncImage(
                            model = artistDetails.bannerUrl.ifEmpty { artistDetails.thumbnailUrl },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.background
                                        ),
                                        startY = 300f
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
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatSubscribers(artistDetails.subscriberCount)} Subscribers",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = onFollowClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (artistDetails.isSubscribed) 
                                            MaterialTheme.colorScheme.surfaceVariant 
                                        else 
                                            MaterialTheme.colorScheme.primary,
                                        contentColor = if (artistDetails.isSubscribed) 
                                            MaterialTheme.colorScheme.onSurfaceVariant 
                                        else 
                                            MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Text(if (artistDetails.isSubscribed) "Following" else "Follow")
                                }
                            }
                        }
                    }
                }

                // Popular Songs
                if (artistDetails.topTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Popular",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                    
                    items(artistDetails.topTracks) { track ->
                        ArtistTrackItem(track = track, onClick = { onTrackClick(track, artistDetails.topTracks) })
                    }
                }

                // Albums
                if (artistDetails.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
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

                // About
                if (artistDetails.description.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = artistDetails.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // Back Button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ArtistTrackItem(track: MusicTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AlbumCard(album: MusicPlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp)),
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
            text = "${album.trackCount} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

private fun formatSubscribers(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
