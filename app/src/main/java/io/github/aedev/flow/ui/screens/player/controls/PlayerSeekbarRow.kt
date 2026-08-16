package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.ui.screens.player.components.SeekbarWithPreview
import io.github.aedev.flow.ui.theme.PlayerLiveIndicator
import org.schabi.newpipe.extractor.stream.StreamSegment

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

    val seekDuration = if (isLive) duration.coerceAtLeast(positionProvider()) else duration
    SeekbarWithPreview(
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
            modifier
                .fillMaxWidth()
                .zIndex(seekbarZIndex)
                .padding(horizontal = horizontalPadding),
    )
}
