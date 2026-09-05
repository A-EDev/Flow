package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LYRICS_ALIGN_CENTER
import io.github.aedev.flow.data.local.LYRICS_ALIGN_LEFT
import io.github.aedev.flow.data.local.LYRICS_ALIGN_RIGHT
import io.github.aedev.flow.data.lyrics.LyricsCandidate
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.ui.components.FlowMenuGroup
import io.github.aedev.flow.ui.components.FlowMenuItemData
import io.github.aedev.flow.ui.components.FlowMenuSectionHeader
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import java.util.Locale

internal fun lyricsTextAlignFor(pref: String): TextAlign =
    when (pref) {
        LYRICS_ALIGN_LEFT -> TextAlign.Left
        LYRICS_ALIGN_RIGHT -> TextAlign.Right
        else -> TextAlign.Center
    }

internal fun buildLrcExportText(
    syncedLyrics: List<LyricsEntry>,
    plainLyrics: String?,
): String =
    if (syncedLyrics.isNotEmpty()) {
        syncedLyrics.joinToString("\n") { entry ->
            val minutes = entry.time / 60_000
            val seconds = (entry.time % 60_000) / 1_000
            val hundredths = (entry.time % 1_000) / 10
            String.format(Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, hundredths, entry.text)
        }
    } else {
        plainLyrics.orEmpty()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsTrackPill(
    title: String,
    artist: String,
    artworkUrl: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = title to artist,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(initialScale = 0.92f, animationSpec = tween(280)))
                .togetherWith(fadeOut(tween(200)))
        },
        label = "lyricsPillTrack",
        modifier = modifier,
    ) { (pillTitle, pillArtist) ->
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .animateContentSize()
                    .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape),
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = pillTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pillArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (isPlaying) {
                PlayingWaveform(
                    color = MaterialTheme.colorScheme.primary,
                    barCount = 3,
                    barWidth = 2.5.dp,
                    barSpacing = 1.5.dp,
                    staggerMillis = 120,
                )
            }
        }
    }
}

@Composable
internal fun LyricsBottomBar(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backInteraction = remember { MutableInteractionSource() }
        val backPressed by backInteraction.collectIsPressedAsState()
        val backScale by animateFloatAsState(
            targetValue = if (backPressed) 0.85f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "lyricsBackScale",
        )
        FilledTonalIconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = backScale
                        scaleY = backScale
                    },
            shape = CircleShape,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            interactionSource = backInteraction,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val haptics = LocalHapticFeedback.current
        val playCorner by animateDpAsState(
            targetValue = if (isPlaying) 18.dp else 32.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "lyricsPlayCorner",
        )
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(playCorner))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                AnimatedContent(targetState = isPlaying, label = "lyricsPlayIcon") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription =
                            stringResource(if (playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledTonalIconButton(
            onClick = onMenu,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun LyricsSyncOffsetRow(
    offsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val offsetActive = offsetMs != 0L
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // M3E connected button group: 2dp seams, round outer ends, the pressed
        // segment widens with a spring while its inner corners relax — the same
        // press-morph idiom as the player's main transport buttons.
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OffsetStepSegment(deltaMs = -500L, edge = OffsetSegmentEdge.START, onAdjust = onAdjust)
            OffsetStepSegment(deltaMs = -100L, edge = OffsetSegmentEdge.INNER, onAdjust = onAdjust)

            val chipWeight by animateFloatAsState(
                targetValue = if (offsetActive) 1.7f else 1.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "syncChipWeight",
            )
            val chipContainer by animateColorAsState(
                targetValue =
                    if (offsetActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                label = "syncChipContainer",
            )
            val chipContent by animateColorAsState(
                targetValue =
                    if (offsetActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                label = "syncChipContent",
            )
            Box(
                modifier =
                    Modifier
                        .weight(chipWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipContainer)
                        .clickable(enabled = offsetActive) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReset()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = offsetMs,
                        transitionSpec = {
                            // The value rolls like a counter: up when the offset grows.
                            if (targetState > initialState) {
                                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                            } else {
                                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                            }
                        },
                        label = "syncOffsetValue",
                    ) { value ->
                        val valueText = if (value != 0L) String.format(Locale.US, "%+.1f", value / 1000f) else "0"
                        Text(
                            text = stringResource(R.string.lyrics_sync_offset_value, valueText),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = chipContent,
                            maxLines = 1,
                        )
                    }
                    AnimatedVisibility(
                        visible = offsetActive,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.lyrics_sync_reset),
                            modifier = Modifier.size(14.dp),
                            tint = chipContent,
                        )
                    }
                }
            }

            OffsetStepSegment(deltaMs = 100L, edge = OffsetSegmentEdge.INNER, onAdjust = onAdjust)
            OffsetStepSegment(deltaMs = 500L, edge = OffsetSegmentEdge.END, onAdjust = onAdjust)
        }

        IconButton(
            onClick = onHide,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class OffsetSegmentEdge { START, INNER, END }

@Composable
private fun RowScope.OffsetStepSegment(
    deltaMs: Long,
    edge: OffsetSegmentEdge,
    onAdjust: (Long) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val weight by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "syncStepWeight",
    )
    val innerRadius by animateDpAsState(
        targetValue = if (pressed) 18.dp else 10.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "syncStepCorner",
    )
    val shape =
        when (edge) {
            OffsetSegmentEdge.START -> {
                RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = innerRadius, bottomEnd = innerRadius)
            }

            OffsetSegmentEdge.END -> {
                RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = 26.dp, bottomEnd = 26.dp)
            }

            OffsetSegmentEdge.INNER -> {
                RoundedCornerShape(innerRadius)
            }
        }
    Box(
        modifier =
            Modifier
                .weight(weight)
                .fillMaxHeight()
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(interactionSource = interactionSource, indication = ripple()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAdjust(deltaMs)
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String.format(Locale.US, "%+.1f", deltaMs / 1000f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsActionsSheet(
    hasLyrics: Boolean,
    providerName: String,
    alignPref: String,
    syncOffsetMs: Long,
    onRefresh: () -> Unit,
    onChooseSource: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSaveFile: () -> Unit,
    onAlignChange: (String) -> Unit,
    onAdjustSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            val sourceDescription: (@Composable () -> Unit)? =
                providerName.takeIf { it.isNotBlank() }?.let { name ->
                    { Text(stringResource(R.string.lyrics_current_source, name)) }
                }
            FlowMenuGroup(
                items =
                    buildList {
                        add(
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                title = { Text(stringResource(R.string.refresh_lyrics)) },
                                description = sourceDescription,
                                onClick = {
                                    onDismiss()
                                    onRefresh()
                                },
                            ),
                        )
                        add(
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
                                title = { Text(stringResource(R.string.lyrics_choose_source)) },
                                onClick = {
                                    onDismiss()
                                    onChooseSource()
                                },
                            ),
                        )
                        if (hasLyrics) {
                            add(
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    title = { Text(stringResource(R.string.lyrics_edit)) },
                                    onClick = {
                                        onDismiss()
                                        onEdit()
                                    },
                                ),
                            )
                            add(
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                    title = { Text(stringResource(R.string.lyrics_copy)) },
                                    onClick = {
                                        onDismiss()
                                        onCopy()
                                    },
                                ),
                            )
                            add(
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                                    title = { Text(stringResource(R.string.lyrics_save_file)) },
                                    onClick = {
                                        onDismiss()
                                        onSaveFile()
                                    },
                                ),
                            )
                        }
                    },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            FlowMenuSectionHeader(stringResource(R.string.lyrics_display_header))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LyricsAlignToggleButton(
                    checked = alignPref == LYRICS_ALIGN_LEFT,
                    icon = Icons.Outlined.FormatAlignLeft,
                    contentDescription = stringResource(R.string.lyrics_align_left),
                    uncheckedShape =
                        RoundedCornerShape(
                            topStart = 22.dp,
                            bottomStart = 22.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp,
                        ),
                    onClick = { onAlignChange(LYRICS_ALIGN_LEFT) },
                )
                LyricsAlignToggleButton(
                    checked = alignPref == LYRICS_ALIGN_CENTER,
                    icon = Icons.Outlined.FormatAlignCenter,
                    contentDescription = stringResource(R.string.lyrics_align_center),
                    uncheckedShape = RoundedCornerShape(8.dp),
                    onClick = { onAlignChange(LYRICS_ALIGN_CENTER) },
                )
                LyricsAlignToggleButton(
                    checked = alignPref == LYRICS_ALIGN_RIGHT,
                    icon = Icons.Outlined.FormatAlignRight,
                    contentDescription = stringResource(R.string.lyrics_align_right),
                    uncheckedShape =
                        RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 22.dp,
                            bottomEnd = 22.dp,
                        ),
                    onClick = { onAlignChange(LYRICS_ALIGN_RIGHT) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val offsetDescription: (@Composable () -> Unit)? =
                syncOffsetMs.takeIf { it != 0L }?.let { offset ->
                    {
                        Text(
                            stringResource(
                                R.string.lyrics_sync_offset_value,
                                String.format(Locale.US, "%+.1f", offset / 1000f),
                            ),
                        )
                    }
                }
            FlowMenuGroup(
                items =
                    listOf(
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                            title = { Text(stringResource(R.string.lyrics_adjust_sync)) },
                            description = offsetDescription,
                            onClick = {
                                onDismiss()
                                onAdjustSync()
                            },
                        ),
                    ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.LyricsAlignToggleButton(
    checked: Boolean,
    icon: ImageVector,
    contentDescription: String,
    uncheckedShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        colors =
            ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        shapes =
            ToggleButtonShapes(
                shape = uncheckedShape,
                pressedShape = RoundedCornerShape(12.dp),
                checkedShape = RoundedCornerShape(22.dp),
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsSourcesSheet(
    candidates: List<LyricsCandidate>,
    isBrowsing: Boolean,
    currentProviderName: String,
    onSelect: (LyricsCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.lyrics_choose_source),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                candidates.isEmpty() && isBrowsing -> {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(30.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.lyrics_searching_sources),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                candidates.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.lyrics_no_sources_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(candidates, key = { it.providerName }) { candidate ->
                            LyricsCandidateRow(
                                candidate = candidate,
                                isCurrent = candidate.providerName == currentProviderName,
                                onClick = { onSelect(candidate) },
                            )
                        }
                        if (isBrowsing) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsCandidateRow(
    candidate: LyricsCandidate,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color =
            if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = candidate.providerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (candidate.synced) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_synced_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                val preview =
                    remember(candidate) {
                        candidate.entries
                            .asSequence()
                            .map { it.text.trim() }
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString(" · ")
                    }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.lyrics_current_source, candidate.providerName),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
internal fun LyricsEditDialog(
    initialText: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_edit)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
