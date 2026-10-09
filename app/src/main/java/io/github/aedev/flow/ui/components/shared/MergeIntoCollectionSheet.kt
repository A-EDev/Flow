package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val SheetBottomPadding: Dp = 24.dp
private val HeaderPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
private val EmptyPadding: Dp = 24.dp
private val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val ListPadding = PaddingValues(vertical = 8.dp)
private val CreateButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val RowSpacing: Dp = 16.dp

data class CollectionTarget(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val itemCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergeIntoCollectionSheet(
    targets: List<CollectionTarget>,
    placeholder: ImageVector,
    itemCountLabel: @Composable (Int) -> String,
    createLabel: String,
    onSelect: (CollectionTarget) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = SheetBottomPadding),
        ) {
            Text(
                text = stringResource(R.string.merge_playlist_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(HeaderPadding),
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ListPadding,
            ) {
                if (targets.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.merge_playlist_no_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(EmptyPadding),
                        )
                    }
                } else {
                    items(
                        items = targets,
                        key = { it.id },
                        contentType = { "collection" },
                    ) { target ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(target)
                                        onDismiss()
                                    }.padding(RowPadding),
                            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumbnail(
                                thumbnailUrl = target.thumbnailUrl.takeIf { it.isNotEmpty() },
                                size = 48.dp,
                                placeholder = placeholder,
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = itemCountLabel(target.itemCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item(key = "create", contentType = "create") {
                    val buttonHeight = ButtonDefaults.MediumContainerHeight
                    FilledTonalButton(
                        onClick = onCreateNew,
                        shapes = ButtonDefaults.shapesFor(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(CreateButtonPadding)
                                .heightIn(min = buttonHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                        Text(
                            text = createLabel,
                            style = ButtonDefaults.textStyleFor(buttonHeight),
                        )
                    }
                }
            }
        }
    }
}
