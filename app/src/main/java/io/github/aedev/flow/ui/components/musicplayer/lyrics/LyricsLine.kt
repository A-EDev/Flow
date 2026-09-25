package io.github.aedev.flow.ui.components.musicplayer.lyrics

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.WordTimestamp
import kotlin.math.abs

private const val LYRICS_WORD_CANVAS_WINDOW = 3

@Composable
internal fun IntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
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

    val progress =
        if (gapEndMs > gapStartMs) {
            ((currentPositionMs - gapStartMs).toFloat() / (gapEndMs - gapStartMs).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val animatedProgress by animateFloatAsState(progress, tween(100), label = "intervalProgress")

    Box(
        modifier =
            modifier
                .height(72.dp * rowHeight.value)
                .padding(top = 16.dp * rowHeight.value)
                .graphicsLayer {
                    this.alpha = alpha.value
                    this.clip = true
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.ui_instrumental),
                color = color.copy(alpha = 0.8f * (1f - animatedProgress)),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                    ),
            )
            Canvas(
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.5f)
                        .height(3.dp),
            ) {
                val center = size.width / 2f
                val halfRemaining = center * (1f - animatedProgress)
                drawLine(
                    color = color.copy(alpha = 0.15f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(center - halfRemaining, size.height / 2f),
                    end = Offset(center + halfRemaining, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f * (1f - animatedProgress)),
                    radius = size.height * 1.5f,
                    center = Offset(center - halfRemaining, size.height / 2f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f * (1f - animatedProgress)),
                    radius = size.height * 1.5f,
                    center = Offset(center + halfRemaining, size.height / 2f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricsLine(
    index: Int,
    item: LyricsEntry,
    isSynced: Boolean,
    isActiveLine: Boolean,
    syncOffsetMs: Long,
    bgVisible: Boolean,
    currentPositionState: Long,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    expressiveAccent: Color,
    isAutoScrollEnabled: Boolean,
    displayedCurrentLineIndex: Int,
    textAlign: TextAlign,
    onSizeChanged: (Int) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lineDistance = abs(index - displayedCurrentLineIndex)
    val dofBlurRadius by animateFloatAsState(
        targetValue =
            if (!isSynced || isActiveLine || item.isBackground) {
                0f
            } else {
                with(density) { (lineDistance * 4.dp.toPx()).coerceAtMost(16.dp.toPx()) }
            },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricsDofBlur",
    )
    val depthScale by animateFloatAsState(
        targetValue = if (isActiveLine) 1.05f else 0.92f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricsDepthScale",
    )

    val itemModifier =
        modifier
            .fillMaxWidth()
            .onSizeChanged { onSizeChanged(it.height) }
            .graphicsLayer {
                scaleX = depthScale
                scaleY = depthScale
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dofBlurRadius > 0.5f) {
                    renderEffect =
                        RenderEffect
                            .createBlurEffect(
                                dofBlurRadius,
                                dofBlurRadius,
                                android.graphics.Shader.TileMode.DECAL,
                            ).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            }.clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .background(Color.Transparent)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = if (item.isBackground) 0.dp else 12.dp,
                bottom = if (item.isBackground) 2.dp else 12.dp,
            )

    Box(modifier = itemModifier, contentAlignment = Alignment.Center) {
        @Composable
        fun LyricContent() {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val inactiveAlpha = if (item.isBackground) 0.2f else 0.45f
                val activeAlpha = 1f
                val focusedAlpha = if (item.isBackground) 0.6f else 0.45f
                val targetAlpha =
                    if (!isSynced || item.isBackground || isActiveLine) {
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
                val mainText =
                    if (item.isBackground) {
                        item.text.removePrefix("(").removeSuffix(")")
                    } else {
                        item.text
                    }
                val translation = item.translation?.takeIf { it.isNotBlank() }
                val adaptiveTextSize =
                    remember(mainText, lyricsTextSize, item.isBackground) {
                        adaptiveLyricsTextSize(lyricsTextSize, mainText.length, item.isBackground)
                    }

                val lyricStyle =
                    TextStyle(
                        fontSize = adaptiveTextSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = if (item.isBackground) FontStyle.Italic else FontStyle.Normal,
                        lineHeight =
                            if (item.isBackground) {
                                (adaptiveTextSize * lyricsLineSpacing).sp
                            } else {
                                (adaptiveTextSize * lyricsLineSpacing).sp
                            },
                        letterSpacing = 0.sp,
                        textAlign = textAlign,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle =
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                    )

                val effectiveWords =
                    if (item.words?.isNotEmpty() == true) {
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
                                    endTime = item.time + (idx * wordStaggerMs) + wordDurationMs,
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
                        syncOffsetMs = syncOffsetMs,
                        currentPositionState = currentPositionState,
                        lyricStyle = lyricStyle,
                        lineColor = lineColor,
                        expressiveAccent = expressiveAccent,
                        isBackground = item.isBackground,
                        focusedAlpha = focusedAlpha,
                        alignment = textAlign,
                    )
                } else {
                    Text(
                        text = mainText,
                        style = activeTextStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (translation != null) {
                    Text(
                        text = translation,
                        color =
                            if (isActiveLine) {
                                expressiveAccent.copy(alpha = 0.72f)
                            } else {
                                lineColor.copy(alpha = (lineColor.alpha * 0.78f).coerceIn(0.08f, 0.6f))
                            },
                        style =
                            lyricStyle.copy(
                                fontSize = (adaptiveTextSize * 0.52f).coerceAtLeast(15f).sp,
                                lineHeight = (adaptiveTextSize * 0.68f).coerceAtLeast(19f).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = FontStyle.Normal,
                                letterSpacing = 0.sp,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 7.dp),
                    )
                }
            }
        }

        if (item.isBackground) {
            AnimatedVisibility(
                visible = bgVisible,
                enter = fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                exit = fadeOut(tween(250)),
            ) {
                LyricContent()
            }
        } else {
            LyricContent()
        }
    }
}
