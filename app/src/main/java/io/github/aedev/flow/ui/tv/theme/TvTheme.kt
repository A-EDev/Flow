package io.github.aedev.flow.ui.tv.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val TvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * TV layer over [io.github.aedev.flow.ui.theme.FlowTheme]: keeps the already-resolved
 * color scheme (all theme modes work unchanged) and swaps in the ten-foot type scale,
 * larger shapes, and TV layout tokens.
 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvDimens provides TvDimens()) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = TvTypography,
            shapes = TvShapes,
            content = content,
        )
    }
}
