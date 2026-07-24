package io.github.aedev.flow.ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.ui.tv.components.TvButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Manual "Skip <category>" chip shown while the playhead is inside a
 * SponsorBlock segment. Position is sampled at 1 Hz only while segments exist;
 * automatic skipping stays in the engine's SponsorBlockHandler.
 */
@Composable
fun TvSponsorSkipButton(
    segments: List<SponsorBlockSegment>,
    positionProvider: () -> Long,
    onSkipTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeSegment by remember { mutableStateOf<SponsorBlockSegment?>(null) }

    LaunchedEffect(segments) {
        if (segments.isEmpty()) {
            activeSegment = null
            return@LaunchedEffect
        }
        while (isActive) {
            val positionSec = positionProvider() / 1_000f
            activeSegment = segments.firstOrNull { positionSec >= it.startTime && positionSec < it.endTime }
            delay(1_000L)
        }
    }

    activeSegment?.let { segment ->
        val label = segment.category.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        TvButton(
            text = stringResource(R.string.tv_player_skip_segment, label),
            onClick = { onSkipTo((segment.endTime * 1_000L).toLong()) },
            modifier = modifier,
        )
    }
}
