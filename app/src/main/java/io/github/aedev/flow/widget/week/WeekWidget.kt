package io.github.aedev.flow.widget.week

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.recap.RecapRoutes
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.image.ShapeDecor
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
import io.github.aedev.flow.widget.core.image.WidgetShape
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** This week's watching and listening from the Recap ledgers, with the month's top artist. */
class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 150.dp)))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val week = withContext(Dispatchers.IO) { widgetEntryPoint(context).weekSource().load() }
        val portrait = WidgetImageLoader.load(context, week.topArtistImage, context.dpToPx(PORTRAIT_DP), shape = WidgetShape.COOKIE_9)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) { WeekContent(week, portrait) }
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val week = withContext(Dispatchers.IO) { widgetEntryPoint(context).weekSource().load() }
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { WeekContent(week, portrait = null) } }
    }

    internal companion object {
        const val PORTRAIT_DP = 64f
    }
}

@Composable
private fun WeekContent(
    week: WidgetWeek,
    portrait: Bitmap?,
) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WideMinWidth
    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .widgetSurface()
                .padding(14.dp)
                .clickable(actionStartActivity(WidgetDeepLink.openRoute(context, RecapRoutes.stats()))),
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Text(text = context.getString(R.string.widget_week_title), style = WidgetText.labelMedium(), maxLines = 1)
            Text(text = timeLabel(context, week.totalMs), style = WidgetText.headline(GlanceTheme.colors.primary), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            DayBars(week.days)
        }
        if (wide && week.topArtist != null) {
            Spacer(GlanceModifier.width(16.dp))
            TopArtist(week.topArtist, portrait)
        }
    }
}

/** One bar per day, today in the primary colour; a day with nothing still shows a stub. */
@Composable
private fun DayBars(days: List<WeekDay>) {
    val context = LocalContext.current
    val longest = days.maxOfOrNull { it.ms }?.takeIf { it > 0L } ?: 1L
    val today = LocalDate.now()
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    Row(modifier = GlanceModifier.fillMaxWidth().height(BarHeight), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .height(BarMin + (BarHeight - BarMin) * (day.ms.toFloat() / longest))
                            .background(if (day.date == today) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                            .cornerRadius(WidgetDimens.BadgeCorner),
                ) {}
            }
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = WidgetDimens.SmallGap)) {
        days.forEach { day ->
            Text(
                text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                style = WidgetText.centered(WidgetText.labelSmall(GlanceTheme.colors.onSurfaceVariant)),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
        }
    }
}

/** The artist on Cookie9, the shape every artist portrait wears in the app. */
@Composable
private fun TopArtist(
    name: String,
    portrait: Bitmap?,
) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.width(96.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (portrait != null) {
                Image(
                    ImageProvider(portrait),
                    contentDescription = null,
                    modifier = GlanceModifier.size(PortraitSize),
                    contentScale = ContentScale.Fit,
                )
            } else {
                ShapeDecor(WidgetShape.COOKIE_9, GlanceTheme.colors.secondaryContainer, PortraitSize)
            }
        }
        Spacer(GlanceModifier.height(WidgetDimens.SmallGap))
        Text(text = name, style = WidgetText.centered(WidgetText.titleSmall()), maxLines = 2)
        Text(
            text = context.getString(R.string.widget_week_top_artist),
            style = WidgetText.centered(WidgetText.labelSmall(GlanceTheme.colors.onSurfaceVariant)),
            maxLines = 2,
        )
    }
}

private fun timeLabel(
    context: Context,
    ms: Long,
): String {
    val minutes = (ms / 60_000L).toInt()
    val hours = minutes / 60
    return if (hours > 0) {
        context.getString(R.string.duration_hours_minutes, hours, minutes % 60)
    } else {
        context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
    }
}

private val WideMinWidth = 250.dp
private val BarHeight = 40.dp
private val BarMin = 4.dp
private val PortraitSize = WeekWidget.PORTRAIT_DP.dp

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }
}
