package io.github.aedev.flow.widget.recognize

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.image.ShapeDecor
import io.github.aedev.flow.widget.core.image.WidgetShape
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import kotlinx.coroutines.flow.first

/** One tap to identify a song: the whole tile is the button, labelled once it is wide enough. */
class RecognizeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(TILE, LABELLED))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { RecognizeContent() } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                RecognizeContent()
            }
        }
    }

    internal companion object {
        val TILE = DpSize(40.dp, 40.dp)
        val LABELLED = DpSize(130.dp, 40.dp)
    }
}

@Composable
private fun RecognizeContent() {
    val context = LocalContext.current
    val labelled = LocalSize.current.width >= RecognizeWidget.LABELLED.width
    val modifier =
        GlanceModifier
            .fillMaxSize()
            .widgetSurface(GlanceTheme.colors.primaryContainer)
            .clickable(actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_RECOGNIZE)))
    if (labelled) {
        Row(modifier = modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MicMark(48.dp)
            Spacer(GlanceModifier.width(WidgetDimens.ContentPadding))
            Text(
                text = context.getString(R.string.widget_identify_song),
                style = WidgetText.titleMedium(GlanceTheme.colors.onPrimaryContainer),
                maxLines = 2,
            )
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { MicMark(52.dp) }
    }
}

@Composable
private fun MicMark(size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        ShapeDecor(WidgetShape.SUNNY, GlanceTheme.colors.primary, size)
        Image(
            provider = ImageProvider(R.drawable.ic_widget_mic),
            contentDescription = LocalContext.current.getString(R.string.recognize_music),
            modifier = GlanceModifier.size(size * 0.46f),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
    }
}

class RecognizeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecognizeWidget()
}
