package com.flow.youtube.ui.screens.music.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.ui.screens.music.LyricLine
import com.flow.youtube.ui.screens.music.MusicTrack

enum class PlayerTab {
    UP_NEXT, LYRICS, RELATED
}

@Composable
fun UnifiedPlayerSheet(
    currentTab: PlayerTab,
    onTabSelect: (PlayerTab) -> Unit,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    // Up Next Params
    queue: List<MusicTrack>,
    currentIndex: Int,
    playingFrom: String,
    autoplayEnabled: Boolean,
    selectedFilter: String,
    onTrackClick: (Int) -> Unit,
    onToggleAutoplay: () -> Unit,
    onFilterSelect: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    // Lyrics Params
    lyrics: String?,
    syncedLyrics: List<LyricLine>,
    currentPosition: Long,
    isLyricsLoading: Boolean,
    onSeekTo: (Long) -> Unit,
    // Related Params
    relatedTracks: List<MusicTrack>,
    isRelatedLoading: Boolean,
    onRelatedTrackClick: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Tab Row (Drag Handle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) 
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerTab.values().forEach { tab ->
                val title = when(tab) {
                    PlayerTab.UP_NEXT -> stringResource(R.string.up_next)
                    PlayerTab.LYRICS -> stringResource(R.string.lyrics)
                    PlayerTab.RELATED -> stringResource(R.string.related)
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            onTabSelect(tab)
                            onExpand()
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isExpanded && tab == currentTab) FontWeight.Bold else FontWeight.Medium,
                        color = if (isExpanded && tab == currentTab) Color.White else Color.White.copy(alpha = 0.6f)
                    )
                    
                    val alpha by animateFloatAsState(if (isExpanded && tab == currentTab) 1f else 0f)
                    if (alpha > 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .background(Color.White, MaterialTheme.shapes.small)
                        )
                    } else {
                         Spacer(modifier = Modifier.height(9.dp)) 
                    }
                }
            }
        }
        
        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)) 
        ) {
            when (currentTab) {
                PlayerTab.UP_NEXT -> UpNextContent(
                    queue = queue,
                    currentIndex = currentIndex,
                    playingFrom = playingFrom,
                    autoplayEnabled = autoplayEnabled,
                    selectedFilter = selectedFilter,
                    onTrackClick = onTrackClick,
                    onToggleAutoplay = onToggleAutoplay,
                    onFilterSelect = onFilterSelect,
                    onMoveTrack = onMoveTrack
                )
                PlayerTab.LYRICS -> LyricsContent(
                    lyrics = lyrics,
                    syncedLyrics = syncedLyrics,
                    currentPosition = currentPosition,
                    isLoading = isLyricsLoading,
                    onSeekTo = onSeekTo
                )
                PlayerTab.RELATED -> RelatedContent(
                    relatedTracks = relatedTracks,
                    isLoading = isRelatedLoading,
                    onTrackClick = onRelatedTrackClick
                )
            }
        }
    }
}
