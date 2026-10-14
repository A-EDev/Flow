package io.github.aedev.flow.ui.components.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.aedev.flow.R

/** An artist with a portrait and an add/added toggle, for picking artists from a list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaArtistToggleRow(
    name: String,
    thumbnailUrl: String,
    picked: Boolean,
    shape: Shape,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleDescription = stringResource(if (picked) R.string.favourite_artists_remove else R.string.favourite_artists_add, name)
    SegmentedListItem(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            ArtworkThumbnail(
                thumbnailUrl = thumbnailUrl.ifBlank { null },
                shape = flowArtistShape(),
                placeholder = Icons.Rounded.Person,
            )
        },
        trailingContent = {
            FilledTonalIconToggleButton(
                checked = picked,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            ) {
                Icon(if (picked) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
            }
        },
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
