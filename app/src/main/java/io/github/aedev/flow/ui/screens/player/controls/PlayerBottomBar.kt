package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.player.components.PlayerTimePill
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import org.schabi.newpipe.extractor.stream.StreamSegment

/** Alpha the bottom gradient reaches at the very bottom of the player. */
private const val BOTTOM_GRADIENT_ALPHA = 0.44f

/** Sizing shared by the pill row and the seek bar beneath it. */
data class PlayerBottomBarMetrics(
    val pillHeight: Dp,
    val pillsRowMinHeight: Dp,
    val actionSpacing: Dp,
    val horizontalPadding: Dp,
    val seekbarHorizontalPadding: Dp,
    val seekbarBottomPadding: Dp,
    val expandIconSize: Dp,
    val chapterMaxWidth: Dp,
)

/**
 * The pill row — comments, elapsed time, chapter, quality, fullscreen — sitting above the seek bar.
 */
@Composable
internal fun PlayerBottomBar(
    positionProvider: () -> Long,
    duration: Long,
    isLive: Boolean,
    isFullscreen: Boolean,
    showRemainingTime: Boolean,
    showCommentsButton: Boolean,
    isCommentsPanelOpen: Boolean,
    currentChapter: StreamSegment?,
    compactQualityLabel: String?,
    seekbarContent: PlayerSeekbarContent,
    metrics: PlayerBottomBarMetrics,
    actions: PlayerControlActions,
    onScrubProgress: (progress: Float, duration: Long) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, PlayerScrim.copy(alpha = BOTTOM_GRADIENT_ALPHA)),
                        ),
                ).padding(bottom = metrics.seekbarBottomPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.pillsRowMinHeight)
                    .zIndex(1f)
                    .offset(y = 0.dp)
                    .padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = if (isFullscreen) 4.dp else 0.dp,
                    ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.actionSpacing),
                modifier = Modifier.weight(1f),
            ) {
                if (showCommentsButton) {
                    Box(
                        modifier =
                            Modifier
                                .size(metrics.pillHeight)
                                .clip(CircleShape)
                                .background(PlayerScrimAffordance)
                                .clickable(onClick = actions.onCommentsClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = stringResource(R.string.comments),
                            tint = if (isCommentsPanelOpen) accentColor else PlayerScrimContent,
                            modifier = Modifier.size(metrics.expandIconSize),
                        )
                    }
                }

                PlayerTimePill(
                    positionProvider = positionProvider,
                    duration = duration,
                    isLive = isLive,
                    showRemainingTime = showRemainingTime,
                    onClick = { if (isLive) actions.onLiveClick() else actions.onToggleRemainingTime() },
                    modifier = Modifier.height(metrics.pillHeight),
                )

                if (currentChapter != null) {
                    Surface(
                        color = PlayerScrimAffordance,
                        shape = CircleShape,
                        modifier =
                            Modifier
                                .height(metrics.pillHeight)
                                .clip(CircleShape)
                                .clickable(onClick = actions.onChapterClick),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = currentChapter.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = PlayerScrimContent,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = metrics.chapterMaxWidth),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = PlayerScrimContent.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.actionSpacing),
            ) {
                if (compactQualityLabel != null) {
                    Surface(
                        color = PlayerScrimAffordance,
                        shape = CircleShape,
                        modifier =
                            Modifier
                                .height(metrics.pillHeight)
                                .widthIn(min = metrics.pillHeight)
                                .clip(CircleShape)
                                .clickable(onClick = actions.onQualityClick),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = compactQualityLabel,
                                color = PlayerScrimContent,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .size(metrics.pillHeight)
                            .clip(CircleShape)
                            .background(PlayerScrimAffordance)
                            .clickable(onClick = actions.onFullscreenClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                        contentDescription = stringResource(R.string.fullscreen),
                        tint = PlayerScrimContent,
                        modifier = Modifier.size(metrics.expandIconSize),
                    )
                }
            }
        }

        PlayerSeekbarRow(
            positionProvider = positionProvider,
            duration = duration,
            isLive = isLive,
            content = seekbarContent,
            edgeAligned = !isFullscreen,
            horizontalPadding = metrics.seekbarHorizontalPadding,
            onScrubProgress = onScrubProgress,
            onScrubFinished = onScrubFinished,
            seekbarZIndex = 2f,
        )
    }
}
