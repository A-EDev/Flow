package io.github.aedev.flow.ui.tv.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aedev.flow.ui.tv.player.state.TvSeekBarMarks
import io.github.aedev.flow.utils.formatDuration
import kotlinx.coroutines.isActive

/**
 * Focusable D-pad seek bar.
 *
 * Perf contract: position/duration arrive as providers, never as hot state.
 * A frame loop exists only while [active] (overlay visible + lifecycle STARTED)
 * and its tick is read solely inside the draw lambda — per-frame work is a
 * draw-phase invalidation of this one node, zero recomposition. Time labels
 * derive seconds from the same tick, so they recompose at most once per second.
 * Key handling lives in the screen's onPreviewKeyEvent, not here.
 */
@Composable
fun TvSeekBar(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    bufferedFractionProvider: () -> Float,
    scrubTargetMs: Long?,
    active: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    marks: TvSeekBarMarks? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val frameTick = remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(active, lifecycleOwner) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                withFrameMillis { frameTick.longValue = it }
            }
        }
    }

    val positionSeconds by remember {
        derivedStateOf {
            frameTick.longValue
            positionProvider() / 1_000L
        }
    }
    val durationSeconds by remember {
        derivedStateOf {
            frameTick.longValue
            durationProvider() / 1_000L
        }
    }

    // The bar floats directly over video (no scrim), so the neutral layers are
    // white-based like the music player's over-artwork chips — theme-derived
    // onSurface would vanish over video in light themes. Accent stays primary.
    val trackColor = Color.White.copy(alpha = 0.28f)
    val bufferedColor = Color.White.copy(alpha = 0.45f)
    val playedColor = MaterialTheme.colorScheme.primary
    val ghostColor = Color.White
    val sponsorColor = MaterialTheme.colorScheme.tertiary
    val selfPromoColor = MaterialTheme.colorScheme.secondary
    val interactionColor = MaterialTheme.colorScheme.error
    // Precomputed once per marks change: nothing allocates in the draw loop.
    val segmentColors = remember(marks, sponsorColor, selfPromoColor, interactionColor) {
        marks?.segments?.map { segment ->
            segment to when (segment.category) {
                "selfpromo" -> selfPromoColor
                "interaction", "poi_highlight" -> interactionColor
                else -> sponsorColor
            }
        }.orEmpty()
    }
    // YouTube-style chapter segmentation: the track splits into spans with a
    // small gap at every chapter start (falls back to one full-width span).
    val chapterSpans = remember(marks) {
        val fractions = marks?.chapterFractions.orEmpty()
            .filter { it > 0f && it < 1f }
            .distinct()
            .sorted()
        if (fractions.isEmpty()) {
            listOf(0f to 1f)
        } else {
            buildList {
                var start = 0f
                fractions.forEach { fraction ->
                    add(start to fraction)
                    start = fraction
                }
                add(start to 1f)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable()
                .drawBehind {
                    frameTick.longValue // subscribe this draw scope to frame ticks
                    val duration = durationProvider().coerceAtLeast(0L)
                    val position = positionProvider().coerceAtLeast(0L)
                    val barHeight = (if (focused) 8.dp else 5.dp).toPx()
                    val centerY = size.height / 2f
                    val top = centerY - barHeight / 2f
                    val corner = CornerRadius(2.dp.toPx())
                    val gapHalf = 1.25.dp.toPx()

                    val bufferedFraction = bufferedFractionProvider().coerceIn(0f, 1f)
                    val playedFraction = if (duration > 0L) {
                        (position.toFloat() / duration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    // Track, buffered, and played, clipped to each chapter span.
                    chapterSpans.forEach { (startFraction, endFraction) ->
                        val left = size.width * startFraction + if (startFraction > 0f) gapHalf else 0f
                        val right = size.width * endFraction - if (endFraction < 1f) gapHalf else 0f
                        if (right <= left) return@forEach
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, top),
                            size = Size(right - left, barHeight),
                            cornerRadius = corner,
                        )
                        val bufferedRight = (size.width * bufferedFraction).coerceIn(left, right)
                        if (bufferedRight > left) {
                            drawRoundRect(
                                color = bufferedColor,
                                topLeft = Offset(left, top),
                                size = Size(bufferedRight - left, barHeight),
                                cornerRadius = corner,
                            )
                        }
                        val playedRight = (size.width * playedFraction).coerceIn(left, right)
                        if (playedRight > left) {
                            drawRoundRect(
                                color = playedColor,
                                topLeft = Offset(left, top),
                                size = Size(playedRight - left, barHeight),
                                cornerRadius = corner,
                            )
                        }
                    }
                    segmentColors.forEach { (segment, color) ->
                        val left = size.width * segment.startFraction
                        val right = size.width * segment.endFraction
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(2.dp.toPx()), barHeight),
                            cornerRadius = corner,
                        )
                    }

                    val playedWidth = size.width * playedFraction
                    val knobRadius = (if (focused) 10.dp else 7.dp).toPx()
                    drawCircle(playedColor, knobRadius, center = Offset(playedWidth, centerY))

                    if (scrubTargetMs != null && duration > 0L) {
                        val ghostX = size.width * (scrubTargetMs.toFloat() / duration).coerceIn(0f, 1f)
                        drawCircle(ghostColor, knobRadius, center = Offset(ghostX, centerY))
                        drawCircle(playedColor, knobRadius * 0.45f, center = Offset(ghostX, centerY))
                    }
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownSeconds = scrubTargetMs?.div(1_000L) ?: positionSeconds
            Text(
                text = formatDuration(shownSeconds.coerceAtLeast(0L).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = if (scrubTargetMs != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.85f)
                },
            )
            Text(
                text = formatDuration(durationSeconds.coerceAtLeast(0L).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
