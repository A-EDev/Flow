package io.github.aedev.flow.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass

/**
 * The window's size class, computed once at the root from the container the app actually draws in,
 * so split-screen and freeform windows are classified by what they can show rather than by the
 * display they sit on. Readers below the root take it from here instead of re-deriving it from
 * [android.content.res.Configuration].
 */
val LocalWindowSizeClass = staticCompositionLocalOf { WindowSizeClass(0, 0) }

@Composable
fun ProvideWindowSizeClass(content: @Composable () -> Unit) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowSizeClass =
        remember(containerSize, density) {
            with(density) {
                WindowSizeClass.compute(containerSize.width.toDp().value, containerSize.height.toDp().value)
            }
        }
    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass, content = content)
}

val WindowSizeClass.isMediumWidth: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isExpandedWidth: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

val WindowSizeClass.isMediumHeight: Boolean
    get() = isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
