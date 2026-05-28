/*
 * Lyrics rendering in this file is adapted from Metrolist's GPL-3.0 lyrics
 * engine and canvas implementation.
 *
 * Upstream project: https://github.com/MetrolistGroup/Metrolist
 * Upstream files: ui/component/ExperimentalLyrics.kt, LyricsLine.kt,
 * LyricsCommon.kt, and ui/utils/FadingEdge.kt.
 */
package io.github.aedev.flow.ui.screens.music.player

import android.graphics.BlurMaskFilter
import android.graphics.Typeface
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.WordTimestamp
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.utils.fadingEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.BreakIterator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LYRICS_ANCHOR_RATIO = 0.35f
private val LYRICS_ITEM_FALLBACK_HEIGHT_DP = 68.dp
private val LYRICS_ITEM_GAP_DP = 16.dp
private val LYRICS_FADE_TOP_DP = 130.dp
private val LYRICS_FADE_BOTTOM_DP = 160.dp
private const val LYRICS_STAGGER_DELAY_PER_DISTANCE = 20
private const val LYRICS_STAGGER_DELAY_MAX_MS = 200
private const val LYRICS_PREVIEW_TIME = 8000L
private const val LYRICS_WORD_CANVAS_WINDOW = 3

private sealed class LyricsListItem {
    data class Line(val index: Int, val entry: LyricsEntry) : LyricsListItem()
    data class Indicator(
        val afterLineIndex: Int,
        val gapStartMs: Long,
        val gapEndMs: Long
    ) : LyricsListItem()
}

private data class HyphenGroupWord(
    val pos: Int,
    val size: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long
)

@Composable
fun InlineLyricsPanel(
    lyrics: String?,
    syncedLyrics: List<LyricsEntry>,
    currentPosition: Long,
    isLoading: Boolean,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    providerName: String = "",
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestCurrentPosition by rememberUpdatedState(currentPosition)
    val expressiveAccent = remember(accentColor) {
        if (accentColor.luminance() < 0.48f) Color(0xFFEDEFC1) else accentColor
    }

    val lines = remember(lyrics, syncedLyrics) {
        buildLines(lyrics = lyrics, syncedLyrics = syncedLyrics)
    }
    val mergedLyricsList = remember(lines) { buildMergedLyricsList(lines) }
    val hasWordTimings = remember(lines) { lines.any { !it.words.isNullOrEmpty() } }
    val isSynced = remember(lines, syncedLyrics) {
        syncedLyrics.isNotEmpty() && entriesLookSynced(syncedLyrics) &&
            lines.any { it.time in 1L..999_999L }
    }

    var activeLineIndices by remember { mutableStateOf(emptySet<Int>()) }
    var scrollTargetIndex by remember { mutableIntStateOf(-1) }
    var previousScrollActiveIndices by remember { mutableStateOf(emptySet<Int>()) }
    var currentPositionState by remember { mutableLongStateOf(currentPosition) }
    var deferredCurrentLineIndex by remember { mutableIntStateOf(0) }
    var lastPreviewTime by remember { mutableLongStateOf(0L) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var lastMainMaxSeen by remember(lines) { mutableIntStateOf(-1) }
    var userManualOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lines) {
        activeLineIndices = emptySet()
        previousScrollActiveIndices = emptySet()
        scrollTargetIndex = -1
        deferredCurrentLineIndex = 0
        isAutoScrollEnabled = true
        userManualOffset = 0f
        lastMainMaxSeen = -1
    }

    LaunchedEffect(lines) {
        if (lines.isEmpty()) return@LaunchedEffect

        var lastPlayerPos = EnhancedMusicPlayerManager.getCurrentPosition().takeIf { it > 0 } ?: latestCurrentPosition
        var lastUpdateTime = System.currentTimeMillis()
        var previousPosition = lastPlayerPos

        while (isActive) {
            delay(32)
            val now = System.currentTimeMillis()
            val managerPosition = EnhancedMusicPlayerManager.getCurrentPosition()
            if (managerPosition != lastPlayerPos) {
                lastPlayerPos = managerPosition
                lastUpdateTime = now
            }
            val elapsed = now - lastUpdateTime
            val position = lastPlayerPos + if (EnhancedMusicPlayerManager.isPlaying()) elapsed else 0L

            if (previousPosition - position > 2000L && isAutoScrollEnabled) {
                val seekTarget = findActiveLineIndices(lines, position)
                    .filter { lines.getOrNull(it)?.isBackground == false }
                    .maxOrNull() ?: findActiveLineIndices(lines, position).maxOrNull() ?: 0
                scrollTargetIndex = seekTarget
                lastMainMaxSeen = seekTarget
                deferredCurrentLineIndex = seekTarget
            }
            previousPosition = position

            currentPositionState = position

            val initialActiveIndices = findActiveLineIndices(lines, position)
            val scrollActiveRaw = findActiveLineIndices(lines, position + if (hasWordTimings) 0L else 250L)
            val scrollActiveIndices = scrollActiveRaw.toMutableSet()

            for (i in scrollActiveRaw) {
                if (lines.getOrNull(i)?.isBackground == true) {
                    for (j in i - 1 downTo 0) {
                        if (lines.getOrNull(j)?.isBackground == false) {
                            scrollActiveIndices.add(j)
                            break
                        }
                    }
                }
            }

            val newActiveIndices = initialActiveIndices.toMutableSet()
            for (i in initialActiveIndices) {
                if (lines.getOrNull(i)?.isBackground == true) {
                    for (j in i - 1 downTo 0) {
                        if (lines.getOrNull(j)?.isBackground == false) {
                            newActiveIndices.add(j)
                            break
                        }
                    }
                }
            }

            val scrollMax = scrollActiveIndices
                .filter { lines.getOrNull(it)?.isBackground == false }
                .maxOrNull() ?: (scrollActiveIndices.maxOrNull() ?: -1)

            val shouldScroll = when {
                scrollTargetIndex !in scrollActiveIndices && scrollActiveIndices.isNotEmpty() && scrollMax > scrollTargetIndex -> true
                scrollTargetIndex !in scrollActiveIndices && scrollActiveIndices.isEmpty() && previousScrollActiveIndices.isNotEmpty() -> true
                scrollTargetIndex == -1 && scrollActiveIndices.isNotEmpty() -> true
                previousScrollActiveIndices.isEmpty() && scrollActiveIndices.isNotEmpty() && scrollMax > scrollTargetIndex -> true
                else -> false
            }

            if (shouldScroll) {
                val targetToScroll = when {
                    scrollTargetIndex !in scrollActiveIndices && scrollActiveIndices.isNotEmpty() -> scrollMax
                    scrollTargetIndex !in scrollActiveIndices && scrollActiveIndices.isEmpty() -> {
                        (lastMainMaxSeen + 1 until lines.size).firstOrNull {
                            lines.getOrNull(it)?.isBackground == false
                        } ?: scrollTargetIndex
                    }
                    else -> scrollMax
                }
                if (targetToScroll != -1 && targetToScroll > scrollTargetIndex) {
                    scrollTargetIndex = targetToScroll
                }
            }

            if (scrollMax > lastMainMaxSeen && scrollMax != -1) lastMainMaxSeen = scrollMax
            previousScrollActiveIndices = scrollActiveIndices
            activeLineIndices = newActiveIndices
        }
    }

    LaunchedEffect(scrollTargetIndex, isAutoScrollEnabled) {
        if (scrollTargetIndex != -1 && isAutoScrollEnabled) {
            deferredCurrentLineIndex = scrollTargetIndex
        }
    }

    LaunchedEffect(isAutoScrollEnabled, lastPreviewTime) {
        if (!isAutoScrollEnabled && lastPreviewTime != 0L) {
            delay(LYRICS_PREVIEW_TIME)
            lastPreviewTime = 0L
            isAutoScrollEnabled = true
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = expressiveAccent,
                modifier = Modifier.align(Alignment.Center)
            )
            return@BoxWithConstraints
        }

        if (lines.isEmpty()) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.lyrics_not_available),
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            return@BoxWithConstraints
        }

        val maxHeightPx = constraints.maxHeight.toFloat()
        val anchorY = maxHeightPx * LYRICS_ANCHOR_RATIO
        val lineHeightPx = with(density) { LYRICS_ITEM_FALLBACK_HEIGHT_DP.toPx() }
        val indicatorHeightPx = with(density) { 72.dp.toPx() }
        val constraintLineHeightPx = with(density) { 120.dp.toPx() }
        val itemHeights = remember(lines, mergedLyricsList) { mutableStateMapOf<Int, Int>() }
        var isInitialLayout by remember(lines, mergedLyricsList) { mutableStateOf(true) }
        var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val velocityTracker = remember { VelocityTracker() }
        val decayAnimSpec = remember { exponentialDecay<Float>(frictionMultiplier = 1.8f) }
        val activeListIndex by remember(mergedLyricsList, deferredCurrentLineIndex) {
            derivedStateOf {
                mergedLyricsList.indexOfFirst {
                    (it is LyricsListItem.Line && it.index == deferredCurrentLineIndex) ||
                        (it is LyricsListItem.Indicator && it.afterLineIndex == deferredCurrentLineIndex)
                }.coerceAtLeast(0)
            }
        }

        val positions = remember(itemHeights.toMap(), activeListIndex, mergedLyricsList) {
            val map = mutableMapOf<Int, Float>()
            if (activeListIndex == -1 || mergedLyricsList.isEmpty()) return@remember map

            map[activeListIndex] = 0f
            var currentY = 0f
            for (i in activeListIndex - 1 downTo 0) {
                val item = mergedLyricsList[i]
                val height = itemHeights[i]?.toFloat() ?: if (item is LyricsListItem.Indicator) indicatorHeightPx else lineHeightPx
                val noGap = (item as? LyricsListItem.Line)?.entry?.isBackground == true || item is LyricsListItem.Indicator
                currentY -= height + if (noGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() }
                map[i] = currentY
            }
            currentY = 0f
            for (i in activeListIndex until mergedLyricsList.size - 1) {
                val currentItem = mergedLyricsList[i]
                val nextItem = mergedLyricsList[i + 1]
                val height = itemHeights[i]?.toFloat() ?: if (currentItem is LyricsListItem.Indicator) indicatorHeightPx else lineHeightPx
                val nextNoGap = (nextItem as? LyricsListItem.Line)?.entry?.isBackground == true || nextItem is LyricsListItem.Indicator
                currentY += height + if (nextNoGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() }
                map[i + 1] = currentY
            }
            map
        }

        val minOffset = remember(itemHeights.toMap(), mergedLyricsList, activeListIndex, anchorY) {
            if (mergedLyricsList.isEmpty() || activeListIndex == -1) return@remember 0f
            val totalBelow = (activeListIndex until mergedLyricsList.size - 1).sumOf { i ->
                val currentItem = mergedLyricsList[i]
                val nextItem = mergedLyricsList[i + 1]
                val height = itemHeights[i]?.toFloat() ?: if (currentItem is LyricsListItem.Indicator) indicatorHeightPx else constraintLineHeightPx
                val nextNoGap = (nextItem as? LyricsListItem.Line)?.entry?.isBackground == true || nextItem is LyricsListItem.Indicator
                (height + if (nextNoGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() }).toDouble()
            }.toFloat()
            val lastItem = mergedLyricsList.last()
            val lastHeight = itemHeights[mergedLyricsList.size - 1]?.toFloat()
                ?: if (lastItem is LyricsListItem.Indicator) indicatorHeightPx else constraintLineHeightPx
            with(density) { 100.dp.toPx() } - anchorY - totalBelow - lastHeight
        }

        val maxOffset = remember(itemHeights.toMap(), mergedLyricsList, activeListIndex, maxHeightPx, anchorY) {
            if (mergedLyricsList.isEmpty() || activeListIndex == -1) return@remember 0f
            val totalAbove = (0 until activeListIndex).sumOf { i ->
                val item = mergedLyricsList[i]
                val height = itemHeights[i]?.toFloat() ?: if (item is LyricsListItem.Indicator) indicatorHeightPx else constraintLineHeightPx
                val noGap = (item as? LyricsListItem.Line)?.entry?.isBackground == true || item is LyricsListItem.Indicator
                (height + if (noGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() }).toDouble()
            }.toFloat()
            maxHeightPx - with(density) { 150.dp.toPx() } - anchorY + totalAbove
        }

        val scrollClampMin = minOf(minOffset, maxOffset)
        val scrollClampMax = maxOf(minOffset, maxOffset)

        val resyncToCurrentLine = {
            flingJob?.cancel()
            val target = findActiveLineIndices(lines, currentPositionState)
                .filter { lines.getOrNull(it)?.isBackground == false }
                .maxOrNull()
                ?: findActiveLineIndices(lines, currentPositionState).maxOrNull()
                ?: scrollTargetIndex
            if (target != -1) {
                val listIndex = mergedLyricsList.indexOfFirst {
                    it is LyricsListItem.Line && it.index == target
                }.coerceAtLeast(0)
                userManualOffset += positions[listIndex] ?: 0f
                deferredCurrentLineIndex = target
                scrollTargetIndex = target
                lastMainMaxSeen = target
            }
            isAutoScrollEnabled = true
            lastPreviewTime = 0L
        }

        LaunchedEffect(scrollClampMin, scrollClampMax) {
            userManualOffset = userManualOffset.coerceIn(scrollClampMin, scrollClampMax)
        }

        LaunchedEffect(isAutoScrollEnabled, lines) {
            if (isAutoScrollEnabled) {
                val start = userManualOffset
                if (abs(start) < 1f) {
                    userManualOffset = 0f
                    return@LaunchedEffect
                }
                val anim = Animatable(start)
                var lastValue = start
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween((abs(start) / 4f).toInt().coerceIn(200, 600), easing = FastOutSlowInEasing)
                ) {
                    userManualOffset += value - lastValue
                    lastValue = value
                }
                userManualOffset = 0f
            }
        }

        LaunchedEffect(lines, mergedLyricsList.size, activeListIndex) {
            if (mergedLyricsList.isNotEmpty()) {
                isInitialLayout = true
                snapshotFlow {
                    val h = itemHeights.toMap()
                    val windowStart = (activeListIndex - 8).coerceAtLeast(0)
                    val windowEnd = (activeListIndex + 12).coerceAtMost(mergedLyricsList.size - 1)
                    (windowStart..windowEnd).all { h.containsKey(it) }
                }
                    .first { it }
                isInitialLayout = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(top = LYRICS_FADE_TOP_DP, bottom = LYRICS_FADE_BOTTOM_DP)
                .clipToBounds()
                .pointerInput(scrollClampMin, scrollClampMax, isInitialLayout) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (isInitialLayout) continue
                            flingJob?.cancel()
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var totalDrag = 0f
                            var isDragging = false

                            verticalDrag(down.id) { change ->
                                val delta = change.positionChange().y
                                totalDrag += abs(delta)

                                if (!isDragging && totalDrag > touchSlop) {
                                    isDragging = true
                                }

                                if (isDragging) {
                                    val next = (userManualOffset + delta).coerceIn(scrollClampMin, scrollClampMax)
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    if (next != userManualOffset) {
                                        userManualOffset = next
                                        isAutoScrollEnabled = false
                                        lastPreviewTime = System.currentTimeMillis()
                                        change.consume()
                                    }
                                }
                            }

                            if (isDragging) {
                                val velocity = velocityTracker.calculateVelocity().y
                                flingJob = scope.launch {
                                    AnimationState(initialValue = userManualOffset, initialVelocity = velocity)
                                        .animateDecay(decayAnimSpec) {
                                            val clamped = value.coerceIn(scrollClampMin, scrollClampMax)
                                            userManualOffset = clamped
                                            if (value != clamped) cancelAnimation()
                                        }
                                }
                            }
                        }
                    }
                }
        ) {
            val renderPaddingPx = with(density) { 420.dp.toPx() }
            mergedLyricsList.forEachIndexed { listIndex, listItem ->
                val estimatedOffset = anchorY + positions.getOrDefault(
                    listIndex,
                    (listIndex - activeListIndex) * lineHeightPx
                ) + userManualOffset
                val shouldRender =
                    estimatedOffset > -renderPaddingPx &&
                        estimatedOffset < maxHeightPx + renderPaddingPx ||
                        abs(listIndex - activeListIndex) <= 14
                if (!shouldRender) return@forEachIndexed

                key(listItem) {
                    val distance = abs(listIndex - activeListIndex)
                    val targetOffset = anchorY + positions.getOrDefault(listIndex, (listIndex - activeListIndex) * lineHeightPx)
                    val frozenOffset = remember { mutableFloatStateOf(targetOffset) }
                    LaunchedEffect(isAutoScrollEnabled, targetOffset, isInitialLayout) {
                        if (isAutoScrollEnabled || isInitialLayout) frozenOffset.floatValue = targetOffset
                    }
                    val animatedOffset by animateFloatAsState(
                        targetValue = if (isAutoScrollEnabled) targetOffset else frozenOffset.floatValue,
                        animationSpec = if (isInitialLayout || !isAutoScrollEnabled) {
                            snap()
                        } else {
                            tween(
                                durationMillis = 750,
                                delayMillis = (distance * LYRICS_STAGGER_DELAY_PER_DISTANCE).coerceAtMost(LYRICS_STAGGER_DELAY_MAX_MS),
                                easing = FastOutSlowInEasing
                            )
                        },
                        label = "lyricStaggeredOffset_$listIndex"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                                layout(placeable.width, 0) { placeable.place(0, 0) }
                            }
                            .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) }
                    ) {
                        when (listItem) {
                            is LyricsListItem.Indicator -> {
                                IntervalIndicator(
                                    gapStartMs = listItem.gapStartMs,
                                    gapEndMs = listItem.gapEndMs - 650L,
                                    currentPositionMs = currentPositionState,
                                    visible = isAutoScrollEnabled &&
                                        currentPositionState >= listItem.gapStartMs &&
                                        currentPositionState <= listItem.gapEndMs - 650L,
                                    color = expressiveAccent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { itemHeights[listIndex] = it.height }
                                        .padding(horizontal = 24.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }

                            is LyricsListItem.Line -> {
                                val index = listItem.index
                                val item = listItem.entry
                                val isActiveLine = activeLineIndices.contains(index)
                                val pairedMainLineIndex = if (item.isBackground) {
                                    (index - 1 downTo 0).firstOrNull { lines.getOrNull(it)?.isBackground == false } ?: -1
                                } else {
                                    -1
                                }
                                val isInGapWithMain = if (item.isBackground && pairedMainLineIndex != -1) {
                                    val pairedMainLine = lines[pairedMainLineIndex]
                                    currentPositionState >= pairedMainLine.time && currentPositionState <= item.time
                                } else {
                                    false
                                }
                                val bgVisible = item.isBackground &&
                                    (activeLineIndices.contains(pairedMainLineIndex) ||
                                        activeLineIndices.contains(index) ||
                                        isInGapWithMain)

                                LyricsLine(
                                    index = index,
                                    item = item,
                                    isSynced = isSynced,
                                    isActiveLine = isActiveLine,
                                    bgVisible = bgVisible,
                                    currentPositionState = currentPositionState,
                                    lyricsTextSize = 36f,
                                    lyricsLineSpacing = 1.3f,
                                    expressiveAccent = expressiveAccent,
                                    isAutoScrollEnabled = isAutoScrollEnabled,
                                    displayedCurrentLineIndex = deferredCurrentLineIndex,
                                    onSizeChanged = { itemHeights[listIndex] = it },
                                    onClick = {
                                        if (isSynced) {
                                            val seekTarget = item.time.coerceAtLeast(0L)
                                            val duration = EnhancedMusicPlayerManager.getDuration()
                                            if (duration <= 0L || seekTarget < duration + 30_000L) {
                                                onSeekTo(seekTarget)
                                            }
                                            scrollTargetIndex = index
                                            deferredCurrentLineIndex = index
                                            lastMainMaxSeen = index
                                            isAutoScrollEnabled = true
                                            lastPreviewTime = 0L
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isAutoScrollEnabled && isSynced,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.42f),
                contentColor = expressiveAccent
            ) {
                IconButton(onClick = resyncToCurrentLine) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = "Sync lyrics"
                    )
                }
            }
        }

        if (providerName.isNotBlank()) {
            var showProviderName by remember(providerName) { mutableStateOf(true) }
            val providerAlpha by animateFloatAsState(
                targetValue = if (showProviderName) 1f else 0f,
                animationSpec = tween(durationMillis = 600),
                label = "providerNameAlpha"
            )

            LaunchedEffect(providerName) {
                showProviderName = true
                delay(3000)
                showProviderName = false
            }

            if (providerAlpha > 0f) {
                Text(
                    text = providerName,
                    color = expressiveAccent.copy(alpha = 0.6f * providerAlpha),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 38.dp)
                )
            }
        }
    }
}

@Composable
private fun IntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0f) }
    val rowHeight = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            rowHeight.animateTo(1f, tween(200))
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(0f, tween(200))
            rowHeight.animateTo(0f, tween(200))
        }
    }

    val progress = if (gapEndMs > gapStartMs) {
        ((currentPositionMs - gapStartMs).toFloat() / (gapEndMs - gapStartMs).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(progress, tween(100), label = "intervalProgress")

    Box(
        modifier = modifier
            .height(72.dp * rowHeight.value)
            .padding(top = 16.dp * rowHeight.value)
            .graphicsLayer {
                this.alpha = alpha.value
                this.clip = true
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "I N S T R U M E N T A L",
                color = color.copy(alpha = 0.8f * (1f - animatedProgress)),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            )
            Canvas(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.5f)
                    .height(3.dp)
            ) {
                val center = size.width / 2f
                val halfRemaining = center * (1f - animatedProgress)
                drawLine(
                    color = color.copy(alpha = 0.15f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(center - halfRemaining, size.height / 2f),
                    end = Offset(center + halfRemaining, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f * (1f - animatedProgress)),
                    radius = size.height * 1.5f,
                    center = Offset(center - halfRemaining, size.height / 2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f * (1f - animatedProgress)),
                    radius = size.height * 1.5f,
                    center = Offset(center + halfRemaining, size.height / 2f)
                )
            }
        }
    }
}

private fun adaptiveLyricsTextSize(baseSize: Float, textLength: Int, isBackground: Boolean): Float {
    val foregroundSize = when {
        textLength > 92 -> baseSize * 0.66f
        textLength > 72 -> baseSize * 0.72f
        textLength > 54 -> baseSize * 0.8f
        textLength > 42 -> baseSize * 0.9f
        else -> baseSize
    }
    return if (isBackground) foregroundSize * 0.7f else foregroundSize
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricsLine(
    index: Int,
    item: LyricsEntry,
    isSynced: Boolean,
    isActiveLine: Boolean,
    bgVisible: Boolean,
    currentPositionState: Long,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    expressiveAccent: Color,
    isAutoScrollEnabled: Boolean,
    displayedCurrentLineIndex: Int,
    onSizeChanged: (Int) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val lineDistance = abs(index - displayedCurrentLineIndex)
    val dofBlurRadius by animateFloatAsState(
        targetValue = if (!isSynced || isActiveLine || item.isBackground) {
            0f
        } else {
            with(density) { (lineDistance * 4.dp.toPx()).coerceAtMost(16.dp.toPx()) }
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricsDofBlur"
    )
    val depthScale by animateFloatAsState(
        targetValue = if (isActiveLine) 1.05f else 0.92f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricsDepthScale"
    )

    val itemModifier = modifier
        .fillMaxWidth()
        .onSizeChanged { onSizeChanged(it.height) }
        .graphicsLayer {
            scaleX = depthScale
            scaleY = depthScale
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dofBlurRadius > 0.5f) {
                renderEffect = RenderEffect
                    .createBlurEffect(
                        dofBlurRadius,
                        dofBlurRadius,
                        android.graphics.Shader.TileMode.DECAL
                    )
                    .asComposeRenderEffect()
            } else {
                renderEffect = null
            }
        }
        .clip(RoundedCornerShape(8.dp))
        .combinedClickable(onClick = onClick)
        .background(Color.Transparent)
        .padding(
            start = 24.dp,
            end = 24.dp,
            top = if (item.isBackground) 0.dp else 12.dp,
            bottom = if (item.isBackground) 2.dp else 12.dp
        )

    Box(modifier = itemModifier, contentAlignment = Alignment.Center) {
        @Composable
        fun LyricContent() {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val inactiveAlpha = if (item.isBackground) 0.2f else 0.45f
                val activeAlpha = 1f
                val focusedAlpha = if (item.isBackground) 0.6f else 0.45f
                val targetAlpha = if (!isSynced || item.isBackground || isActiveLine) {
                    activeAlpha
                } else if (isAutoScrollEnabled && displayedCurrentLineIndex >= 0) {
                    when (abs(index - displayedCurrentLineIndex)) {
                        0 -> focusedAlpha
                        1 -> 0.4f
                        2 -> 0.35f
                        3 -> 0.3f
                        else -> inactiveAlpha
                    }
                } else {
                    inactiveAlpha
                }

                val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lyricsLineAlpha")
                val lineColor = expressiveAccent.copy(alpha = if (item.isBackground) focusedAlpha else animatedAlpha)
                val mainText = if (item.isBackground) {
                    item.text.removePrefix("(").removeSuffix(")")
                } else {
                    item.text
                }
                val translation = item.translation?.takeIf { it.isNotBlank() }
                val adaptiveTextSize = remember(mainText, lyricsTextSize, item.isBackground) {
                    adaptiveLyricsTextSize(lyricsTextSize, mainText.length, item.isBackground)
                }

                val lyricStyle = TextStyle(
                    fontSize = adaptiveTextSize.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (item.isBackground) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = if (item.isBackground) {
                        (adaptiveTextSize * lyricsLineSpacing).sp
                    } else {
                        (adaptiveTextSize * lyricsLineSpacing).sp
                    },
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )

                val effectiveWords = if (item.words?.isNotEmpty() == true) {
                    item.words
                } else {
                    remember(mainText, item.time) {
                        val words = mainText.split(Regex("\\s+")).filter { it.isNotBlank() }
                        val wordDurationMs = 180L
                        val wordStaggerMs = 30L
                        words.mapIndexed { idx, wordText ->
                            WordTimestamp(
                                text = wordText,
                                startTime = item.time + (idx * wordStaggerMs),
                                endTime = item.time + (idx * wordStaggerMs) + wordDurationMs
                            )
                        }
                    }
                }
                val activeTextStyle = lyricStyle.copy(color = if (isActiveLine) expressiveAccent else lineColor)

                if (isSynced && effectiveWords.isNotEmpty() && (isActiveLine || lineDistance <= LYRICS_WORD_CANVAS_WINDOW)) {
                    WordLevelLyrics(
                        mainText = mainText,
                        words = effectiveWords,
                        isActiveLine = isActiveLine,
                        currentPositionState = currentPositionState,
                        lyricStyle = lyricStyle,
                        lineColor = lineColor,
                        expressiveAccent = expressiveAccent,
                        isBackground = item.isBackground,
                        focusedAlpha = focusedAlpha,
                        alignment = TextAlign.Center
                    )
                } else {
                    Text(
                        text = mainText,
                        style = activeTextStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (translation != null) {
                    Text(
                        text = translation,
                        color = if (isActiveLine) {
                            expressiveAccent.copy(alpha = 0.72f)
                        } else {
                            lineColor.copy(alpha = (lineColor.alpha * 0.78f).coerceIn(0.08f, 0.6f))
                        },
                        style = lyricStyle.copy(
                            fontSize = (adaptiveTextSize * 0.52f).coerceAtLeast(15f).sp,
                            lineHeight = (adaptiveTextSize * 0.68f).coerceAtLeast(19f).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Normal,
                            letterSpacing = 0.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                    )
                }
            }
        }

        if (item.isBackground) {
            AnimatedVisibility(
                visible = bgVisible,
                enter = fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                exit = fadeOut(tween(250))
            ) {
                LyricContent()
            }
        } else {
            LyricContent()
        }
    }
}

@Composable
private fun WordLevelLyrics(
    mainText: String,
    words: List<WordTimestamp>,
    isActiveLine: Boolean,
    currentPositionState: Long,
    lyricStyle: TextStyle,
    lineColor: Color,
    expressiveAccent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val glowPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val liquidPaint = remember(expressiveAccent) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    var smoothPosition by remember { mutableLongStateOf(currentPositionState) }

    LaunchedEffect(isActiveLine) {
        if (isActiveLine) {
            var lastPlayerPos = EnhancedMusicPlayerManager.getCurrentPosition()
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    val playerPos = EnhancedMusicPlayerManager.getCurrentPosition()
                    if (playerPos != lastPlayerPos) {
                        lastPlayerPos = playerPos
                        lastUpdateTime = now
                    }
                    val elapsed = now - lastUpdateTime
                    smoothPosition = lastPlayerPos + if (EnhancedMusicPlayerManager.isPlaying()) elapsed else 0L
                }
            }
        }
    }

    LaunchedEffect(isActiveLine, currentPositionState) {
        if (!isActiveLine) smoothPosition = currentPositionState
    }

    val sanitizedInputWords = remember(words) { sanitizeWordTimestamps(words) }
    val (effectiveWords, effectiveToOriginalIdx) = remember(sanitizedInputWords, isBackground) {
        sanitizedInputWords.flatMapIndexed { originalIdx, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 && originalIdx == sanitizedInputWords.lastIndex
            if (shouldSplit) {
                val segments = mutableListOf<String>()
                var start = 0
                for (i in word.text.indices) {
                    if (word.text[i] == '-') {
                        segments.add(word.text.substring(start, i + 1))
                        start = i + 1
                    }
                }
                if (start < word.text.length) segments.add(word.text.substring(start))
                if (segments.size > 1) {
                    val totalDuration = word.endTime - word.startTime
                    val segmentDuration = totalDuration / segments.size
                    segments.mapIndexed { index, segmentText ->
                        WordTimestamp(
                            text = segmentText,
                            startTime = word.startTime + index * segmentDuration,
                            endTime = word.startTime + (index + 1) * segmentDuration
                        ) to originalIdx
                    }
                } else {
                    listOf(word to originalIdx)
                }
            } else {
                listOf(word to originalIdx)
            }
        }.let { data -> data.map { it.first } to data.map { it.second } }
    }

    val graphemeClusters = remember(mainText) { mainText.toGraphemeClusters() }
    val clusterCount = graphemeClusters.size
    val clusterCharOffsets = remember(mainText) {
        IntArray(clusterCount).also { offsets ->
            var charOffset = 0
            graphemeClusters.forEachIndexed { i, cluster ->
                offsets[i] = charOffset
                charOffset += cluster.length
            }
        }
    }

    val charToWordData = remember(mainText, effectiveWords, isBackground, graphemeClusters, clusterCharOffsets) {
        val wordIdxMap = IntArray(clusterCount) { -1 }
        val charInWordMap = IntArray(clusterCount)
        val wordLenMap = IntArray(clusterCount) { 1 }
        var currentPos = 0
        var clusterCursor = 0
        effectiveWords.forEachIndexed { wordIdx, word ->
            val rawWordText = if (isBackground) {
                var text = word.text
                if (wordIdx == 0) text = text.removePrefix("(")
                if (wordIdx == effectiveWords.size - 1) text = text.removeSuffix(")")
                text
            } else {
                word.text
            }
            val indexInMain = mainText.indexOf(rawWordText, currentPos)
            if (indexInMain != -1) {
                val wordEndInMain = indexInMain + rawWordText.length
                while (clusterCursor < clusterCount && clusterCharOffsets[clusterCursor] < indexInMain) clusterCursor++
                val wordClusterIndices = mutableListOf<Int>()
                while (clusterCursor < clusterCount && clusterCharOffsets[clusterCursor] < wordEndInMain) {
                    wordClusterIndices.add(clusterCursor)
                    clusterCursor++
                }
                val wordClusterLen = wordClusterIndices.size
                wordClusterIndices.forEachIndexed { posInWord, clusterIndex ->
                    wordIdxMap[clusterIndex] = wordIdx
                    charInWordMap[clusterIndex] = posInWord
                    wordLenMap[clusterIndex] = wordClusterLen
                }
                if (
                    clusterCursor < clusterCount &&
                    clusterCharOffsets[clusterCursor] == wordEndInMain &&
                    wordEndInMain < mainText.length &&
                    mainText[wordEndInMain] == ' '
                ) {
                    wordIdxMap[clusterCursor] = wordIdx
                    charInWordMap[clusterCursor] = wordClusterLen
                    wordLenMap[clusterCursor] = wordClusterLen + 1
                    clusterCursor++
                }
                currentPos = wordEndInMain
            }
        }
        Triple(wordIdxMap, charInWordMap, wordLenMap)
    }

    val hyphenGroupData = remember(effectiveWords) {
        val map = mutableMapOf<Int, HyphenGroupWord>()
        var currentGroup = mutableListOf<Int>()
        effectiveWords.forEachIndexed { wordIdx, word ->
            currentGroup.add(wordIdx)
            if (!word.text.endsWith("-")) {
                if (currentGroup.size > 1) {
                    val groupSize = currentGroup.size
                    val groupStartMs = effectiveWords[currentGroup.first()].startTime
                    val groupEndMs = word.endTime
                    currentGroup.forEachIndexed { pos, idx ->
                        map[idx] = HyphenGroupWord(pos, groupSize, pos == groupSize - 1, groupStartMs, groupEndMs)
                    }
                }
                currentGroup = mutableListOf()
            }
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true
            )
        }
        val letterLayouts = remember(mainText, lyricStyle) {
            graphemeClusters.map { cluster -> textMeasurer.measure(cluster, lyricStyle) }
        }
        val isRtlText = remember(mainText) { mainText.containsRtl() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { layoutResult.size.height.toDp() })
                .graphicsLayer(
                    clip = false,
                    compositingStrategy = CompositingStrategy.Offscreen
                )
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = lineColor)
                return@Canvas
            }

            val currentMillis = System.currentTimeMillis()
            val shimmerOffset = (currentMillis % 3000L) / 3000f
            val shaderX = layoutResult.size.width * shimmerOffset
            liquidPaint.shader = android.graphics.LinearGradient(
                shaderX - layoutResult.size.width / 2f,
                0f,
                shaderX + layoutResult.size.width / 2f,
                layoutResult.size.height.toFloat(),
                intArrayOf(
                    expressiveAccent.toArgb(),
                    Color.White.copy(alpha = 0.8f).toArgb(),
                    expressiveAccent.toArgb()
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            liquidPaint.textSize = lyricStyle.fontSize.toPx()

            if (isRtlText) {
                val (wordIdxMap, _, _) = charToWordData
                val wordFactors = effectiveWords.map { word ->
                    val isWordSung = smoothPosition > word.endTime
                    val isWordActive = smoothPosition in word.startTime..word.endTime
                    val sungFactor = if (isWordSung) {
                        1f
                    } else if (isWordActive) {
                        ((smoothPosition - word.startTime).toFloat() / (word.endTime - word.startTime).coerceAtLeast(1)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Triple(sungFactor, isWordSung, isWordActive)
                }

                drawText(layoutResult, color = lineColor.copy(alpha = focusedAlpha))
                effectiveWords.indices.forEach { wordIndex ->
                    val (sungFactor, isWordSung, isWordActive) = wordFactors[wordIndex]
                    var left = Float.MAX_VALUE
                    var right = Float.MIN_VALUE
                    var top = Float.MAX_VALUE
                    var bottom = Float.MIN_VALUE
                    var found = false
                    for (i in 0 until clusterCount) {
                        if (wordIdxMap[i] == wordIndex) {
                            val bounds = layoutResult.getBoundingBox(clusterCharOffsets[i])
                            left = minOf(left, bounds.left)
                            right = maxOf(right, bounds.right)
                            top = minOf(top, bounds.top)
                            bottom = maxOf(bottom, bounds.bottom)
                            found = true
                        }
                    }
                    if (found) {
                        if (isWordSung) {
                            clipRect(left = left, top = top, right = right, bottom = bottom) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        layoutResult.layoutInput.text.text,
                                        0f,
                                        layoutResult.firstBaseline,
                                        liquidPaint
                                    )
                                }
                            }
                        } else if (isWordActive && sungFactor > 0f) {
                            val fillLeft = right - ((right - left) * sungFactor)
                            clipRect(left = fillLeft, top = top, right = right, bottom = bottom) {
                                drawText(
                                    layoutResult,
                                    color = expressiveAccent.copy(alpha = focusedAlpha + (1f - focusedAlpha) * sungFactor)
                                )
                            }
                        }
                    }
                }
                return@Canvas
            }

            val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
            val wordFactors = effectiveWords.map { word ->
                val isWordSung = smoothPosition > word.endTime
                val isWordActive = smoothPosition in word.startTime..word.endTime
                val sungFactor = if (isWordSung) {
                    1f
                } else if (isWordActive) {
                    ((smoothPosition - word.startTime).toFloat() / (word.endTime - word.startTime).coerceAtLeast(1)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Triple(sungFactor, word, isWordSung)
            }

            val wordWobbles = FloatArray(sanitizedInputWords.size)
            sanitizedInputWords.forEachIndexed { wordIdx, word ->
                val timeSinceStart = (smoothPosition - word.startTime).toFloat()
                val anticipation = ((50f + timeSinceStart) / 50f).coerceIn(0f, 1f)
                val inhaleDip = if (timeSinceStart in -50f..0f) -0.38f * sin(anticipation * PI.toFloat()) else 0f
                val impact = if (timeSinceStart in 0f..750f) {
                    if (timeSinceStart < 125f) timeSinceStart / 125f else (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                } else {
                    0f
                }
                wordWobbles[wordIdx] = inhaleDip + impact
            }

            val lineCurrentPushes = FloatArray(layoutResult.lineCount)
            val lineTotalPushes = FloatArray(layoutResult.lineCount)

            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                var crescendoDeltaX = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p = sungFactor
                    val timeSinceEnd = (smoothPosition - groupWord.groupEndMs).toFloat()
                    val pOut = (timeSinceEnd / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val baseScalePerSegment = 0.012f
                    crescendoDeltaX = if (pOut > 0f) {
                        val baseAtEnd = groupWord.pos * baseScalePerSegment
                        val totalAtEnd = baseAtEnd + peakScale
                        totalAtEnd * exp(-2.5f * pOut) * cos(10f * pOut * PI.toFloat()) * (1f - pOut)
                    } else if (groupWord.isLast) {
                        val base = groupWord.pos * baseScalePerSegment
                        base + peakScale * (1f - exp(-2.5f * p) * cos(10f * p * PI.toFloat()) * (1f - p))
                    } else {
                        (groupWord.pos * baseScalePerSegment) + if (p > 0f) 0.02f * (1f - p) else 0f
                    }
                }
                val charLp = if (wordItem != null) {
                    val dur = (wordItem.endTime - wordItem.startTime).coerceAtLeast(100L).toDouble()
                    val wordProgress = (smoothPosition.toDouble() - wordItem.startTime) / dur
                    val cInW = charInWordMap[i].toDouble()
                    val wLen = wordLenMap[i].toDouble()
                    ((wordProgress - cInW / wLen) * wLen).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) {
                    0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp)
                } else {
                    0f
                }
                val charScaleX = 1f + (wobble * 0.025f) + crescendoDeltaX + (nudgeScale * 0.3f)
                val charBounds = layoutResult.getBoundingBox(charOffset)
                lineTotalPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }

            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val charBounds = layoutResult.getBoundingBox(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                val alignShift = when (alignment) {
                    TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                    TextAlign.Right -> -lineTotalPushes[lineIdx]
                    else -> 0f
                }
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                val charLp = if (wordItem != null) {
                    val dur = (wordItem.endTime - wordItem.startTime).coerceAtLeast(100L).toDouble()
                    val wordProgress = (smoothPosition.toDouble() - wordItem.startTime) / dur
                    val cInW = charInWordMap[i].toDouble()
                    val wLen = wordLenMap[i].toDouble()
                    ((wordProgress - cInW / wLen) * wLen).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }

                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                var crescendoDeltaX = 0f
                var crescendoDeltaY = 0f
                if (groupWord != null) {
                    val p = sungFactor
                    val pOut = ((smoothPosition - groupWord.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val baseScalePerSegment = 0.012f
                    val spring = if (pOut > 0f) {
                        val totalAtEnd = (groupWord.pos * baseScalePerSegment) + peakScale
                        totalAtEnd * exp(-3.5f * pOut) * cos(5f * pOut * PI.toFloat()) * (1f - pOut)
                    } else if (groupWord.isLast) {
                        val base = groupWord.pos * baseScalePerSegment
                        base + peakScale * (1f - exp(-3.5f * p) * cos(5f * p * PI.toFloat()) * (1f - p))
                    } else {
                        (groupWord.pos * baseScalePerSegment) + if (p > 0f) 0.02f * (1f - p) else 0f
                    }
                    crescendoDeltaX = spring
                    crescendoDeltaY = spring
                }

                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) {
                    0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp)
                } else {
                    0f
                }
                val charScaleX = 1f + (wobble * 0.025f) + crescendoDeltaX + nudgeScale * 0.3f
                val charScaleY = 1f + (wobble * 0.015f) + crescendoDeltaY + nudgeScale

                withTransform({
                    var waveOffset = 0f
                    if (groupWord != null) {
                        val timeInGroup = (smoothPosition - groupWord.groupStartMs).toFloat()
                        val timeToGroupEnd = (groupWord.groupEndMs - smoothPosition).toFloat()
                        val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) * (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                        if (waveFade > 0.01f) {
                            waveOffset = sin(System.currentTimeMillis() * 0.006f + i * 0.4f) * 3.24f * waveFade
                        }
                    }
                    translate(
                        left = alignShift + lineCurrentPushes[lineIdx] + charBounds.left,
                        top = charBounds.top + waveOffset
                    )
                    if (wordIdx != -1) {
                        scale(charScaleX, charScaleY, pivot = Offset(charBounds.width / 2f, charBounds.height))
                    }
                }) {
                    if (wordItem != null && !isWordSung && sungFactor > 0.001f) {
                        val duration = wordItem.endTime - wordItem.startTime
                        val impactRatio = duration.toFloat() / wordItem.text.length.coerceAtLeast(1)
                        val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                        val impactFactor = (
                            (((impactRatio - 100f) / 250f).coerceIn(0f, 1f) * 0.6f) +
                                (((duration.toFloat() - 300f) / 1500f).coerceIn(0f, 1f) * 0.4f)
                            ).coerceIn(0f, 1f) * fadeFactor
                        if (impactFactor > 0.01f) {
                            drawIntoCanvas { canvas ->
                                glowPaint.maskFilter = BlurMaskFilter(12.dp.toPx() * impactFactor, BlurMaskFilter.Blur.NORMAL)
                                glowPaint.color = expressiveAccent.copy(alpha = (0.35f * impactFactor).coerceIn(0f, 0.4f)).toArgb()
                                glowPaint.textSize = lyricStyle.fontSize.toPx()
                                glowPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                canvas.nativeCanvas.drawText(
                                    letterLayouts[i].layoutInput.text.text,
                                    0f,
                                    letterLayouts[i].firstBaseline,
                                    glowPaint
                                )
                            }
                        }
                    }

                    val baseAlpha = if (isWordSung || charLp > 0.99f) 1f else focusedAlpha + (1f - focusedAlpha) * sungFactor
                    drawText(letterLayouts[i], color = expressiveAccent.copy(alpha = if (wordIdx == -1) focusedAlpha else baseAlpha))
                    if (!isWordSung && charLp > 0f && charLp < 1f) {
                        val fillX = charBounds.width * charLp
                        val edgeWidth = (charBounds.width * 0.45f).coerceAtLeast(1f)
                        val solidWidth = (fillX - edgeWidth).coerceAtLeast(0f)
                        if (solidWidth > 0f) {
                            clipRect(left = 0f, top = 0f, right = solidWidth, bottom = charBounds.height) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        letterLayouts[i].layoutInput.text.text,
                                        0f,
                                        letterLayouts[i].firstBaseline,
                                        liquidPaint
                                    )
                                }
                            }
                        }
                        for (j in 0 until 12) {
                            val start = solidWidth + (j * edgeWidth / 12f)
                            val end = (solidWidth + ((j + 1) * edgeWidth / 12f) + 0.5f).coerceAtMost(fillX)
                            if (end > start) {
                                clipRect(left = start, top = 0f, right = end, bottom = charBounds.height) {
                                    drawText(letterLayouts[i], color = expressiveAccent.copy(alpha = 1f - (j + 0.5f) / 12f))
                                }
                            }
                        }
                    }
                }
                lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }
        }
    }
}

private fun buildLines(lyrics: String?, syncedLyrics: List<LyricsEntry>): List<LyricsEntry> {
    if (syncedLyrics.isNotEmpty() && entriesLookSynced(syncedLyrics)) {
        return listOf(LyricsEntry(time = 0L, text = "")) + syncedLyrics.sorted()
    }

    val plainSource = lyrics?.takeIf { it.isNotBlank() }
        ?: syncedLyrics.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
    val plainLines = plainSource
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (plainLines.isEmpty()) return emptyList()
    return plainLines.mapIndexed { index, line ->
        LyricsEntry(time = 1_000_000L + index, text = line)
    }
}

private fun entriesLookSynced(entries: List<LyricsEntry>): Boolean {
    if (entries.size < 2) return false
    val main = entries.filter { !it.isBackground }
    val list = if (main.size >= 2) main else entries
    val distinctTimes = list.map { it.time }.distinct()
    if (distinctTimes.size < 2) return false
    val firstPositive = distinctTimes.firstOrNull { it > 0L } ?: return false
    val maxTime = list.maxOf { it.time }
    if (maxTime - firstPositive < 5_000L) return false
    if (entries.any { !it.words.isNullOrEmpty() }) return true
    val distinctTimedLines = list.count { it.time > 0L }
    return distinctTimedLines >= (list.size * 0.5).toInt().coerceAtLeast(2)
}

private fun buildMergedLyricsList(lines: List<LyricsEntry>): List<LyricsListItem> {
    val result = mutableListOf<LyricsListItem>()
    lines.forEachIndexed { index, entry ->
        if (entry.text.isNotBlank()) {
            result.add(LyricsListItem.Line(index, entry))
        }
        if (index < lines.lastIndex) {
            val nextStart = lines[index + 1].time
            val currentEnd = when {
                !entry.words.isNullOrEmpty() -> entry.words.last().endTime
                entry.text.isBlank() -> entry.time
                else -> null
            }
            if (currentEnd != null && currentEnd < nextStart && nextStart - currentEnd > 4000L) {
                result.add(LyricsListItem.Indicator(index, currentEnd, nextStart))
            }
        }
    }
    return result
}

private fun findActiveLineIndices(lines: List<LyricsEntry>, position: Long): Set<Int> {
    val active = mutableSetOf<Int>()
    val hasWordTimings = lines.any { !it.words.isNullOrEmpty() }

    val distinctMainTimes = lines.asSequence()
        .filter { !it.isBackground }
        .map { it.time }
        .distinct()
        .take(3)
        .toList()
    if (distinctMainTimes.size < 2) return active

    for (index in lines.indices) {
        val line = lines[index]
        if (line.time > position) break
        val lineEndMs = if (!line.words.isNullOrEmpty()) {
            line.words.last().endTime
        } else {

            (index + 1 until lines.size)
                .asSequence()
                .map { lines[it].time }
                .firstOrNull { it > line.time }
                ?: Long.MAX_VALUE
        }
        if (position <= lineEndMs) active.add(index)
    }

    if (!hasWordTimings && active.size > 1) {
        val mainActive = active.filter { !lines[it].isBackground }
        if (mainActive.size > 1) {
            val maxTime = mainActive.maxOf { lines[it].time }
            active.removeAll { it in mainActive && lines[it].time < maxTime }
        }
    }

    return active
}

private fun sanitizeWordTimestamps(words: List<WordTimestamp>): List<WordTimestamp> {
    if (words.isEmpty()) return emptyList()
    return words.mapIndexed { index, word ->
        val nextWord = words.getOrNull(index + 1)
        val start = word.startTime.coerceAtLeast(0L)
        val endFromNext = nextWord?.startTime?.takeIf { it > start }
        val end = when {
            endFromNext != null && word.endTime > endFromNext -> endFromNext
            word.endTime <= start -> start + 80L
            else -> word.endTime
        }
        word.copy(startTime = start, endTime = end)
    }
}

private fun String.containsRtl(): Boolean {
    for (char in this) {
        val directionality = Character.getDirectionality(char).toInt()
        if (
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
        ) {
            return true
        }
    }
    return false
}

private fun String.toGraphemeClusters(): List<String> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result.add(substring(start, end))
        start = end
        end = iterator.next()
    }
    return result
}
