package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatRichText

@Composable
fun FlowReplyItem(
    reply: Comment,
    onTimestampClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val annotatedText =
        remember(reply.text, primaryColor) {
            formatRichText(
                text = reply.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }
    var replyTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(reply.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        ChannelAvatarImage(
            url = reply.authorThumbnail,
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        onAvatarClick(reply.authorThumbnail)
                        showFullSizeImage = true
                    },
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(reply.author),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onAuthorClick(commentAuthorChannelRef(reply)) },
                            ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = localizedCommentPublishedTime(reply.publishedTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Reply Body
            SelectionContainer {
                BasicText(
                    text = annotatedText,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp,
                        ),
                    onTextLayout = { replyTextLayoutResult = it },
                    modifier =
                        Modifier.pointerInput(annotatedText) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    replyTextLayoutResult?.let { result ->
                                        val offset = result.getOffsetForPosition(tapOffset)
                                        val ts =
                                            annotatedText
                                                .getStringAnnotations("TIMESTAMP", offset, offset)
                                                .firstOrNull()
                                        if (ts != null) {
                                            onTimestampClick(ts.item)
                                        } else {
                                            annotatedText
                                                .getStringAnnotations("URL", offset, offset)
                                                .firstOrNull()
                                                ?.let {
                                                    try {
                                                        uriHandler.openUri(it.item)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                        }
                                    }
                                },
                            )
                        },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Bar (Minimal for replies)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                if (reply.likeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatLikeCount(reply.likeCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
