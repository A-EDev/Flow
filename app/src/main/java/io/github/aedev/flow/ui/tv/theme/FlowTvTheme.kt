package io.github.aedev.flow.ui.tv.theme

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.aedev.flow.ui.components.shared.LocalDpadNavigation

/**
 * The TV layer over [io.github.aedev.flow.ui.theme.FlowTheme]. Colours, type, shapes and motion stay
 * the app's own: Google's TV type roles at 960×540 dp are the mobile M3 sizes.
 *
 * What it adds is focus. M3's inset focus ring turns on for every component that draws a ripple, so
 * the mobile cards, rows, chips and buttons show TV focus without knowing about TV, and
 * [LocalDpadNavigation] lets shared items lift on focus and keep one focus stop each.
 */
@Composable
fun FlowTvTheme(content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRing =
        remember(colorScheme.onSurface, colorScheme.surface) {
            RippleConfiguration(
                focus = RippleConfiguration.Focus.InsetRing(colorScheme.onSurface, colorScheme.surface),
            )
        }
    CompositionLocalProvider(
        LocalRippleThemeConfiguration provides RippleDefaults.InsetFocusRingThemeConfiguration,
        LocalRippleConfiguration provides focusRing,
        LocalDpadNavigation provides true,
        LocalTvDimens provides TvDimens(),
        content = content,
    )
}
