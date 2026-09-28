package io.github.aedev.flow.widget.quickactions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import kotlinx.coroutines.flow.first

/** A search pill and the shortcuts chosen for this widget, as many as its width holds. */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // Exact sizing has no size of its own in the picker, so the preview names the one it draws.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(340.dp, 72.dp)))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { QuickActionsContent(QuickShortcut.DEFAULT) } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            val shortcuts = QuickShortcut.from(currentState<Preferences>())
            FlowGlanceTheme(colors) {
                QuickActionsContent(shortcuts)
            }
        }
    }
}

@Composable
private fun QuickActionsContent(shortcuts: List<QuickShortcut>) {
    val context = LocalContext.current
    val width = LocalSize.current.width
    val shown = shortcuts.take(shortcutsThatFit(width))
    Row(
        modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(horizontal = WidgetDimens.ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchPill(modifier = GlanceModifier.defaultWeight(), compact = width < WidePillWidth)
        shown.forEach { shortcut ->
            Spacer(GlanceModifier.width(ShortcutGap))
            CircleIconButton(
                imageProvider = ImageProvider(shortcut.icon),
                contentDescription = context.getString(shortcut.label),
                onClick = actionStartActivity(WidgetDeepLink.openRoute(context, shortcut.route)),
                backgroundColor = GlanceTheme.colors.secondaryContainer,
                contentColor = GlanceTheme.colors.onSecondaryContainer,
                modifier = GlanceModifier.size(WidgetDimens.TouchTarget),
            )
        }
    }
}

@Composable
private fun SearchPill(
    modifier: GlanceModifier,
    compact: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier =
            modifier
                .height(WidgetDimens.TouchTarget)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(WidgetDimens.TouchTarget / 2)
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
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = context.getString(if (compact) R.string.nav_search else R.string.search_in_flow),
            style = WidgetText.titleSmall(GlanceTheme.colors.onPrimaryContainer),
            maxLines = 1,
        )
    }
}

/** Keeps the pill at least [MinPillWidth] wide; the rest of the row holds 48 dp shortcuts. */
internal fun shortcutsThatFit(width: Dp): Int {
    val spare = width - WidgetDimens.ContentPadding * 2 - MinPillWidth
    return (spare / (WidgetDimens.TouchTarget + ShortcutGap)).toInt().coerceIn(0, QuickShortcut.MAX)
}

private val ShortcutGap = 6.dp
private val MinPillWidth = 104.dp
private val WidePillWidth = 300.dp

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
