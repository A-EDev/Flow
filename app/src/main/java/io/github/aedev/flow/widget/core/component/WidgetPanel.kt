package io.github.aedev.flow.widget.core.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.layout.RowScope
import io.github.aedev.flow.widget.core.theme.WidgetDimens

/** A titled content widget on Glance's own Scaffold and TitleBar, which carry the launcher's corners. */
@Composable
internal fun WidgetPanel(
    title: String,
    @DrawableRes icon: Int,
    onTitleClick: Action,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(icon),
                title = title,
                iconColor = GlanceTheme.colors.primary,
                textColor = GlanceTheme.colors.onSurface,
                modifier = GlanceModifier.clickable(onTitleClick),
                actions = actions,
            )
        },
        backgroundColor = GlanceTheme.colors.widgetBackground,
        horizontalPadding = WidgetDimens.ContentPadding,
        content = content,
    )
}
