package io.github.aedev.flow.ui.screens.settings.appearance

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.util.AppIcons
import io.github.aedev.flow.util.LauncherIcon

@StringRes
internal fun themeVariantLabel(variant: ThemeVariant): Int =
    when (variant) {
        ThemeVariant.LIGHT -> R.string.appearance_variant_light
        ThemeVariant.DARK -> R.string.appearance_variant_dark
        ThemeVariant.AMOLED -> R.string.appearance_variant_amoled
    }

/** Every alias in manifest order; the order and the set come from [AppIcons], never from this file. */
internal val appIconOptions: List<LauncherIcon>
    get() = AppIcons.ALL

internal fun appIconOption(suffix: String): LauncherIcon = AppIcons.icon(suffix)
