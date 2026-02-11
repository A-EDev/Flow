package com.flow.youtube.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerTab.values().forEach { tab ->
                        val isSelected = isExpanded && tab == currentTab
                        val title = when(tab) {
                            PlayerTab.UP_NEXT -> stringResource(R.string.up_next)
                            PlayerTab.LYRICS -> stringResource(R.string.lyrics)
                            PlayerTab.RELATED -> stringResource(R.string.related)
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { 
                                    onTabSelect(tab)
                                    onExpand()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = if (isSelected) 1.05f else 1f
                                        scaleY = if (isSelected) 1.05f else 1f
                                    }
                                )
                                
                                val indicatorWidth by animateFloatAsState(
                                    targetValue = if (isSelected) 20f else 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                                
                                if (indicatorWidth > 0f) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(indicatorWidth.dp)
                                            .height(3.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(7.dp))
                                }
                            }
                        }
                    }
                }
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
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
    }
}
