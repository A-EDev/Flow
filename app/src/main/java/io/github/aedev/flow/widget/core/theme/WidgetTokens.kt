package io.github.aedev.flow.widget.core.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aedev.flow.ui.theme.Typography
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight

internal object WidgetDimens {
    val TouchTarget = 48.dp
    val ContentPadding = 12.dp
    val ItemGap = 8.dp
    val SmallGap = 4.dp
    val BadgeCorner = 4.dp
    val ProgressHeight = 4.dp

    // Launchers before Android 12 have no inner radius, and RemoteViews cannot clip there anyway.
    val InnerCornerFallback = 12.dp
}

/** The app's own type scale for widgets. Glance draws only system families, which is what the app uses. */
internal object WidgetText {
    @Composable
    fun headline(color: ColorProvider = GlanceTheme.colors.onSurface) = Typography.headlineLargeEmphasized.toGlance(color)

    @Composable
    fun titleMedium(color: ColorProvider = GlanceTheme.colors.onSurface) = Typography.titleMedium.toGlance(color)

    @Composable
    fun titleSmall(color: ColorProvider = GlanceTheme.colors.onSurface) = Typography.titleSmall.toGlance(color)

    @Composable
    fun bodyMedium(color: ColorProvider = GlanceTheme.colors.onSurface) = Typography.bodyMedium.toGlance(color)

    @Composable
    fun bodySmall(color: ColorProvider = GlanceTheme.colors.onSurfaceVariant) = Typography.bodySmall.toGlance(color)

    @Composable
    fun labelMedium(color: ColorProvider = GlanceTheme.colors.onSurfaceVariant) = Typography.labelMedium.toGlance(color)

    @Composable
    fun labelSmall(color: ColorProvider) = Typography.labelSmall.toGlance(color)

    fun centered(style: TextStyle) = style.copy(textAlign = TextAlign.Center)

    private fun ComposeTextStyle.toGlance(color: ColorProvider) =
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight =
                when {
                    (fontWeight ?: ComposeFontWeight.Normal) >= ComposeFontWeight.SemiBold -> FontWeight.Bold
                    (fontWeight ?: ComposeFontWeight.Normal) >= ComposeFontWeight.Medium -> FontWeight.Medium
                    else -> FontWeight.Normal
                },
        )
}

/** The launcher's inner corner radius for content inside a widget. */
internal fun GlanceModifier.innerCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    } else {
        this
    }

/** Below Android 12 nothing clips an image, so its corners are rounded into the bitmap instead. */
internal fun bakedCornerRadiusPx(context: Context): Float =
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.S
    ) {
        0f
    } else {
        WidgetDimens.InnerCornerFallback.value * context.resources.displayMetrics.density
    }

internal fun Context.dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
