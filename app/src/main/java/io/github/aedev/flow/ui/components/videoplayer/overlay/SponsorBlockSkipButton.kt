package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import kotlinx.coroutines.delay

private const val SB_SKIP_DIM_DELAY_MS = 5_000L
private const val SB_SKIP_DIMMED_ALPHA = 0.45f

private fun sbCategoryLabelRes(category: String): Int? =
    when (category) {
        "sponsor" -> R.string.sb_category_sponsor
        "selfpromo" -> R.string.sb_category_selfpromo
        "interaction" -> R.string.sb_category_interaction
        "intro" -> R.string.sb_category_intro
        "outro" -> R.string.sb_category_outro
        "music_offtopic" -> R.string.sb_category_music_offtopic
        "filler" -> R.string.sb_category_filler
        "preview" -> R.string.sb_category_preview
        "exclusive_access" -> R.string.sb_category_exclusive_access
        else -> null
    }

/**
 * Overlay button that lets the user manually skip a SponsorBlock segment.
 */
@Composable
fun SponsorBlockSkipButton(
    sponsorSegments: List<SponsorBlockSegment>,
    currentPositionMs: Long,
    categoryActions: Map<String, SponsorBlockAction>,
    controlsVisible: Boolean,
    playbackEnded: Boolean,
    onSkipClick: (endPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var skippedUuids by remember(sponsorSegments) { mutableStateOf(emptySet<String>()) }

    val activeSegment =
        remember(
            sponsorSegments,
            currentPositionMs,
            skippedUuids,
            categoryActions,
            playbackEnded,
        ) {
            findActiveManualSponsorSegment(
                sponsorSegments = sponsorSegments,
                currentPositionMs = currentPositionMs,
                skippedUuids = skippedUuids,
                categoryActions = categoryActions,
                playbackEnded = playbackEnded,
            )
        }

    var displaySegment by remember { mutableStateOf<SponsorBlockSegment?>(null) }
    LaunchedEffect(activeSegment) {
        if (activeSegment != null) displaySegment = activeSegment
    }

    var isDimmed by remember { mutableStateOf(false) }
    LaunchedEffect(activeSegment?.uuid, controlsVisible) {
        if (activeSegment == null || controlsVisible) {
            isDimmed = false
        } else {
            isDimmed = false
            delay(SB_SKIP_DIM_DELAY_MS)
            isDimmed = true
        }
    }

    val buttonAlpha =
        animateFloatAsState(
            targetValue = if (isDimmed) SB_SKIP_DIMMED_ALPHA else 1f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "sbSkipAlpha",
        )

    AnimatedVisibility(
        visible = activeSegment != null,
        enter =
            slideInHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), initialOffsetX = { it }) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit =
            slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), targetOffsetX = { it }) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        val seg = displaySegment ?: return@AnimatedVisibility
        val categoryRes = sbCategoryLabelRes(seg.category)
        val skipLabel =
            if (categoryRes != null) {
                stringResource(R.string.sb_skip_segment, stringResource(categoryRes))
            } else {
                stringResource(R.string.sb_manual_skip)
            }
        Surface(
            onClick = {
                skippedUuids = skippedUuids + seg.uuid
                onSkipClick((seg.endTime * 1000L).toLong())
            },
            color = PlayerScrim.copy(alpha = 0.5f),
            contentColor = PlayerScrimContent,
            shape = RoundedCornerShape(50),
            tonalElevation = 0.dp,
            modifier = Modifier.graphicsLayer { alpha = buttonAlpha.value },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = skipLabel,
                    color = PlayerScrimContent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = PlayerScrimContent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
