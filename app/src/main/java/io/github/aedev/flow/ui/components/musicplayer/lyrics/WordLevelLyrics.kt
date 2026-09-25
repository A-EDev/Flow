package io.github.aedev.flow.ui.components.musicplayer.lyrics

import android.graphics.BlurMaskFilter
import android.graphics.Typeface
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.lyrics.WordTimestamp
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@Composable
internal fun WordLevelLyrics(
    mainText: String,
    words: List<WordTimestamp>,
    isActiveLine: Boolean,
    isPlaying: Boolean,
    syncOffsetMs: Long,
    currentPositionState: Long,
    lyricStyle: TextStyle,
    lineColor: Color,
    expressiveAccent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val glowPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val liquidPaint =
        remember(expressiveAccent) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        }
    var smoothPosition by remember { mutableLongStateOf(currentPositionState) }

    val latestSyncOffsetMs by rememberUpdatedState(syncOffsetMs)
    // Paused, the sung position cannot move on its own; only a seek moves it, through the panel.
    LaunchedEffect(isActiveLine, isPlaying) {
        if (isActiveLine && isPlaying) {
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
                    smoothPosition =
                        lastPlayerPos + (if (EnhancedMusicPlayerManager.isPlaying()) elapsed else 0L) + latestSyncOffsetMs
                }
            }
        }
    }

    LaunchedEffect(isActiveLine, isPlaying, currentPositionState) {
        if (!isActiveLine || !isPlaying) smoothPosition = currentPositionState
    }

    val sanitizedInputWords = remember(words) { sanitizeWordTimestamps(words) }
    val (effectiveWords, effectiveToOriginalIdx) =
        remember(sanitizedInputWords, isBackground) { splitTrailingHyphenWord(sanitizedInputWords) }

    val graphemeClusters = remember(mainText) { mainText.toGraphemeClusters() }
    val clusterCount = graphemeClusters.size
    val clusterCharOffsets = remember(mainText) { clusterStartOffsets(graphemeClusters) }

    val charToWordData =
        remember(mainText, effectiveWords, isBackground, graphemeClusters, clusterCharOffsets) {
            mapClustersToWords(mainText, effectiveWords, isBackground, clusterCharOffsets)
        }

    val hyphenGroupData = remember(effectiveWords) { hyphenGroups(effectiveWords) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult =
            remember(mainText, maxWidthPx, lyricStyle) {
                textMeasurer.measure(
                    text = mainText,
                    style = lyricStyle,
                    constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                    softWrap = true,
                )
            }
        val letterLayouts =
            remember(mainText, lyricStyle) {
                graphemeClusters.map { cluster -> textMeasurer.measure(cluster, lyricStyle) }
            }
        val isRtlText = remember(mainText) { mainText.containsRtl() }
        // One gradient per line and colour; the shimmer only slides it, through the local matrix.
        val liquidShader =
            remember(layoutResult.size, expressiveAccent) {
                android.graphics.LinearGradient(
                    -layoutResult.size.width / 2f,
                    0f,
                    layoutResult.size.width / 2f,
                    layoutResult.size.height.toFloat(),
                    intArrayOf(
                        expressiveAccent.toArgb(),
                        Color.White.copy(alpha = 0.8f).toArgb(),
                        expressiveAccent.toArgb(),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
        val shimmerMatrix = remember { android.graphics.Matrix() }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { layoutResult.size.height.toDp() })
                    .graphicsLayer(
                        clip = false,
                        compositingStrategy = CompositingStrategy.Offscreen,
                    ),
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = lineColor)
                return@Canvas
            }

            val currentMillis = System.currentTimeMillis()
            val shimmerOffset = (currentMillis % 3000L) / 3000f
            val shaderX = layoutResult.size.width * shimmerOffset
            shimmerMatrix.setTranslate(shaderX, 0f)
            liquidShader.setLocalMatrix(shimmerMatrix)
            liquidPaint.shader = liquidShader
            liquidPaint.textSize = lyricStyle.fontSize.toPx()

            if (isRtlText) {
                val (wordIdxMap, _, _) = charToWordData
                val wordFactors =
                    effectiveWords.map { word ->
                        val isWordSung = smoothPosition > word.endTime
                        val isWordActive = smoothPosition in word.startTime..word.endTime
                        val sungFactor =
                            if (isWordSung) {
                                1f
                            } else if (isWordActive) {
                                (
                                    (smoothPosition - word.startTime).toFloat() /
                                        (word.endTime - word.startTime).coerceAtLeast(
                                            1,
                                        )
                                ).coerceIn(0f, 1f)
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
                                        liquidPaint,
                                    )
                                }
                            }
                        } else if (isWordActive && sungFactor > 0f) {
                            val fillLeft = right - ((right - left) * sungFactor)
                            clipRect(left = fillLeft, top = top, right = right, bottom = bottom) {
                                drawText(
                                    layoutResult,
                                    color = expressiveAccent.copy(alpha = focusedAlpha + (1f - focusedAlpha) * sungFactor),
                                )
                            }
                        }
                    }
                }
                return@Canvas
            }

            val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
            val wordFactors =
                effectiveWords.map { word ->
                    val isWordSung = smoothPosition > word.endTime
                    val isWordActive = smoothPosition in word.startTime..word.endTime
                    val sungFactor =
                        if (isWordSung) {
                            1f
                        } else if (isWordActive) {
                            (
                                (smoothPosition - word.startTime).toFloat() /
                                    (word.endTime - word.startTime).coerceAtLeast(
                                        1,
                                    )
                            ).coerceIn(0f, 1f)
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
                val impact =
                    if (timeSinceStart in 0f..750f) {
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
                    crescendoDeltaX =
                        if (pOut > 0f) {
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
                val charLp =
                    if (wordItem != null) {
                        val dur = (wordItem.endTime - wordItem.startTime).coerceAtLeast(100L).toDouble()
                        val wordProgress = (smoothPosition.toDouble() - wordItem.startTime) / dur
                        val cInW = charInWordMap[i].toDouble()
                        val wLen = wordLenMap[i].toDouble()
                        ((wordProgress - cInW / wLen) * wLen).coerceIn(0.0, 1.0).toFloat()
                    } else {
                        0f
                    }
                val nudgeScale =
                    if (wordItem != null && !isWordSung && sungFactor > 0f) {
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
                val alignShift =
                    when (alignment) {
                        TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                        TextAlign.Right -> -lineTotalPushes[lineIdx]
                        else -> 0f
                    }
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                val charLp =
                    if (wordItem != null) {
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
                    val spring =
                        if (pOut > 0f) {
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

                val nudgeScale =
                    if (wordItem != null && !isWordSung && sungFactor > 0f) {
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
                        top = charBounds.top + waveOffset,
                    )
                    if (wordIdx != -1) {
                        scale(charScaleX, charScaleY, pivot = Offset(charBounds.width / 2f, charBounds.height))
                    }
                }) {
                    if (wordItem != null && !isWordSung && sungFactor > 0.001f) {
                        val duration = wordItem.endTime - wordItem.startTime
                        val impactRatio = duration.toFloat() / wordItem.text.length.coerceAtLeast(1)
                        val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                        val impactFactor =
                            (
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
                                    glowPaint,
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
                                        liquidPaint,
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
