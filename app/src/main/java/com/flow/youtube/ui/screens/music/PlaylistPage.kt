package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPage(
    playlistDetails: PlaylistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onArtistClick: (String) -> Unit,
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 100 } }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient Background (Blurred)
        AsyncImage(
            model = playlistDetails.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .blur(70.dp)
                .alpha(0.4f),
            contentScale = ContentScale.Crop
        )
        
        // Gradient Overlay for Ambient Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent, // Make Scaffold transparent to show ambient bg
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isScrolled) MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            else Color.Transparent
                        )
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    if (isScrolled) {
                        Text(
                            text = playlistDetails.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 48.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = { /* Search */ },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Header Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Large Thumbnail with Shadow/Elevation feel
                        Surface(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 16.dp
                        ) {
                            AsyncImage(
                                model = playlistDetails.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Title
                        Text(
                            text = playlistDetails.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Artist Info (The "a tag" equivalent)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { playlistDetails.authorId?.let { onArtistClick(it) } }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (playlistDetails.authorAvatarUrl != null) {
                                AsyncImage(
                                    model = playlistDetails.authorAvatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            } else {
                                // Fallback icon for artist
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = playlistDetails.author,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata
                        val metadata = buildString {
                            playlistDetails.views?.let { append("${formatViews(it)} views • ") }
                            playlistDetails.durationText?.let { append("$it • ") }
                            playlistDetails.dateText?.let { append(it) }
                        }.trim().removeSuffix("•").trim()

                        if (metadata.isNotEmpty()) {
                            Text(
                                text = metadata,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Description
                        playlistDetails.description?.let { desc ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Action Buttons - Premium Look
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PremiumActionButton(icon = Icons.Outlined.Download, onClick = onDownloadClick)
                            PremiumActionButton(icon = Icons.Outlined.FavoriteBorder, onClick = {})
                            
                            // Play Button - Large and Central
                            Surface(
                                onClick = { 
                                    if (playlistDetails.tracks.isNotEmpty()) {
                                        onTrackClick(playlistDetails.tracks.first(), playlistDetails.tracks)
                                    }
                                },
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(72.dp),
                                shadowElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            PremiumActionButton(icon = Icons.Outlined.Share, onClick = onShareClick)
                            PremiumActionButton(icon = Icons.Default.MoreVert, onClick = {})
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Tracks List
                itemsIndexed(playlistDetails.tracks) { index, track ->
                    MusicTrackRow(
                        track = track,
                        onClick = { onTrackClick(track, playlistDetails.tracks) }
                    )
                }

                // Footer
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${playlistDetails.trackCount} tracks • ${playlistDetails.durationText ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}
