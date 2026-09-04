package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

private val SheetTitlePadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
private val SheetListPadding = PaddingValues(vertical = 8.dp)
private val CreateButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val SaveIndicatorSize = 28.dp
private const val SHEET_MAX_HEIGHT_FRACTION = 0.65f

@Immutable
data class CollectionSheetEntry(
    val id: String,
    val name: String,
    val supporting: String,
    val thumbnailUrl: String,
    val isSaved: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveToCollectionSheet(
    title: String,
    entries: List<CollectionSheetEntry>,
    placeholderIcon: ImageVector,
    createLabel: String,
    emptyLabel: String,
    onToggle: (CollectionSheetEntry) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * SHEET_MAX_HEIGHT_FRACTION

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(SheetTitlePadding),
            )

            HorizontalDivider()

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                contentPadding = SheetListPadding,
            ) {
                if (entries.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = emptyLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(SheetTitlePadding),
                        )
                    }
                } else {
                    items(
                        items = entries,
                        key = CollectionSheetEntry::id,
                        contentType = { "collection" },
                    ) { entry ->
                        MediaRow(
                            title = entry.name,
                            subtitle = entry.supporting,
                            titleMaxLines = 1,
                            onClick = { onToggle(entry) },
                            trailing = {
                                Icon(
                                    imageVector =
                                        if (entry.isSaved) {
                                            Icons.Filled.Bookmark
                                        } else {
                                            Icons.Outlined.BookmarkBorder
                                        },
                                    contentDescription = null,
                                    tint =
                                        if (entry.isSaved) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.size(SaveIndicatorSize),
                                )
                            },
                        ) {
                            CollectionThumbnail(
                                thumbnailUrl = entry.thumbnailUrl,
                                placeholder = placeholderIcon,
                            )
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
