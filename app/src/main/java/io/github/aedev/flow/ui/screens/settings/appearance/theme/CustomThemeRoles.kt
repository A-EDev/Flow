package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.CustomColorRole

/** A labelled run of related colour roles on the custom theme page. */
internal data class CustomRoleGroup(
    val key: String,
    @StringRes val titleRes: Int,
    val roles: List<Pair<CustomColorRole, Int>>,
)

internal val CustomRoleGroups =
    listOf(
        CustomRoleGroup(
            "primary",
            R.string.appearance_role_primary,
            listOf(
                CustomColorRole.PRIMARY to R.string.appearance_role_primary,
                CustomColorRole.ON_PRIMARY to R.string.appearance_role_on_primary,
                CustomColorRole.PRIMARY_CONTAINER to R.string.appearance_role_primary_container,
                CustomColorRole.ON_PRIMARY_CONTAINER to R.string.appearance_role_on_primary_container,
                CustomColorRole.INVERSE_PRIMARY to R.string.appearance_role_inverse_primary,
            ),
        ),
        CustomRoleGroup(
            "secondary",
            R.string.appearance_role_secondary,
            listOf(
                CustomColorRole.SECONDARY to R.string.appearance_role_secondary,
                CustomColorRole.ON_SECONDARY to R.string.appearance_role_on_secondary,
                CustomColorRole.SECONDARY_CONTAINER to R.string.appearance_role_secondary_container,
                CustomColorRole.ON_SECONDARY_CONTAINER to R.string.appearance_role_on_secondary_container,
            ),
        ),
        CustomRoleGroup(
            "tertiary",
            R.string.appearance_role_tertiary,
            listOf(
                CustomColorRole.TERTIARY to R.string.appearance_role_tertiary,
                CustomColorRole.ON_TERTIARY to R.string.appearance_role_on_tertiary,
                CustomColorRole.TERTIARY_CONTAINER to R.string.appearance_role_tertiary_container,
                CustomColorRole.ON_TERTIARY_CONTAINER to R.string.appearance_role_on_tertiary_container,
            ),
        ),
        CustomRoleGroup(
            "surfaces",
            R.string.settings_custom_group_surfaces,
            listOf(
                CustomColorRole.BACKGROUND to R.string.appearance_role_background,
                CustomColorRole.ON_BACKGROUND to R.string.appearance_role_on_background,
                CustomColorRole.SURFACE to R.string.appearance_role_surface,
                CustomColorRole.ON_SURFACE to R.string.appearance_role_on_surface,
                CustomColorRole.SURFACE_VARIANT to R.string.appearance_role_surface_variant,
                CustomColorRole.ON_SURFACE_VARIANT to R.string.appearance_role_on_surface_variant,
                CustomColorRole.SURFACE_TINT to R.string.appearance_role_surface_tint,
                CustomColorRole.INVERSE_SURFACE to R.string.appearance_role_inverse_surface,
                CustomColorRole.INVERSE_ON_SURFACE to R.string.appearance_role_inverse_on_surface,
                CustomColorRole.SURFACE_BRIGHT to R.string.appearance_role_surface_bright,
                CustomColorRole.SURFACE_DIM to R.string.appearance_role_surface_dim,
                CustomColorRole.SURFACE_CONTAINER_LOWEST to R.string.appearance_role_surface_container_lowest,
                CustomColorRole.SURFACE_CONTAINER_LOW to R.string.appearance_role_surface_container_low,
                CustomColorRole.SURFACE_CONTAINER to R.string.appearance_role_surface_container,
                CustomColorRole.SURFACE_CONTAINER_HIGH to R.string.appearance_role_surface_container_high,
                CustomColorRole.SURFACE_CONTAINER_HIGHEST to R.string.appearance_role_surface_container_highest,
            ),
        ),
        CustomRoleGroup(
            "error",
            R.string.appearance_role_error,
            listOf(
                CustomColorRole.ERROR to R.string.appearance_role_error,
                CustomColorRole.ON_ERROR to R.string.appearance_role_on_error,
                CustomColorRole.ERROR_CONTAINER to R.string.appearance_role_error_container,
                CustomColorRole.ON_ERROR_CONTAINER to R.string.appearance_role_on_error_container,
            ),
        ),
        CustomRoleGroup(
            "outline",
            R.string.settings_custom_group_outline,
            listOf(
                CustomColorRole.OUTLINE to R.string.appearance_role_outline,
                CustomColorRole.OUTLINE_VARIANT to R.string.appearance_role_outline_variant,
                CustomColorRole.SCRIM to R.string.appearance_role_scrim,
            ),
        ),
    )
