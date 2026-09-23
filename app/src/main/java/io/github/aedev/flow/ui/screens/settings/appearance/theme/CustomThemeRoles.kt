package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.PaletteColors

/** One editable role of a custom theme: its label, and how to read and replace it. */
internal class ThemeRole(
    val key: String,
    @StringRes val labelRes: Int,
    val read: (PaletteColors) -> Color,
    val write: (PaletteColors, Color) -> PaletteColors,
)

/** A labelled run of related roles on the editor. */
internal class ThemeRoleGroup(
    val key: String,
    @StringRes val titleRes: Int,
    val roles: List<ThemeRole>,
)

/** The thirteen roles Flow Desktop's editor offers, in the same groups a reader looks for them. */
internal val ThemeRoleGroups =
    listOf(
        ThemeRoleGroup(
            "accents",
            R.string.settings_theme_roles_accents,
            listOf(
                ThemeRole("primary", R.string.settings_theme_role_primary, { it.primary }, { c, v -> c.copy(primary = v) }),
                ThemeRole("on_primary", R.string.settings_theme_role_on_primary, { it.onPrimary }, { c, v -> c.copy(onPrimary = v) }),
                ThemeRole("secondary", R.string.settings_theme_role_secondary, { it.secondary }, { c, v -> c.copy(secondary = v) }),
            ),
        ),
        ThemeRoleGroup(
            "surfaces",
            R.string.settings_custom_group_surfaces,
            listOf(
                ThemeRole("background", R.string.settings_theme_role_background, { it.background }, { c, v -> c.copy(background = v) }),
                ThemeRole("surface", R.string.settings_theme_role_surface, { it.surface }, { c, v -> c.copy(surface = v) }),
                ThemeRole(
                    "surface_low",
                    R.string.settings_theme_role_surface_low,
                    { it.surfaceContainerLow },
                    { c, v -> c.copy(surfaceContainerLow = v) },
                ),
                ThemeRole(
                    "surface_container",
                    R.string.settings_theme_role_surface_container,
                    { it.surfaceContainer },
                    { c, v -> c.copy(surfaceContainer = v) },
                ),
                ThemeRole(
                    "surface_high",
                    R.string.settings_theme_role_surface_high,
                    { it.surfaceContainerHigh },
                    { c, v -> c.copy(surfaceContainerHigh = v) },
                ),
                ThemeRole(
                    "surface_highest",
                    R.string.settings_theme_role_surface_highest,
                    { it.surfaceContainerHighest },
                    { c, v -> c.copy(surfaceContainerHighest = v) },
                ),
            ),
        ),
        ThemeRoleGroup(
            "text",
            R.string.settings_theme_roles_text,
            listOf(
                ThemeRole("text", R.string.settings_theme_role_text, { it.onSurface }, { c, v -> c.copy(onSurface = v) }),
                ThemeRole(
                    "muted_text",
                    R.string.settings_theme_role_muted_text,
                    { it.onSurfaceVariant },
                    { c, v -> c.copy(onSurfaceVariant = v) },
                ),
                ThemeRole("outline", R.string.settings_theme_role_outline, { it.outline }, { c, v -> c.copy(outline = v) }),
            ),
        ),
        ThemeRoleGroup(
            "status",
            R.string.settings_theme_roles_status,
            listOf(ThemeRole("error", R.string.settings_theme_role_error, { it.error }, { c, v -> c.copy(error = v) })),
        ),
    )
