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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.data.lyrics.LyricsEntry
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
    sheetCornerRadius: androidx.compose.ui.unit.Dp = 32.dp,
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
    syncedLyrics: List<LyricsEntry>,
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
        shape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        border = BorderStroke(
            width = 1.dp, 
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f), 
                    Color.Transparent
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E1E).copy(alpha = 0.90f),
                            Color.Black.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .width(48.dp)
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(64.dp),
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
                                    targetValue = if (isSelected) 32f else 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                                
                                if (indicatorWidth > 0f) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(indicatorWidth.dp)
                                            .height(4.dp)
                                            .background(Color.White, CircleShape)
                                            .shadow(8.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius))
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
