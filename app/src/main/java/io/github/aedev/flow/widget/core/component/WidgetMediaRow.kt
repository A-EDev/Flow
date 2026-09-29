package io.github.aedev.flow.widget.core.component

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText

/** One video or song as the list widgets draw it. [progress] is the watched share, when there is one. */
data class WidgetMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val open: Action,
    val thumbnail: Bitmap? = null,
    val badge: String? = null,
    val emphasizedBadge: Boolean = false,
    val progress: Float? = null,
    val isSquare: Boolean = false,
    @DrawableRes val placeholderIcon: Int,
)

val WidgetRowThumb = DpSize(96.dp, 54.dp)
val WidgetRowSquareThumb = DpSize(54.dp, 54.dp)

@Composable
internal fun WidgetMediaRow(item: WidgetMediaItem) {
    val thumb = if (item.isSquare) WidgetRowSquareThumb else WidgetRowThumb
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = WidgetDimens.SmallGap).clickable(item.open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            WidgetArtwork(
                bitmap = item.thumbnail,
                placeholderIcon = item.placeholderIcon,
                modifier = GlanceModifier.size(thumb.width, thumb.height),
                badge = item.badge,
                badgeAlignment = if (item.emphasizedBadge) Alignment.BottomStart else Alignment.BottomEnd,
            )
            item.progress?.let { WidgetProgress(it, GlanceModifier.width(thumb.width).padding(top = WidgetDimens.SmallGap)) }
        }
        Spacer(GlanceModifier.width(WidgetDimens.ContentPadding))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = item.title, style = WidgetText.titleSmall(), maxLines = 2)
            if (item.subtitle.isNotBlank()) {
                Text(text = item.subtitle, style = WidgetText.bodySmall(), maxLines = 1)
            }
        }
    }
}

/** The featured first item: a wide image with its badge and progress, then the title unless [titleLines] is 0. */
@Composable
internal fun WidgetHeroCard(
    item: WidgetMediaItem,
    imageHeight: Dp,
    modifier: GlanceModifier = GlanceModifier,
    titleLines: Int = 2,
) {
    Column(modifier = modifier.fillMaxWidth().clickable(item.open)) {
        WidgetArtwork(
            bitmap = item.thumbnail,
            placeholderIcon = item.placeholderIcon,
            modifier = GlanceModifier.fillMaxWidth().height(imageHeight),
            badge = item.badge,
            badgeAlignment = if (item.emphasizedBadge) Alignment.BottomStart else Alignment.BottomEnd,
        )
        item.progress?.let { WidgetProgress(it, GlanceModifier.fillMaxWidth().padding(top = WidgetDimens.SmallGap)) }
        if (titleLines > 0) {
            Spacer(GlanceModifier.height(WidgetDimens.SmallGap))
            Text(text = item.title, style = WidgetText.titleSmall(), maxLines = titleLines)
            if (item.subtitle.isNotBlank()) {
                Text(text = item.subtitle, style = WidgetText.bodySmall(), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun WidgetProgress(
    progress: Float,
    modifier: GlanceModifier,
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.height(WidgetDimens.ProgressHeight),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}
