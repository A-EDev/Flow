package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.player.stream.StoryboardLevel
import io.github.aedev.flow.player.stream.StoryboardSpec
import io.github.aedev.flow.ui.components.shared.MediaSeekBar
import io.github.aedev.flow.ui.theme.PlayerLiveIndicator
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.roundToInt

private val SeekPreviewGap = 12.dp

/**
 * Everything a seek bar paints besides the playhead itself. Grouped because all three seek bars in
 * the player — expanded, always-visible and locked — need exactly this set and nothing else.
 */
@Immutable
data class PlayerSeekbarContent(
    val chapters: List<StreamSegment> = emptyList(),
    val sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    val sponsorColors: Map<String, Color> = emptyMap(),
    val bufferedPercentage: Float = 0f,
    val storyboard: List<StoryboardLevel> = emptyList(),
)

/**
 * The seek bar, plus the substitute shown when there is nothing to seek along.
 *
 * A live stream of unknown duration gets a plain progress-less bar instead. Both the expanded
 * controls and the thin always-visible strip need that same either/or, which is why it lives here
 * rather than being spelled out at each of them.
 */
@Composable
internal fun PlayerSeekbarRow(
    positionProvider: () -> Long,
    duration: Long,
    isLive: Boolean,
    content: PlayerSeekbarContent,
    edgeAligned: Boolean,
    horizontalPadding: Dp,
    onScrubProgress: (progress: Float, duration: Long) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
    seekbarZIndex: Float = 0f,
    isScrubbing: Boolean = false,
) {
    if (isLive && duration <= 0L) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlayerLiveIndicator),
        )
        return
    }

    // Derived rather than read: a live timeline's duration is recomputed from the playhead, and a
    // plain read here would recompose this row on every tick for a value that almost never moves.
    val seekDuration by remember(duration, isLive, positionProvider) {
        derivedStateOf { if (isLive) duration.coerceAtLeast(positionProvider()) else duration }
    }
    val showPreview = isScrubbing && content.storyboard.isNotEmpty() && seekDuration > 0L
    var trackWidthPx by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxWidth()) {
        if (showPreview) {
            SeekPreview(
                levels = content.storyboard,
                positionProvider = positionProvider,
                duration = seekDuration,
                trackWidthPx = trackWidthPx,
                horizontalPadding = horizontalPadding,
            )
        }
        SeekBar(
            positionProvider = positionProvider,
            seekDuration = seekDuration,
            content = content,
            edgeAligned = edgeAligned,
            horizontalPadding = horizontalPadding,
            onScrubProgress = onScrubProgress,
            onScrubFinished = onScrubFinished,
            seekbarZIndex = seekbarZIndex,
            onTrackWidth = { trackWidthPx = it },
        )
    }
}

/**
 * The frame under the thumb, floating above the bar.
 *
 * It reports a size of zero so the seek bar row cannot grow when a scrub starts: [Modifier.offset]
 * alone moves a child but leaves the parent measuring it at full height, which is what shoved the
 * controls upward the first time this was built.
 */
@Composable
private fun SeekPreview(
    levels: List<StoryboardLevel>,
    positionProvider: () -> Long,
    duration: Long,
    trackWidthPx: Int,
    horizontalPadding: Dp,
) {
    val density = LocalDensity.current
    val previewWidthPx = with(density) { SeekPreviewWidth.roundToPx() }
    val gapPx = with(density) { SeekPreviewGap.roundToPx() }
    val paddingPx = with(density) { horizontalPadding.roundToPx() }
    val level = remember(levels, previewWidthPx) { StoryboardSpec.levelFor(levels, previewWidthPx) } ?: return
    val position = positionProvider()
    val tile = remember(level, position) { level.tileAt(position) } ?: return
    val fraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    SeekPreviewThumbnail(
        tile = tile,
        modifier =
            Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                val track = (trackWidthPx - 2 * paddingPx).coerceAtLeast(0)
                val centred = paddingPx + fraction * track - placeable.width / 2f
                val maxX = (trackWidthPx - placeable.width).coerceAtLeast(0)
                layout(0, 0) {
                    placeable.place(
                        x = centred.roundToInt().coerceIn(0, maxX),
                        y = -(placeable.height + gapPx),
                    )
                }
            },
    )
}

@Composable
private fun SeekBar(
    positionProvider: () -> Long,
    seekDuration: Long,
    content: PlayerSeekbarContent,
    edgeAligned: Boolean,
    horizontalPadding: Dp,
    onScrubProgress: (progress: Float, duration: Long) -> Unit,
    onScrubFinished: () -> Unit,
    seekbarZIndex: Float,
    onTrackWidth: (Int) -> Unit,
) {
    MediaSeekBar(
        value = {
            if (seekDuration > 0) {
                (positionProvider().toFloat() / seekDuration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        },
        onValueChange = { progress -> onScrubProgress(progress, seekDuration) },
        onValueChangeFinished = onScrubFinished,
        chapters = content.chapters,
        sponsorSegments = content.sponsorSegments,
        sponsorColors = content.sponsorColors,
        duration = seekDuration,
        bufferedValue = content.bufferedPercentage,
        edgeAligned = edgeAligned,
        modifier =
            Modifier
                .fillMaxWidth()
                .onSizeChanged { onTrackWidth(it.width) }
                .zIndex(seekbarZIndex)
                .padding(horizontal = horizontalPadding),
    )
}
