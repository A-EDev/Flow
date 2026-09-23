package io.github.aedev.flow.ui.screens.settings.appearance

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.util.AppIcons

@StringRes
internal fun themeVariantLabel(variant: ThemeVariant): Int =
    when (variant) {
        ThemeVariant.LIGHT -> R.string.appearance_variant_light
        ThemeVariant.DARK -> R.string.appearance_variant_dark
        ThemeVariant.AMOLED -> R.string.appearance_variant_amoled
    }

/** A launcher icon as the picker shows it: its name and the real adaptive icon it installs. */
internal data class AppIconOption(
    val suffix: String,
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int,
)

private val AppIconOptions =
    mapOf(
        ".IconFlowRed" to AppIconOption(".IconFlowRed", R.string.icon_name_flow_red, R.mipmap.ic_launcher),
        ".IconFlowLight" to AppIconOption(".IconFlowLight", R.string.icon_name_flow_light, R.mipmap.ic_launcher_flow_light),
        ".IconFlowPlay" to AppIconOption(".IconFlowPlay", R.string.icon_name_flow_play, R.mipmap.ic_launcher_flow_play),
        ".IconAmoled" to AppIconOption(".IconAmoled", R.string.icon_name_amoled, R.mipmap.ic_launcher_amoled),
        ".IconMonochrome" to AppIconOption(".IconMonochrome", R.string.icon_name_monochrome, R.mipmap.ic_launcher_monochrome),
        ".IconGhost" to AppIconOption(".IconGhost", R.string.icon_name_ghost, R.mipmap.ic_launcher_ghost),
        ".IconDynamic" to AppIconOption(".IconDynamic", R.string.icon_name_dynamic, R.mipmap.ic_launcher_dynamic),
        ".IconMaterialSky" to AppIconOption(".IconMaterialSky", R.string.icon_name_material_sky, R.mipmap.ic_launcher_material_sky),
        ".IconMaterialMint" to AppIconOption(".IconMaterialMint", R.string.icon_name_material_mint, R.mipmap.ic_launcher_material_mint),
    )

/** Every alias in manifest order; the order and the set come from [AppIcons], never from this file. */
internal val appIconOptions: List<AppIconOption>
    get() = AppIcons.ALL_SUFFIXES.map(::appIconOption)

internal fun appIconOption(suffix: String): AppIconOption = AppIconOptions[suffix] ?: AppIconOptions.getValue(AppIcons.DEFAULT_SUFFIX)
