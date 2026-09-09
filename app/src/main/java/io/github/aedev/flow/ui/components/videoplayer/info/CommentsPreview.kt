package io.github.aedev.flow.ui.components.videoplayer.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatRichText

@Composable
internal fun CommentsPreview(
    latestComment: String?,
    authorAvatar: String?,
    showPreviewText: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comments),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (showPreviewText && !latestComment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = authorAvatar,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val annotatedComment =
                        if (!latestComment.isNullOrBlank()) {
                            formatRichText(
                                text = latestComment,
                                primaryColor = primaryColor,
                                textColor = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            null
                        }

                    Text(
                        text = annotatedComment ?: AnnotatedString(""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (showPreviewText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.add_comment_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
