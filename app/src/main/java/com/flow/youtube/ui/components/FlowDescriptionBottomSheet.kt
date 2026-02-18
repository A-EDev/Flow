package com.flow.youtube.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.flow.youtube.R
import androidx.core.text.HtmlCompat
import com.flow.youtube.data.model.Video
import com.flow.youtube.utils.formatLikeCount
import com.flow.youtube.utils.formatTimeAgo
import com.flow.youtube.utils.formatViewCount

fun parseHtmlDescription(rawHtml: String): AnnotatedString {
    // 1. Parse HTML into an Android Spanned object (Handles <br>, <a>, &amp;)
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString()

    return buildAnnotatedString {
        // 2. Append the clean text (no tags)
        append(text)

        // 3. Find all URLSpans created by the HTML parser and apply Compose styles
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        
        for (span in urlSpans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            val url = span.url
            
            // Apply Blue Color & Underline
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF3EA6FF), 
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                ),
                start = start,
                end = end
            )
            
            // Add Annotation so we can click it
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = start,
                end = end
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDescriptionBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    // FIX: Use the HTML parser instead of manual Regex
    val descriptionText = remember(video.description) {
        parseHtmlDescription(video.description)
    }

    // Auto-extract hashtags (This regex is still fine for finding hashtags in the clean text)
    val hashtags = remember(descriptionText.text) {
        Regex("#\\w+").findAll(descriptionText.text)
            .map { it.value }
            .take(5)
            .toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
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

                        // Rich Text Description — selectable text with clickable links
                        SelectionContainer {
                            ClickableText(
                                text = descriptionText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp,
                                    fontSize = 15.sp
                                ),
                                onClick = { offset ->
                                    descriptionText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            uriHandler.openUri(annotation.item)
                                        }
                                }
                            )
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