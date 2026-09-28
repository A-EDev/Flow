package io.github.aedev.flow.widget.core.component

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.innerCorners

/** Artwork or a thumbnail on the launcher's inner radius, with a tonal placeholder until it loads. */
@Composable
internal fun WidgetArtwork(
    bitmap: Bitmap?,
    @DrawableRes placeholderIcon: Int,
    modifier: GlanceModifier,
    contentDescription: String? = null,
    badge: String? = null,
    badgeAlignment: Alignment = Alignment.BottomEnd,
) {
    Box(modifier = modifier.innerCorners(), contentAlignment = badgeAlignment) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = contentDescription,
                modifier = GlanceModifier.fillMaxSize().innerCorners(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surfaceVariant).innerCorners(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(placeholderIcon),
                    contentDescription = contentDescription,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
        if (badge != null) {
            Box(modifier = GlanceModifier.padding(4.dp)) { WidgetBadge(badge) }
        }
    }
}

/** A duration or kind label over a thumbnail, on the inverse surface like the app's media badges. */
@Composable
internal fun WidgetBadge(
    text: String,
    emphasized: Boolean = false,
) {
    Box(
        modifier =
            GlanceModifier
                .background(if (emphasized) GlanceTheme.colors.primary else GlanceTheme.colors.inverseSurface)
                .cornerRadius(WidgetDimens.BadgeCorner)
                .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = WidgetText.labelSmall(if (emphasized) GlanceTheme.colors.onPrimary else GlanceTheme.colors.inverseOnSurface),
            maxLines = 1,
        )
    }
}
