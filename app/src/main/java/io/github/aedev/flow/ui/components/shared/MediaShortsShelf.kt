package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.ui.theme.extendedColors

private val ShelfVerticalPadding = 4.dp
private val HeaderHorizontalPadding = 12.dp
private val HeaderVerticalPadding = 8.dp
private val HeaderIconSize = 24.dp
private val HeaderIconSpacing = 8.dp
private val StripContentPadding = PaddingValues(horizontal = 12.dp)
private val StripSpacing = 10.dp

/**
 * A horizontal strip of reels under the Shorts heading. [title] is the server's shelf title where
 * one exists; the branded default otherwise.
 */
@Composable
fun MediaShortsShelf(
    shorts: List<Video>,
    onShortClick: (shelf: List<Video>, tapped: Video) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
) {
    val uniqueShorts = remember(shorts) { shorts.distinctByNonBlankKey(Video::id) }
    if (uniqueShorts.isEmpty()) return
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = ShelfVerticalPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (onSeeAllClick != null) Modifier.clickable(onClick = onSeeAllClick) else Modifier)
                    .padding(horizontal = HeaderHorizontalPadding, vertical = HeaderVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_shorts),
                contentDescription = stringResource(R.string.shorts),
                tint = MaterialTheme.extendedColors.shortsAccent,
                modifier = Modifier.size(HeaderIconSize),
            )
            Spacer(modifier = Modifier.width(HeaderIconSpacing))
            Text(
                text = title ?: stringResource(R.string.shorts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (onSeeAllClick != null) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(HeaderIconSize),
                )
            }
        }

        LazyRow(
            contentPadding = StripContentPadding,
            horizontalArrangement = Arrangement.spacedBy(StripSpacing),
        ) {
            items(uniqueShorts, key = { it.id }) { short ->
                MediaShortCard(video = short, onClick = { onShortClick(uniqueShorts, short) })
            }
        }
    }
}
