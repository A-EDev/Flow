package io.github.aedev.flow.widget.core

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aedev.flow.R

/** Display model for the video content widgets. The first item may carry a hero image. */
data class WidgetVideoItem(
    val videoId: String,
    val title: String,
    val subtitle: String,
    val thumbnail: Bitmap? = null,
    val hero: Bitmap? = null,
)

// Row thumbnail (16:9) — shared so every list widget loads/caches at the same size.
val WIDGET_THUMB_WIDTH = 76.dp
val WIDGET_THUMB_HEIGHT = 43.dp
const val WIDGET_THUMB_CORNER_DP = 10f

// Hero image (16:9) load size for the featured first item.
const val WIDGET_HERO_WIDTH_PX = 480
const val WIDGET_HERO_HEIGHT_PX = 270
const val WIDGET_HERO_CORNER_DP = 14f

/**
 * Magazine-style panel shared by the content widgets: tonal icon-chip header,
 * a featured hero card for the newest item, then compact rows.
 */
@Composable
fun WidgetVideoPanel(
    title: String,
    headerIconRes: Int,
    chipBackground: ColorProvider,
    chipContent: ColorProvider,
    headerAction: Action,
    emptyMessage: String,
    emptyAction: Action,
    items: List<WidgetVideoItem>,
) {
    Column(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        PanelHeader(title, headerIconRes, chipBackground, chipContent, headerAction)
        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(emptyAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMessage,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        } else {
            val heroItem = items.first().takeIf { it.hero != null }
            val rowItems = if (heroItem != null) items.drop(1) else items
            LazyColumn {
                if (heroItem != null) {
                    item(itemId = heroItem.videoId.hashCode().toLong()) { HeroCard(heroItem) }
                }
                items(rowItems, itemId = { it.videoId.hashCode().toLong() }) { item ->
                    VideoRow(item)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    iconRes: Int,
    chipBackground: ColorProvider,
    chipContent: ColorProvider,
    onClick: Action,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(chipBackground)
                .cornerRadius(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(chipContent),
            )
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroCard(item: WidgetVideoItem) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(actionStartActivity(WidgetDeepLink.playVideo(context, item.videoId))),
    ) {
        Image(
            provider = ImageProvider(item.hero!!),
            contentDescription = null,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(104.dp)
                .cornerRadius(WIDGET_HERO_CORNER_DP.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = item.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 2,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
    }
}

@Composable
private fun VideoRow(item: WidgetVideoItem) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(actionStartActivity(WidgetDeepLink.playVideo(context, item.videoId))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.thumbnail != null) {
            Image(
                provider = ImageProvider(item.thumbnail),
                contentDescription = null,
                modifier = GlanceModifier.size(WIDGET_THUMB_WIDTH, WIDGET_THUMB_HEIGHT),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .size(WIDGET_THUMB_WIDTH, WIDGET_THUMB_HEIGHT)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(WIDGET_THUMB_CORNER_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                maxLines = 2,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
