package io.github.aedev.flow.widget.core.theme

import android.content.Context
import androidx.glance.color.ColorProviders
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.ui.components.musicplayer.common.paletteColorScheme
import io.github.aedev.flow.ui.components.shared.mediaPaletteOf
import io.github.aedev.flow.widget.core.image.WidgetImageLoader

/**
 * The music player's own artwork scheme, for player widgets while the player is set to take its
 * colours from the cover. Null keeps the app theme: the Default style, or no cover to read.
 */
internal suspend fun artworkColorProviders(
    context: Context,
    artworkUrl: String?,
    style: MusicPlayerBackgroundStyle,
    appColors: ColorProviders,
): ColorProviders? {
    if (style == MusicPlayerBackgroundStyle.DEFAULT) return null
    val bitmap = WidgetImageLoader.load(context, artworkUrl, PALETTE_SAMPLE_PX) ?: return null
    val palette = mediaPaletteOf(bitmap, appColors.surface.getColor(context), appColors.primary.getColor(context))
    val scheme = paletteColorScheme(palette, appColors.error.getColor(context))
    return androidx.glance.material3.ColorProviders(scheme)
}

// The same sample size the in-app player reads its palette from.
private const val PALETTE_SAMPLE_PX = 128
