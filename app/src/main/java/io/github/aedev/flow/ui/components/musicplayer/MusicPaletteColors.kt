package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Artwork-derived colors shared by the mobile and TV music players. */
@Immutable
data class MusicPaletteColors(
    val base: Color,
    val accent: Color,
    /** Readable ink over [base] (white on dark swatches, near-black on light). */
    val onBase: Color,
)

internal val PaletteInkDark = Color(0xFF161616)

/**
 * [animated] eases the swatches in over a second, which is right for the player and wrong for a
 * page that re-derives a whole colour scheme from them: a scheme change recomposes everything
 * under the theme, so pages take the settled colours in one step instead.
 */
@Composable
fun rememberMusicPalette(
    thumbnailUrl: String?,
    animated: Boolean = true,
): MusicPaletteColors {
    val context = LocalContext.current
    var baseSwatch by remember { mutableStateOf<Color?>(null) }
    var accentSwatch by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNullOrEmpty()) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .size(128)
                .build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result is SuccessResult) {
            val palette = withContext(Dispatchers.Default) { Palette.from(result.image.toBitmap()).generate() }
            val bgSwatch =
                palette.darkMutedSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.dominantSwatch
            val accent =
                palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.lightMutedSwatch
            baseSwatch = bgSwatch?.let { Color(it.rgb) }
            accentSwatch = accent?.let { Color(it.rgb) }
        } else {
            baseSwatch = null
            accentSwatch = null
        }
    }

    val baseTarget = baseSwatch ?: MaterialTheme.colorScheme.surface
    val accentTarget = accentSwatch ?: MaterialTheme.colorScheme.primary
    val base =
        if (animated) {
            animateColorAsState(
                targetValue = baseTarget,
                animationSpec = tween(1000),
                label = "musicPaletteBase",
            ).value
        } else {
            baseTarget
        }
    val accent =
        if (animated) {
            animateColorAsState(
                targetValue = accentTarget,
                animationSpec = tween(1000),
                label = "musicPaletteAccent",
            ).value
        } else {
            accentTarget
        }
    val onBase =
        remember(base) {
            if (base.luminance() < 0.45f) Color.White else PaletteInkDark
        }
    return MusicPaletteColors(base = base, accent = accent, onBase = onBase)
}
