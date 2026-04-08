package io.github.aedev.flow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import androidx.core.text.HtmlCompat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatTimeAgo
import io.github.aedev.flow.utils.formatViewCount

fun parseHtmlDescription(rawHtml: String): AnnotatedString {
    // 1. Parse HTML into an Android Spanned object (Handles <br>, <a>, &amp;)
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString()

    return buildAnnotatedString {
        // 2. Append the clean text (no tags)
        append(text)

        // 3. Find all URLSpans created by the HTML parser and apply Compose styles
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        val htmlLinkRanges: List<IntRange> = urlSpans.map {
            spanned.getSpanStart(it) until spanned.getSpanEnd(it)
        }
        for (span in urlSpans) {
            val start = spanned.getSpanStart(span).coerceAtMost(text.length)
            val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
            if (start >= end) continue
            val rawUrl = span.url
            val absoluteUrl = if (rawUrl.startsWith("/")) "https://www.youtube.com$rawUrl" else rawUrl
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF3EA6FF),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                ),
                start = start,
                end = end
            )
            addStringAnnotation(tag = "URL", annotation = absoluteUrl, start = start, end = end)
        }

        // 4. Find plain-text URLs (https://... not covered by an anchor tag)
        val htmlUrlStarts = urlSpans.map { spanned.getSpanStart(it) }.toSet()
        val urlRegex = Regex("""https?://[^\s]+""")
        urlRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            // Skip if already covered by an HTML anchor
            if (start !in htmlUrlStarts) {
                val end = matchResult.range.last + 1
                addStyle(
                    style = SpanStyle(
                        color = Color(0xFF3EA6FF),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(tag = "URL", annotation = matchResult.value, start = start, end = end)
            }
        }

        // 5. Find timestamps — TIMESTAMP takes priority over URL.
        //    YouTube chapter links like <a href="?t=74">1:14</a> should seek the
        //    player, not open a browser.  We still add the TIMESTAMP annotation
        //    even when the range overlaps a URL span; the click handler resolves
        //    the conflict by preferring TIMESTAMP.
        val timestampRegex = Regex("""\b(?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2}\b""")
        timestampRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF3EA6FF),
                    fontWeight = FontWeight.SemiBold
                ),
                start = start,
                end = end
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
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    val descriptionText = remember(video.description) {
        parseHtmlDescription(video.description)
    }
    var descLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Auto-extract hashtags (This regex is still fine for finding hashtags in the clean text)
    val hashtags = remember(descriptionText.text) {
        Regex("#\\w+").findAll(descriptionText.text)
            .map { it.value }
            .take(5)
            .toList()
    }

    // Consume leftover scroll deltas so the sheet's drag handler never sees
    // overscroll at the content boundary — prevents collapse while scrolling.
    val preventCollapseOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ) = available
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val maxHeight = configuration.screenHeightDp.dp * 0.65f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .nestedScroll(preventCollapseOnScroll)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                // Copy entire description button
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("description", descriptionText.text)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.description_copied),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_description))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Video Title
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 2. Stats Row (Clean Layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        value = formatLikeCount(video.likeCount.toInt()),
                        label = stringResource(R.string.likes)
                    )
                    VerticalHorizontalDivider()
                    StatItem(
                        value = formatViewCount(video.viewCount).replace(" views", ""),
                        label = stringResource(R.string.views)
                    )
                    VerticalHorizontalDivider()
                    StatItem(
                        value = formatTimeAgo(video.uploadDate).replace(" ago", ""), // "5d" instead of "5d ago"
                        label = formatTimeAgo(video.uploadDate).let { if(it.contains("mo") || it.contains("yr")) stringResource(R.string.ago) else stringResource(R.string.since) }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )

                // 3. Description Container
                Surface(
                    color = MaterialTheme.colorScheme.surface, // Clean background
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        
                        // Hashtags Row
                        if (hashtags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                hashtags.forEach { tag ->
                                    Text(
                                        text = tag,
                                        color = Color(0xFF3EA6FF),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.clickable { /* Handle hashtag click */ }
                                    )
                                }
                            }
                        }

                        // Rich Text Description — clickable links and timestamps.
                        // SelectionContainer is intentionally omitted: wrapping ClickableText
                        // (or BasicText) inside SelectionContainer intercepts touch events
                        // and prevents the onClick/pointerInput from firing.
                        BasicText(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp,
                                fontSize = 15.sp
                            ),
                            onTextLayout = { descLayoutResult = it },
                            modifier = Modifier.pointerInput(descriptionText) {
                                detectTapGestures { tapOffset ->
                                    val result = descLayoutResult ?: return@detectTapGestures
                                    val charOffset = result.getOffsetForPosition(tapOffset)
                                    // TIMESTAMP takes priority — YouTube chapter links like
                                    // <a href="?t=74">1:14</a> seek the player, not a browser.
                                    val ts = descriptionText
                                        .getStringAnnotations("TIMESTAMP", charOffset, charOffset)
                                        .firstOrNull()
                                    if (ts != null) {
                                        onTimestampClick(ts.item)
                                    } else {
                                        descriptionText
                                            .getStringAnnotations("URL", charOffset, charOffset)
                                            .firstOrNull()
                                            ?.let { uriHandler.openUri(it.item) }
                                    }
                                }
                            }
                        )

                        // Tags section
                        if (tags.isNotEmpty()) {
                            val sortedTags = remember(tags) {
                                tags.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            )

                            Text(
                                text = stringResource(R.string.tags),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sortedTags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.clickable { /* future: search for tag */ }
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
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

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge, // Bigger
            fontWeight = FontWeight.Bold, // Bolder
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VerticalHorizontalDivider() {
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    )
}