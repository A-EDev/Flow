package io.github.aedev.flow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The nine colours a palette is authored with, per light or dark variant, as in Flow Desktop. */
@Immutable
data class PaletteSeed(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
)

/**
 * The thirteen roles one variant of a theme resolves to. The same set Flow Desktop's `ThemeColors`
 * holds, so a custom theme moves between the two apps role for role.
 */
@Immutable
data class PaletteColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color,
)

/** A built-in palette: its Flow Desktop id and its two authored variants. AMOLED derives from dark. */
@Immutable
data class PalettePair(
    val id: String,
    val light: PaletteSeed,
    val dark: PaletteSeed,
) {
    fun colorsFor(variant: ThemeVariant): PaletteColors =
        when (variant) {
            ThemeVariant.LIGHT -> light.expand(variant)
            ThemeVariant.DARK, ThemeVariant.AMOLED -> dark.expand(variant)
        }
}

internal val AmoledBackground = Color(0xFF000000)
internal val AmoledSurface = Color(0xFF080808)
internal val AmoledSurfaceContainerLow = Color(0xFF050505)
internal val AmoledSurfaceContainer = Color(0xFF0C0C0C)
internal val AmoledSurfaceContainerHigh = Color(0xFF141414)
internal val AmoledSurfaceContainerHighest = Color(0xFF1C1C1C)

/**
 * Flow Desktop's `expandPalette`: the container ladder blends the text colour into the surface, so
 * each step always stands apart from the surface and the background whatever the palette's tint.
 * AMOLED keeps the dark accents and text on a fixed near-black ladder.
 */
fun PaletteSeed.expand(variant: ThemeVariant): PaletteColors {
    if (variant == ThemeVariant.AMOLED) {
        return PaletteColors(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            background = AmoledBackground,
            surface = AmoledSurface,
            surfaceContainerLow = AmoledSurfaceContainerLow,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerHigh = AmoledSurfaceContainerHigh,
            surfaceContainerHighest = AmoledSurfaceContainerHighest,
            outline = outline,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
        )
    }
    val dark = variant == ThemeVariant.DARK
    return PaletteColors(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceContainerLow = mixColors(surface, background, if (dark) 0.72f else 0.58f),
        surfaceContainer = surface,
        surfaceContainerHigh = mixColors(onSurface, surface, if (dark) 0.09f else 0.07f),
        surfaceContainerHighest = mixColors(onSurface, surface, if (dark) 0.14f else 0.11f),
        outline = outline,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        error = error,
    )
}

/** CSS `color-mix(in srgb, first amount, second)`: [amount] of [first], the rest of [second]. */
fun mixColors(
    first: Color,
    second: Color,
    amount: Float,
): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = first.red * t + second.red * (1 - t),
        green = first.green * t + second.green * (1 - t),
        blue = first.blue * t + second.blue * (1 - t),
        alpha = 1f,
    )
}

/** Black or white, whichever reads better on [background]. */
internal fun contentColorOn(background: Color): Color = if (background.luminance() > DARK_CONTENT_LUMINANCE) Color.Black else Color.White

private const val DARK_CONTENT_LUMINANCE = 0.179f

/**
 * The full Material 3 scheme for these roles. Roles Flow Desktop has no counterpart for are
 * derived from the thirteen: containers are the accent washed into the surface and carry the
 * surface's own text colour, so they keep the palette's contrast; the strong outline sits between
 * the muted text and the divider colour, so component borders stay visible.
 */
fun PaletteColors.toColorScheme(variant: ThemeVariant): ColorScheme {
    val dark = variant != ThemeVariant.LIGHT
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = mixColors(primary, surface, if (dark) 0.30f else 0.18f),
        onPrimaryContainer = onSurface,
        inversePrimary = mixColors(primary, onSurface, 0.55f),
        secondary = secondary,
        onSecondary = contentColorOn(secondary),
        secondaryContainer = mixColors(secondary, surface, if (dark) 0.26f else 0.16f),
        onSecondaryContainer = onSurface,
        tertiary = secondary,
        onTertiary = contentColorOn(secondary),
        tertiaryContainer = mixColors(secondary, surface, if (dark) 0.20f else 0.12f),
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceContainerHighest,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        error = error,
        onError = contentColorOn(error),
        errorContainer = mixColors(error, surface, if (dark) 0.24f else 0.14f),
        onErrorContainer = onSurface,
        outline = mixColors(onSurfaceVariant, outline, OUTLINE_WEIGHT),
        outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = if (dark) surfaceContainerHighest else background,
        surfaceDim = if (dark) background else surfaceContainerHigh,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}

private const val OUTLINE_WEIGHT = 0.6f
