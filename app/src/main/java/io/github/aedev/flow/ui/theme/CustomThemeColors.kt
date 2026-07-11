package io.github.aedev.flow.ui.theme

enum class ThemeVariant {
    LIGHT,
    DARK,
    AMOLED
}

enum class CustomColorRole {
    PRIMARY,
    ON_PRIMARY,
    PRIMARY_CONTAINER,
    ON_PRIMARY_CONTAINER,
    INVERSE_PRIMARY,
    SECONDARY,
    ON_SECONDARY,
    SECONDARY_CONTAINER,
    ON_SECONDARY_CONTAINER,
    TERTIARY,
    ON_TERTIARY,
    TERTIARY_CONTAINER,
    ON_TERTIARY_CONTAINER,
    BACKGROUND,
    ON_BACKGROUND,
    SURFACE,
    ON_SURFACE,
    SURFACE_VARIANT,
    ON_SURFACE_VARIANT,
    SURFACE_TINT,
    INVERSE_SURFACE,
    INVERSE_ON_SURFACE,
    ERROR,
    ON_ERROR,
    ERROR_CONTAINER,
    ON_ERROR_CONTAINER,
    OUTLINE,
    OUTLINE_VARIANT,
    SCRIM,
    SURFACE_BRIGHT,
    SURFACE_DIM,
    SURFACE_CONTAINER_LOWEST,
    SURFACE_CONTAINER_LOW,
    SURFACE_CONTAINER,
    SURFACE_CONTAINER_HIGH,
    SURFACE_CONTAINER_HIGHEST
}

data class CustomThemeColors(
    val values: Map<CustomColorRole, Long>
) {
    fun colorOf(role: CustomColorRole): Long = values[role] ?: defaults(ThemeVariant.DARK).getValue(role)

    fun withColor(role: CustomColorRole, argb: Long): CustomThemeColors =
        copy(values = values + (role to argb))

    val primary get() = colorOf(CustomColorRole.PRIMARY)
    val onPrimary get() = colorOf(CustomColorRole.ON_PRIMARY)
    val secondary get() = colorOf(CustomColorRole.SECONDARY)
    val onSecondary get() = colorOf(CustomColorRole.ON_SECONDARY)
    val tertiary get() = colorOf(CustomColorRole.TERTIARY)
    val onTertiary get() = colorOf(CustomColorRole.ON_TERTIARY)
    val background get() = colorOf(CustomColorRole.BACKGROUND)
    val onBackground get() = colorOf(CustomColorRole.ON_BACKGROUND)
    val surface get() = colorOf(CustomColorRole.SURFACE)
    val onSurface get() = colorOf(CustomColorRole.ON_SURFACE)
    val surfaceVariant get() = colorOf(CustomColorRole.SURFACE_VARIANT)
    val onSurfaceVariant get() = colorOf(CustomColorRole.ON_SURFACE_VARIANT)
    val error get() = colorOf(CustomColorRole.ERROR)
    val onError get() = colorOf(CustomColorRole.ON_ERROR)
    val outline get() = colorOf(CustomColorRole.OUTLINE)
    val scrim get() = colorOf(CustomColorRole.SCRIM)

    companion object {
        fun default(variant: ThemeVariant = ThemeVariant.DARK): CustomThemeColors =
            CustomThemeColors(defaults(variant))

        fun fromLegacy(values: List<Long>): CustomThemeColors {
            val roles = listOf(
                CustomColorRole.PRIMARY,
                CustomColorRole.ON_PRIMARY,
                CustomColorRole.SECONDARY,
                CustomColorRole.ON_SECONDARY,
                CustomColorRole.TERTIARY,
                CustomColorRole.ON_TERTIARY,
                CustomColorRole.BACKGROUND,
                CustomColorRole.ON_BACKGROUND,
                CustomColorRole.SURFACE,
                CustomColorRole.ON_SURFACE,
                CustomColorRole.SURFACE_VARIANT,
                CustomColorRole.ON_SURFACE_VARIANT,
                CustomColorRole.ERROR,
                CustomColorRole.ON_ERROR,
                CustomColorRole.OUTLINE,
                CustomColorRole.SCRIM
            )
            return CustomThemeColors(defaults(ThemeVariant.DARK) + roles.zip(values).toMap())
        }

        private fun defaults(variant: ThemeVariant): Map<CustomColorRole, Long> {
            val dark = variant != ThemeVariant.LIGHT
            val amoled = variant == ThemeVariant.AMOLED
            val background = when {
                amoled -> 0xFF000000
                dark -> 0xFF11131A
                else -> 0xFFFFFBFF
            }
            val surface = when {
                amoled -> 0xFF000000
                dark -> 0xFF1A1D26
                else -> 0xFFFFFBFF
            }
            val onSurface = if (dark) 0xFFE6E8EF else 0xFF1A1B20
            val surfaceVariant = when {
                amoled -> 0xFF111111
                dark -> 0xFF262A35
                else -> 0xFFE1E2EC
            }
            return mapOf(
                CustomColorRole.PRIMARY to if (dark) 0xFF82B1FF else 0xFF335F95,
                CustomColorRole.ON_PRIMARY to if (dark) 0xFF0A1E3D else 0xFFFFFFFF,
                CustomColorRole.PRIMARY_CONTAINER to if (dark) 0xFF164779 else 0xFFD2E4FF,
                CustomColorRole.ON_PRIMARY_CONTAINER to if (dark) 0xFFD2E4FF else 0xFF001C38,
                CustomColorRole.INVERSE_PRIMARY to if (dark) 0xFF335F95 else 0xFFA2C9FF,
                CustomColorRole.SECONDARY to if (dark) 0xFFB39DDB else 0xFF625B71,
                CustomColorRole.ON_SECONDARY to if (dark) 0xFF180B2D else 0xFFFFFFFF,
                CustomColorRole.SECONDARY_CONTAINER to if (dark) 0xFF4A4458 else 0xFFE8DEF8,
                CustomColorRole.ON_SECONDARY_CONTAINER to if (dark) 0xFFE8DEF8 else 0xFF1D192B,
                CustomColorRole.TERTIARY to if (dark) 0xFF80CBC4 else 0xFF006A64,
                CustomColorRole.ON_TERTIARY to if (dark) 0xFF062421 else 0xFFFFFFFF,
                CustomColorRole.TERTIARY_CONTAINER to if (dark) 0xFF00504B else 0xFF9CF2E9,
                CustomColorRole.ON_TERTIARY_CONTAINER to if (dark) 0xFF9CF2E9 else 0xFF00201E,
                CustomColorRole.BACKGROUND to background,
                CustomColorRole.ON_BACKGROUND to onSurface,
                CustomColorRole.SURFACE to surface,
                CustomColorRole.ON_SURFACE to onSurface,
                CustomColorRole.SURFACE_VARIANT to surfaceVariant,
                CustomColorRole.ON_SURFACE_VARIANT to if (dark) 0xFFC2C8D6 else 0xFF45464F,
                CustomColorRole.SURFACE_TINT to if (dark) 0xFF82B1FF else 0xFF335F95,
                CustomColorRole.INVERSE_SURFACE to if (dark) 0xFFE2E2E9 else 0xFF2F3036,
                CustomColorRole.INVERSE_ON_SURFACE to if (dark) 0xFF2F3036 else 0xFFF1F0F7,
                CustomColorRole.ERROR to if (dark) 0xFFFFB4AB else 0xFFBA1A1A,
                CustomColorRole.ON_ERROR to if (dark) 0xFF690005 else 0xFFFFFFFF,
                CustomColorRole.ERROR_CONTAINER to if (dark) 0xFF93000A else 0xFFFFDAD6,
                CustomColorRole.ON_ERROR_CONTAINER to if (dark) 0xFFFFDAD6 else 0xFF410002,
                CustomColorRole.OUTLINE to if (dark) 0xFF8C909F else 0xFF757780,
                CustomColorRole.OUTLINE_VARIANT to if (dark) 0xFF424752 else 0xFFC5C6D0,
                CustomColorRole.SCRIM to 0xCC000000,
                CustomColorRole.SURFACE_BRIGHT to if (dark) 0xFF373940 else 0xFFFFFBFF,
                CustomColorRole.SURFACE_DIM to if (dark) background else 0xFFDAD9E0,
                CustomColorRole.SURFACE_CONTAINER_LOWEST to if (dark) 0xFF000000 else 0xFFFFFFFF,
                CustomColorRole.SURFACE_CONTAINER_LOW to if (amoled) 0xFF080808 else if (dark) 0xFF191B22 else 0xFFF4F3FA,
                CustomColorRole.SURFACE_CONTAINER to if (amoled) 0xFF0D0D0D else if (dark) 0xFF1D1F26 else 0xFFEEEDF4,
                CustomColorRole.SURFACE_CONTAINER_HIGH to if (amoled) 0xFF151515 else if (dark) 0xFF282A30 else 0xFFE8E7EE,
                CustomColorRole.SURFACE_CONTAINER_HIGHEST to if (amoled) 0xFF1C1C1C else if (dark) 0xFF33343B else 0xFFE2E2E9
            )
        }
    }
}

data class CustomThemePalettes(
    val light: CustomThemeColors = CustomThemeColors.default(ThemeVariant.LIGHT),
    val dark: CustomThemeColors = CustomThemeColors.default(ThemeVariant.DARK),
    val amoled: CustomThemeColors = CustomThemeColors.default(ThemeVariant.AMOLED)
) {
    fun forVariant(variant: ThemeVariant): CustomThemeColors = when (variant) {
        ThemeVariant.LIGHT -> light
        ThemeVariant.DARK -> dark
        ThemeVariant.AMOLED -> amoled
    }

    fun withPalette(variant: ThemeVariant, colors: CustomThemeColors): CustomThemePalettes = when (variant) {
        ThemeVariant.LIGHT -> copy(light = colors)
        ThemeVariant.DARK -> copy(dark = colors)
        ThemeVariant.AMOLED -> copy(amoled = colors)
    }
}