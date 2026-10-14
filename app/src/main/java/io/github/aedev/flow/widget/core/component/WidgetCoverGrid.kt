package io.github.aedev.flow.widget.core.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText

/** Square covers in [columns], each [coverSize] wide, optionally captioned. */
@Composable
internal fun WidgetCoverGrid(
    items: List<WidgetMediaItem>,
    columns: Int,
    coverSize: Dp,
    showLabels: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    LazyVerticalGrid(gridCells = GridCells.Fixed(columns), modifier = modifier) {
        items(items, itemId = { it.id.hashCode().toLong() }) { item ->
            Column(modifier = GlanceModifier.padding(WidgetDimens.SmallGap).clickable(item.open)) {
                WidgetArtwork(
                    bitmap = item.thumbnail,
                    placeholderIcon = item.placeholderIcon,
                    modifier = GlanceModifier.size(coverSize),
                    contentDescription = item.title.takeUnless { showLabels },
                )
                if (showLabels) {
                    Text(
                        text = item.title,
                        style = WidgetText.labelMedium(),
                        maxLines = 1,
                        modifier = GlanceModifier.width(coverSize).padding(top = WidgetDimens.SmallGap),
                    )
                }
            }
        }
    }
}

/** Cover size that fills [width] with [columns] covers and the grid's own padding. */
internal fun coverSizeFor(
    width: Dp,
    columns: Int,
): Dp = ((width - WidgetDimens.ContentPadding * 2) / columns - WidgetDimens.SmallGap * 2).coerceAtLeast(WidgetDimens.TouchTarget)
