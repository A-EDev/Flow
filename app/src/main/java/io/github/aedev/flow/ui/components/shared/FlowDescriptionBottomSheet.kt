package io.github.aedev.flow.ui.components.shared

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.style.URLSpan
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.RichTextTarget
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.VideoDescriptionChannel
import io.github.aedev.flow.innertube.pages.VideoDescriptionFactoid
import io.github.aedev.flow.innertube.pages.VideoDescriptionPage
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import io.github.aedev.flow.ui.components.shared.rememberRichTextInlineContent
import io.github.aedev.flow.ui.theme.DescriptionLinkBlue
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.RICH_TEXT_HASHTAG
import io.github.aedev.flow.utils.RICH_TEXT_SEEK
import io.github.aedev.flow.utils.RICH_TEXT_URL
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatViewCount
import io.github.aedev.flow.utils.toAnnotatedString

fun parseHtmlDescription(
    rawHtml: String,
    linkColor: Color = DescriptionLinkBlue,
): AnnotatedString {
    // 1. Parse HTML into an Android Spanned object (Handles <br>, <a>, &amp;)
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString()

    return buildAnnotatedString {
        // 2. Append the clean text (no tags)
        append(text)

        // 3. Find all URLSpans created by the HTML parser and apply Compose styles
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        val htmlLinkRanges: List<IntRange> =
            urlSpans.map {
                spanned.getSpanStart(it) until spanned.getSpanEnd(it)
            }
        for (span in urlSpans) {
            val start = spanned.getSpanStart(span).coerceAtMost(text.length)
            val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
            if (start >= end) continue
            val rawUrl = span.url
            val absoluteUrl = if (rawUrl.startsWith("/")) "https://www.youtube.com$rawUrl" else rawUrl
            addStyle(
                style =
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                start = start,
                end = end,
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
                    style =
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    start = start,
                    end = end,
                )
                addStringAnnotation(tag = "URL", annotation = matchResult.value, start = start, end = end)
            }
        }

        val timestampRegex = Regex("""\b(?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2}\b""")
        timestampRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            addStyle(
                style =
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                start = start,
                end = end,
            )
            addStringAnnotation(tag = "TIMESTAMP", annotation = matchResult.value, start = start, end = end)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowDescriptionBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onSeekMs: (Long) -> Unit = {},
    onHashtagClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    descriptionPage: VideoDescriptionPage? = null,
    tags: List<String> = emptyList(),
    chapterCount: Int = 0,
    onChaptersClick: (() -> Unit)? = null,
    onTranscriptClick: (() -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null,
    artworkUrl: String? = null,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    dismissOnOutsideTap: Boolean = false,
    enableVerticalDismiss: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val sheetState = rememberFlowBottomSheetState()
    val descriptionScrollState = rememberScrollState()
    val tint = rememberMediaArtworkTint(artworkUrl ?: video.thumbnailUrl)
    val linkColor = tint.accent
    val textColor = MaterialTheme.colorScheme.onSurface

    val richDescription = descriptionPage?.description
    val descriptionEmoji = rememberRichTextInlineContent(richDescription)
    val descriptionText =
        remember(richDescription, video.description, linkColor, textColor) {
            richDescription?.toAnnotatedString(linkColor = linkColor, textColor = textColor)
                ?: parseHtmlDescription(video.description, linkColor)
        }
    var descLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // The server marks its own hashtags; a description that only arrived as HTML is still scanned.
    val hashtags =
        remember(richDescription, descriptionText.text) {
            richDescription
                ?.spans
                ?.mapNotNull { span -> (span.target as? RichTextTarget.Hashtag)?.tag }
                ?.map { tag -> if (tag.startsWith("#")) tag else "#$tag" }
                ?.distinct()
                ?.take(5)
                ?: Regex("""#\w+""")
                    .findAll(descriptionText.text)
                    .map { it.value }
                    .distinct()
                    .take(5)
                    .toList()
        }

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissible = enableVerticalDismiss,
        dismissOnOutsideTap = dismissOnOutsideTap,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            FlowSheetHeader(
                title = stringResource(R.string.description),
                onClose = { sheetState.dismiss() },
                modifier = dragModifier,
                titleStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                dividerAlpha = null,
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("description", descriptionText.text)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.description_copied),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_description))
                    }
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(descriptionScrollState)
                    .padding(horizontal = SheetHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val dateSettings = rememberDateDisplaySettings()
            val stats =
                descriptionPage?.factoids?.takeIf { it.isNotEmpty() }
                    ?: listOf(
                        VideoDescriptionFactoid(
                            value = formatLikeCount(video.likeCount.toInt()),
                            label = stringResource(R.string.likes),
                        ),
                        VideoDescriptionFactoid(
                            value = formatViewCount(descriptionPage?.viewCount ?: video.viewCount),
                            label = stringResource(R.string.views),
                        ),
                        VideoDescriptionFactoid(
                            value =
                                descriptionPage?.publishedDateText
                                    ?: dateSettings.format(video.uploadDate, DateContext.DESCRIPTION, video.timestamp),
                            label = stringResource(R.string.uploaded),
                        ),
                    )

            Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                stats.forEach { factoid ->
                    FactoidCard(
                        factoid = factoid,
                        tint = tint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            DescriptionBody(
                text = descriptionText,
                inlineContent = descriptionEmoji,
                tint = tint,
                onLayout = { descLayoutResult = it },
                onTap = { offset ->
                    descriptionText.handleDescriptionTap(
                        offset = offset,
                        onSeekMs = onSeekMs,
                        onHashtagClick = onHashtagClick,
                        onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                    )
                },
                layoutResult = descLayoutResult,
            )

            if (hashtags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                    verticalArrangement = Arrangement.spacedBy(ChipSpacing),
                ) {
                    hashtags.forEach { tag ->
                        Text(
                            text = tag,
                            color = tint.accent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier =
                                if (onHashtagClick == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .clip(CircleShape)
                                        .clickable { onHashtagClick(tag.removePrefix("#")) }
                                },
                        )
                    }
                }
            }

            if (chapterCount > 0 && onChaptersClick != null) {
                DescriptionSectionRow(
                    title = stringResource(R.string.chapters),
                    subtitle = pluralStringResource(R.plurals.chapters_count_template, chapterCount, chapterCount),
                    tint = tint,
                    onClick = onChaptersClick,
                )
            }

            if (onTranscriptClick != null) {
                DescriptionSectionRow(
                    title = stringResource(R.string.transcript),
                    subtitle = stringResource(R.string.transcript_subtitle),
                    tint = tint,
                    onClick = onTranscriptClick,
                )
            }

            descriptionPage?.channel?.let { channel ->
                ChannelCard(
                    channel = channel,
                    tint = tint,
                    onChannelClick = onChannelClick,
                    onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
                )
            }

            if (tags.isNotEmpty()) {
                val sortedTags =
                    remember(tags) {
                        tags.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                    }
                Column(verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
                    ) {
                        sortedTags.forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = tint.container,
                                contentColor = tint.onContainer,
                                modifier =
                                    if (onTagClick == null) Modifier else Modifier.clickable { onTagClick(tag) },
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

            Spacer(modifier = Modifier.height(SheetBottomSpacing))
        }
    }
}

/** One of the three figures above the description: a big value over the label that names it. */
@Composable
private fun FactoidCard(
    factoid: VideoDescriptionFactoid,
    tint: MediaArtworkTint,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = tint.container,
        contentColor = tint.onContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = FactoidVerticalPadding, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = factoid.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = factoid.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The description text in its own tinted card, collapsed until the reader asks for the rest.
 *
 * The button only appears once the text has actually overflowed, so a two-line description does
 * not get a control that expands nothing.
 */
@Composable
private fun DescriptionBody(
    text: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    tint: MediaArtworkTint,
    layoutResult: TextLayoutResult?,
    onLayout: (TextLayoutResult) -> Unit,
    onTap: (Int) -> Unit,
) {
    val highlightColor = tint.onContainer.copy(alpha = HIGHLIGHT_ALPHA)
    var expanded by rememberSaveable(text.text) { mutableStateOf(false) }
    var overflowed by remember(text.text) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = tint.container,
        contentColor = tint.onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(BodyPadding),
            verticalArrangement = Arrangement.spacedBy(BodySpacing),
        ) {
            SelectionContainer {
                BasicText(
                    text = text,
                    inlineContent = inlineContent,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = tint.onContainer,
                            lineHeight = 24.sp,
                            fontSize = 15.sp,
                        ),
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_BODY_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        onLayout(result)
                        if (result.hasVisualOverflow) overflowed = true
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .richTextHighlights(
                                text = text,
                                layoutResult = { layoutResult },
                                color = highlightColor,
                            ).pointerInput(text) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val result = layoutResult ?: return@detectTapGestures
                                        onTap(result.getOffsetForPosition(tapOffset))
                                    },
                                )
                            },
                )
            }

            if (overflowed) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tint.onContainer),
                    border = BorderStroke(width = 1.dp, color = tint.onContainer.copy(alpha = BODY_BUTTON_BORDER_ALPHA)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (expanded) {
                                stringResource(R.string.desc_see_less)
                            } else {
                                stringResource(R.string.desc_see_more)
                            },
                    )
                }
            }
        }
    }
}

/**
 * The creator behind the video, and the links they publish beside their channel.
 *
 * The links are the creator's own — a second channel, a social profile — and open outside the app,
 * which is why each carries the site's icon rather than a generic one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelCard(
    channel: VideoDescriptionChannel,
    tint: MediaArtworkTint,
    onChannelClick: ((String) -> Unit)?,
    onOpenLink: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = tint.container,
        contentColor = tint.onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(BodyPadding),
            verticalArrangement = Arrangement.spacedBy(BodySpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    if (onChannelClick == null || channel.channelId.isBlank()) {
                        Modifier
                    } else {
                        Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onChannelClick(channel.channelId) }
                    },
            ) {
                ChannelAvatarImage(
                    url = channel.avatarUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(ChannelAvatarSize)
                            .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (channel.subscribersText.isNotBlank()) {
                        Text(
                            text = channel.subscribersText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (channel.links.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                    verticalArrangement = Arrangement.spacedBy(ChipSpacing),
                ) {
                    channel.links.forEach { link ->
                        Surface(
                            onClick = { onOpenLink(link.url) },
                            shape = CircleShape,
                            color = Color.Transparent,
                            contentColor = tint.onContainer,
                            border = BorderStroke(width = 1.dp, color = tint.onContainer.copy(alpha = BODY_BUTTON_BORDER_ALPHA)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                if (link.iconUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = link.iconUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(LinkIconSize),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = link.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A titled row that opens another surface, in the same tinted card language as the body. */
@Composable
private fun DescriptionSectionRow(
    title: String,
    subtitle: String,
    tint: MediaArtworkTint,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = tint.container,
        contentColor = tint.onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

private val SheetHorizontalPadding = 16.dp
private val SheetBottomSpacing = 32.dp
private val SectionSpacing = 16.dp
private val CardSpacing = 8.dp
private val ChipSpacing = 8.dp
private val FactoidVerticalPadding = 14.dp
private val BodyPadding = PaddingValues(16.dp)
private val BodySpacing = 12.dp
private val ChannelAvatarSize = 40.dp
private val LinkIconSize = 16.dp
private const val COLLAPSED_BODY_LINES = 6
private const val BODY_BUTTON_BORDER_ALPHA = 0.35f

/**
 * Routes a tap in the description to whatever the span under it points at.
 *
 * Timestamps carry their seconds when the text came from InnerTube; a description that arrived as
 * HTML still yields a printed "1:57" that has to be parsed back.
 */
internal fun AnnotatedString.handleDescriptionTap(
    offset: Int,
    onSeekMs: (Long) -> Unit,
    onHashtagClick: ((String) -> Unit)?,
    onOpenUrl: (String) -> Unit,
) {
    getStringAnnotations(RICH_TEXT_SEEK, offset, offset).firstOrNull()?.let { seek ->
        seek.item.toLongOrNull()?.let { onSeekMs(it * 1_000L) }
        return
    }
    getStringAnnotations(LEGACY_TIMESTAMP_TAG, offset, offset).firstOrNull()?.let { legacy ->
        onSeekMs(commentTimestampToMs(legacy.item))
        return
    }
    getStringAnnotations(RICH_TEXT_HASHTAG, offset, offset).firstOrNull()?.let { hashtag ->
        onHashtagClick?.invoke(hashtag.item.removePrefix("#"))
        return
    }
    getStringAnnotations(RICH_TEXT_URL, offset, offset).firstOrNull()?.let { url ->
        onOpenUrl(url.item)
    }
}

private const val LEGACY_TIMESTAMP_TAG = "TIMESTAMP"

private const val HIGHLIGHT_ALPHA = 0.12f
