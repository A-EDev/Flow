package io.github.aedev.flow.widget.quickactions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.FlowGlanceTheme
import io.github.aedev.flow.widget.core.WidgetDeepLink
import io.github.aedev.flow.widget.core.widgetColorsFlow
import io.github.aedev.flow.widget.core.widgetSurface
import kotlinx.coroutines.flow.first

/**
 * Launcher dock: an expressive primary search pill flanked by alternating tonal
 * shortcut circles (M3 Expressive multi-tonal color blocking). Pure deep links.
 */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                QuickActionsContent()
            }
        }
    }
}

@Composable
private fun QuickActionsContent() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchPill(modifier = GlanceModifier.defaultWeight())
        Spacer(modifier = GlanceModifier.width(8.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_download),
            contentDescription = context.getString(R.string.widget_open_downloads),
            onClick = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_DOWNLOADS)),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
            contentColor = GlanceTheme.colors.onSecondaryContainer,
            modifier = GlanceModifier.size(44.dp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_history),
            contentDescription = context.getString(R.string.widget_open_history),
            onClick = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_HISTORY)),
            backgroundColor = GlanceTheme.colors.tertiaryContainer,
            contentColor = GlanceTheme.colors.onTertiaryContainer,
            modifier = GlanceModifier.size(44.dp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_mic),
            contentDescription = context.getString(R.string.recognize_music),
            onClick = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_RECOGNIZE)),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
            contentColor = GlanceTheme.colors.onSecondaryContainer,
            modifier = GlanceModifier.size(44.dp),
        )
    }
}

@Composable
private fun SearchPill(modifier: GlanceModifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .height(46.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(23.dp)
            .padding(horizontal = 14.dp)
            .clickable(actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_SEARCH))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_search),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = context.getString(R.string.search_in_flow),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
