package io.github.aedev.flow.widget.recognize

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.FlowGlanceTheme
import io.github.aedev.flow.widget.core.ShapeDecor
import io.github.aedev.flow.widget.core.WidgetDeepLink
import io.github.aedev.flow.widget.core.WidgetShape
import io.github.aedev.flow.widget.core.widgetColorsFlow
import io.github.aedev.flow.widget.core.widgetSurface
import kotlinx.coroutines.flow.first

/**
 * One-tap music recognition. The whole 1x1 tile IS the button: a full-bleed
 * primary-container block with the launcher's corner radius and a single mic glyph.
 */
class RecognizeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                RecognizeContent()
            }
        }
    }
}

@Composable
private fun RecognizeContent() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetSurface(GlanceTheme.colors.primaryContainer)
            .clickable(actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_RECOGNIZE))),
        contentAlignment = Alignment.Center,
    ) {
        // Sunny decor from the expressive shape library carries the mic glyph
        ShapeDecor(WidgetShape.SUNNY, GlanceTheme.colors.primary, 52.dp)
        Image(
            provider = ImageProvider(R.drawable.ic_widget_mic),
            contentDescription = context.getString(R.string.recognize_music),
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
    }
}

class RecognizeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecognizeWidget()
}
