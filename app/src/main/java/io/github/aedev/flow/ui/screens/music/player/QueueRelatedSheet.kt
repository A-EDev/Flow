package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.MusicTrack

enum class QueueRelatedTab {
    UP_NEXT, RELATED
}

val LocalPlayerAccentColor = compositionLocalOf<Color?> { null }
val LocalPlayerOnAccentColor = compositionLocalOf<Color?> { null }
val LocalPlayerOnSheetColor = compositionLocalOf<Color?> { null }

@Composable
fun QueueRelatedSheet(
    sheetBackgroundColor: Color,
    accentColor: Color,
    onSheetColor: Color = Color.Unspecified,
    currentTab: QueueRelatedTab,
    onTabSelect: (QueueRelatedTab) -> Unit,
    sheetCornerRadius: Dp,
    queue: List<MusicTrack>,
    currentIndex: Int,
    playingFrom: String,
    autoplayEnabled: Boolean,
    selectedFilter: String,
    onTrackClick: (Int) -> Unit,
    onToggleAutoplay: () -> Unit,
    onFilterSelect: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    relatedTracks: List<MusicTrack>,
    isRelatedLoading: Boolean,
    onRelatedTrackClick: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedOnSheetColor = remember(sheetBackgroundColor, onSheetColor) {
        if (onSheetColor != Color.Unspecified) {
            onSheetColor
        } else if (sheetBackgroundColor.luminance() < 0.45f) {
            Color.White
        } else {
            Color(0xFF161616)
        }
    }
    val onAccentColor = remember(accentColor) {
        if (accentColor.luminance() < 0.55f) Color.White else Color(0xFF161616)
    }
    val adaptiveSheetColors = lightColorScheme(
        primary = accentColor,
        onPrimary = onAccentColor,
        surface = sheetBackgroundColor,
        onSurface = resolvedOnSheetColor,
        onSurfaceVariant = resolvedOnSheetColor.copy(alpha = 0.72f),
        surfaceVariant = resolvedOnSheetColor.copy(alpha = 0.12f),
        outline = resolvedOnSheetColor.copy(alpha = 0.32f),
        background = sheetBackgroundColor,
        onBackground = resolvedOnSheetColor
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = sheetBackgroundColor,
        shape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        shadowElevation = 24.dp,
        tonalElevation = 0.dp
    ) {
        CompositionLocalProvider(
            LocalPlayerAccentColor provides accentColor,
            LocalPlayerOnAccentColor provides onAccentColor,
            LocalPlayerOnSheetColor provides resolvedOnSheetColor
        ) {
            MaterialTheme(colorScheme = adaptiveSheetColors) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to resolvedOnSheetColor.copy(alpha = 0.08f),
                                    0.28f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = if (sheetBackgroundColor.luminance() < 0.45f) 0.18f else 0.04f)
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 14.dp, bottom = 12.dp)
                                .width(44.dp)
                                .height(5.dp)
                                .background(
                                    color = accentColor.copy(alpha = 0.62f),
                                    shape = CircleShape
                                )
                                .align(Alignment.CenterHorizontally)
                        )

                        QueueRelatedSegmentedTabs(
                            currentTab = currentTab,
                            accentColor = accentColor,
                            onAccentColor = onAccentColor,
                            onTabSelect = onTabSelect,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            color = resolvedOnSheetColor.copy(alpha = 0.10f)
                        )

                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "queueRelatedContent",
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                QueueRelatedTab.UP_NEXT -> UpNextContent(
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

                                QueueRelatedTab.RELATED -> RelatedContent(
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
    }
}

@Composable
private fun QueueRelatedSegmentedTabs(
    currentTab: QueueRelatedTab,
    accentColor: Color,
    onAccentColor: Color,
    onTabSelect: (QueueRelatedTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueRelatedTab.values().forEach { tab ->
                val isSelected = tab == currentTab
                val label = when (tab) {
                    QueueRelatedTab.UP_NEXT -> stringResource(R.string.up_next)
                    QueueRelatedTab.RELATED -> stringResource(R.string.related)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
