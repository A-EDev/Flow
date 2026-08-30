package io.github.aedev.flow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.style.URLSpan
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatViewCount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun parseHtmlDescription(rawHtml: String): AnnotatedString = parseHtmlDescription(rawHtml = rawHtml, accentColor = Color.Unspecified)

private fun parseHtmlDescription(
    rawHtml: String,
    accentColor: Color,
): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString()

    return buildAnnotatedString {
        append(text)

        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        for (span in urlSpans) {
            val start = spanned.getSpanStart(span).coerceAtMost(text.length)
            val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
            if (start >= end) continue

            val rawUrl = span.url
            val absoluteUrl = if (rawUrl.startsWith("/")) "https://www.youtube.com$rawUrl" else rawUrl
            addStyle(
                style =
                    SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                start = start,
                end = end,
            )
            addStringAnnotation(tag = "URL", annotation = absoluteUrl, start = start, end = end)
        }

        val htmlUrlStarts = urlSpans.map { spanned.getSpanStart(it) }.toSet()
        Regex("""https?://[^\s]+""").findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            if (start !in htmlUrlStarts) {
                val end = matchResult.range.last + 1
                addStyle(
                    style =
                        SpanStyle(
                            color = accentColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    start = start,
                    end = end,
                )
                addStringAnnotation(tag = "URL", annotation = matchResult.value, start = start, end = end)
            }
        }

        Regex("""\b(?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2}\b""").findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            addStyle(
                style = SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold),
                start = start,
                end = end,
            )
            addStringAnnotation(tag = "TIMESTAMP", annotation = matchResult.value, start = start, end = end)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlowDescriptionBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onTimestampClick: (String) -> Unit = {},
    tags: List<String> = emptyList(),
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    dismissOnOutsideTap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberFlowReduceMotion()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnSheetProgressChange by rememberUpdatedState(onSheetProgressChange)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    val descriptionScrollState = rememberScrollState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(sheetHeightPx, collapsedHeightPx, sheetProgressRangePx) {
        snapshotFlow {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect { progress ->
            latestOnSheetProgressChange(progress)
        }
    }

    val descriptionText =
        remember(video.description, accentColor) {
            parseHtmlDescription(video.description, accentColor)
        }
    var descLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val hashtags =
        remember(descriptionText.text) {
            Regex("#\\w+")
                .findAll(descriptionText.text)
                .map { it.value }
                .take(5)
                .toList()
        }

    fun animateToExpanded() {
        coroutineScope.launch {
            if (reduceMotion) {
                sheetHeightPx.snapTo(expandedHeightPx)
            } else {
                sheetHeightPx.animateTo(
                    targetValue = expandedHeightPx,
                    animationSpec =
                        tween(
                            durationMillis = FlowMotion.CONTENT_DURATION_MILLIS,
                            easing = FlowMotion.EnterEasing,
                        ),
                )
            }
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        coroutineScope.launch {
            if (reduceMotion) {
                sheetHeightPx.snapTo(collapsedHeightPx)
            } else {
                sheetHeightPx.animateTo(
                    targetValue = collapsedHeightPx,
                    animationSpec =
                        tween(
                            durationMillis = FlowMotion.EXIT_DURATION_MILLIS,
                            easing = FlowMotion.ExitEasing,
                        ),
                )
            }
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx, reduceMotion) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        if (reduceMotion) {
            sheetHeightPx.snapTo(expandedHeightPx)
        } else {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.ENTER_DURATION_MILLIS,
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier =
        Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (isAnimatingOut) return@detectVerticalDragGestures
                    velocityTracker.addPointerInputChange(change)
                    coroutineScope.launch {
                        val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                        sheetHeightPx.snapTo(nextValue)
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    if (!isAnimatingOut) animateToExpanded()
                },
                onDragEnd = {
                    val velocityY = velocityTracker.calculateVelocity().y
                    velocityTracker.resetTracking()
                    when {
                        velocityY > 1_200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                        else -> animateToExpanded()
                    }
                },
            )
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (dismissOnOutsideTap) {
            val scrimColor = MaterialTheme.colorScheme.scrim
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .graphicsLayer {
                            val progress =
                                ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx)
                                    .coerceIn(0f, 1f)
                            alpha = 0.32f * progress
                        }.pointerInput(isAnimatingOut) {
                            detectTapGestures { animateToDismiss() }
                        },
            )
        }

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height =
                            sheetHeightPx.value
                                .roundToInt()
                                .coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minHeight = height,
                                    maxHeight = height,
                                ),
                            )
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(headerDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.description),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.description), descriptionText.text)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.description_copied),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_description))
                    }
                    IconButton(onClick = ::animateToDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(descriptionScrollState),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatItem(
                            value = formatLikeCount(video.likeCount.toInt()),
                            label = stringResource(R.string.likes),
                        )
                        VerticalHorizontalDivider()
                        StatItem(
                            value = formatViewCount(video.viewCount),
                            label = stringResource(R.string.views),
                        )
                        VerticalHorizontalDivider()
                        val dateSettings = rememberDateDisplaySettings()
                        StatItem(
                            value = dateSettings.format(video.uploadDate, DateContext.DESCRIPTION, video.timestamp),
                            label = stringResource(R.string.uploaded),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (hashtags.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                hashtags.forEach { tag ->
                                    Text(
                                        text = tag,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }

                        SelectionContainer {
                            BasicText(
                                text = descriptionText,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                onTextLayout = { descLayoutResult = it },
                                modifier =
                                    Modifier.pointerInput(descriptionText) {
                                        detectTapGestures(
                                            onTap = { tapOffset ->
                                                descLayoutResult?.let { result ->
                                                    val charOffset = result.getOffsetForPosition(tapOffset)
                                                    val timestamp =
                                                        descriptionText
                                                            .getStringAnnotations("TIMESTAMP", charOffset, charOffset)
                                                            .firstOrNull()
                                                    if (timestamp != null) {
                                                        onTimestampClick(timestamp.item)
                                                    } else {
                                                        descriptionText
                                                            .getStringAnnotations("URL", charOffset, charOffset)
                                                            .firstOrNull()
                                                            ?.let { uriHandler.openUri(it.item) }
                                                    }
                                                }
                                            },
                                        )
                                    },
                            )
                        }

                        if (tags.isNotEmpty()) {
                            val sortedTags =
                                remember(tags) {
                                    tags.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                                }

                            HorizontalDivider(
                                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )

                            Text(
                                text = stringResource(R.string.tags),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                sortedTags.forEach { tag ->
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun StatItem(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VerticalHorizontalDivider() {
    Box(
        modifier =
            Modifier
                .height(24.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
