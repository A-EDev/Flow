package io.github.aedev.flow.widget.core.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.image.ShapeDecor
import io.github.aedev.flow.widget.core.image.WidgetShape
import io.github.aedev.flow.widget.core.theme.WidgetDimens

/** Shuffle on the Cookie12 action shape, the one the app gives shuffle and radio. */
@Composable
internal fun WidgetShuffleButton(onClick: Action) {
    Box(
        modifier = GlanceModifier.size(WidgetDimens.TouchTarget).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        ShapeDecor(WidgetShape.COOKIE_12, GlanceTheme.colors.primary, 44.dp)
        Image(
            provider = ImageProvider(R.drawable.ic_shuffle),
            contentDescription = LocalContext.current.getString(R.string.widget_shuffle),
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
    }
}
