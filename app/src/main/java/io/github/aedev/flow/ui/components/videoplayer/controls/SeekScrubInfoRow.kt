package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimPanel
import io.github.aedev.flow.ui.theme.defaultSponsorBlockColor
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.utils.sponsorCategoryLabelRes

private val ScrubChipHeight: Dp = 24.dp
private val ScrubChipSpacing: Dp = 6.dp
private val ScrubChipDotSize: Dp = 6.dp

/**
 * The read-out above the preview: where the scrub has reached, and what is playing there.
 *
 * Centred on the thumb and clamped to the track, so it reads as belonging to the frame under it
 * rather than to the bar as a whole. Only the chapter flexes — everything else is short enough to
 * keep its width, so a long chapter title ellipsizes instead of pushing the rest off the track.
 */
@Composable
internal fun SeekScrubInfoRow(
    positionProvider: () -> Long,
    durationMs: Long,
    content: PlayerSeekbarContent,
    modifier: Modifier = Modifier,
) {
    val info by remember(content, positionProvider) {
        derivedStateOf {
            scrubInfoAt(positionProvider(), content.chapters, content.sponsorSegments, content.heatmap)
        }
    }
    val elapsedSeconds by remember(positionProvider) { derivedStateOf { positionProvider() / 1000L } }

    Row(
        modifier = modifier.anchoredToThumb(positionProvider, durationMs),
        horizontalArrangement = Arrangement.spacedBy(ScrubChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrubChip(text = remember(elapsedSeconds) { formatDuration(elapsedSeconds.toInt(), padMinutes = true) })
        info.chapterTitle?.let { title ->
            ScrubChip(text = title, modifier = Modifier.weight(1f, fill = false))
        }
        info.sponsorCategory?.let { category ->
            sponsorCategoryLabelRes(category)?.let { labelRes ->
                ScrubChip(
                    text = stringResource(labelRes),
                    dotColor = content.sponsorColors[category] ?: defaultSponsorBlockColor(category),
                )
            }
        }
        info.highlightLabel?.let { label -> ScrubChip(text = label) }
    }
}

@Composable
private fun RowScope.ScrubChip(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    Surface(
        color = PlayerScrimPanel,
        shape = CircleShape,
        modifier = modifier.height(ScrubChipHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScrubChipSpacing),
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            if (dotColor != null) {
                Box(modifier = Modifier.size(ScrubChipDotSize).background(dotColor, CircleShape))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = PlayerScrimContent,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
