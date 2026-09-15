package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val EntityImageSize = 28.dp

/**
 * One row of a typeahead list: search history, live suggestions, in-place filtering.
 *
 * [query] is emphasised inside [text] the way YouTube emphasises the part the user has not typed —
 * the completion is bold, the prefix is not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowSuggestionRow(
    text: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    leadingImageUrl: String? = null,
    supportingText: String? = null,
    trailingIcon: ImageVector? = null,
    trailingContentDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val label = remember(text, query) { emphasiseCompletion(text, query) }
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            if (leadingImageUrl.isNullOrBlank()) {
                Icon(imageVector = leadingIcon, contentDescription = null)
            } else {
                AsyncImage(
                    model = leadingImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(EntityImageSize).clip(CircleShape),
                )
            }
        },
        supportingContent =
            supportingText?.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailingContent =
            trailingIcon?.let {
                {
                    IconButton(onClick = { onTrailingClick?.invoke() }) {
                        Icon(imageVector = it, contentDescription = trailingContentDescription)
                    }
                }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun emphasiseCompletion(
    text: String,
    query: String,
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || !text.startsWith(trimmed, ignoreCase = true)) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, trimmed.length))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(text.substring(trimmed.length))
        }
    }
}
