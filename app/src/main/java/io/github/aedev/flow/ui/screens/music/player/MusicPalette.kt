package io.github.aedev.flow.ui.screens.music.player

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
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Artwork-derived colors shared by the mobile and TV music players. */
@Immutable
data class MusicPaletteColors(
    val base: Color,
    val accent: Color,
    /** Readable ink over [base] (white on dark swatches, near-black on light). */
    val onBase: Color,
)

private val PALETTE_INK_DARK = Color(0xFF161616)


@Composable
fun rememberMusicPalette(thumbnailUrl: String?): MusicPaletteColors {
    val context = LocalContext.current
    var baseSwatch by remember { mutableStateOf<Color?>(null) }
    var accentSwatch by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNullOrEmpty()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .allowHardware(false)
            .size(128)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val palette = Palette.from(result.drawable.toBitmap()).generate()
            val bgSwatch = palette.darkMutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
            val accent = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.lightMutedSwatch
            baseSwatch = bgSwatch?.let { Color(it.rgb) }
            accentSwatch = accent?.let { Color(it.rgb) }
        } else {
            baseSwatch = null
            accentSwatch = null
        }
    }

    val base by animateColorAsState(
        targetValue = baseSwatch ?: MaterialTheme.colorScheme.surface,
        animationSpec = tween(1000),
        label = "musicPaletteBase",
    )
    val accent by animateColorAsState(
        targetValue = accentSwatch ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(1000),
        label = "musicPaletteAccent",
    )
    val onBase = remember(base) {
        if (base.luminance() < 0.45f) Color.White else PALETTE_INK_DARK
    }
    return MusicPaletteColors(base = base, accent = accent, onBase = onBase)
}
