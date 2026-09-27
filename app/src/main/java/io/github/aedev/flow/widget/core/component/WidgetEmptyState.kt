package io.github.aedev.flow.widget.core.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import io.github.aedev.flow.widget.core.image.ShapeDecor
import io.github.aedev.flow.widget.core.image.WidgetShape
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText

/** An icon on the placeholder clover, one line of text, and either a button or a tappable whole. */
@Composable
internal fun WidgetEmptyState(
    @DrawableRes icon: Int,
    message: String,
    action: Action,
    actionLabel: String? = null,
    modifier: GlanceModifier = GlanceModifier,
) {
    val base = modifier.fillMaxSize().padding(WidgetDimens.ContentPadding)
    Column(
        modifier = if (actionLabel == null) base.clickable(action) else base,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapeDecor(WidgetShape.CLOVER, GlanceTheme.colors.secondaryContainer, PlaceholderSize)
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        Text(text = message, style = WidgetText.centered(WidgetText.bodySmall()), maxLines = 2)
        if (actionLabel != null) {
            Spacer(GlanceModifier.height(WidgetDimens.ContentPadding))
            FilledButton(text = actionLabel, onClick = action)
        }
    }
}

private val PlaceholderSize = 56.dp
