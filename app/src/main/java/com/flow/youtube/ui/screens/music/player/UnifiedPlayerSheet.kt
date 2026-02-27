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
import androidx.compose.material3.HorizontalDivider
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        shadowElevation = 24.dp,
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .align(Alignment.CenterHorizontally)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
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
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { 
                                        if (!isSelected) {
                                            onTabSelect(tab)
                                            onExpand()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
                
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
