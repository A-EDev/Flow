package io.github.aedev.flow.data.local

import io.github.aedev.flow.ui.theme.CustomColorRole
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeVariant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk form of [CustomThemePalettes], written and read through generated serializers.
 *
 * Roles are stored by name instead of as the enum itself for two reasons. A reflective reader has
 * to recover `Map<CustomColorRole, Long>` from the field's generic signature, which R8 is free to
 * strip from a class no keep rule names — it then reads the map raw and hands back
 * String keys, so every custom colour silently missed its lookup and fell back to a default, and
 * anything that touched a key crashed. And a role that no longer exists now drops out of the
 * palette rather than failing the whole decode.
 */
@Serializable
private data class StoredPalettes(
    val light: StoredPalette? = null,
    val dark: StoredPalette? = null,
    val amoled: StoredPalette? = null,
)

@Serializable
private data class StoredPalette(
    val values: Map<String, Long> = emptyMap(),
)

private val paletteJson = Json { ignoreUnknownKeys = true }

internal fun encodeCustomThemePalettes(palettes: CustomThemePalettes): String =
    paletteJson.encodeToString(
        StoredPalettes.serializer(),
        StoredPalettes(
            light = palettes.light.stored(),
            dark = palettes.dark.stored(),
            amoled = palettes.amoled.stored(),
        ),
    )

internal fun decodeCustomThemePalettes(
    raw: String?,
    legacyRaw: String?,
): CustomThemePalettes {
    if (!raw.isNullOrBlank()) {
        runCatching { paletteJson.decodeFromString(StoredPalettes.serializer(), raw) }
            .getOrNull()
            ?.let { stored ->
                return CustomThemePalettes(
                    light = stored.light.restored(ThemeVariant.LIGHT),
                    dark = stored.dark.restored(ThemeVariant.DARK),
                    amoled = stored.amoled.restored(ThemeVariant.AMOLED),
                )
            }
    }
    val legacyValues =
        legacyRaw
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf { it.size == 16 }
    return if (legacyValues != null) {
        CustomThemePalettes(dark = CustomThemeColors.fromLegacy(legacyValues))
    } else {
        CustomThemePalettes()
    }
}

private fun CustomThemeColors.stored(): StoredPalette = StoredPalette(values.entries.associate { (role, argb) -> role.name to argb })

private fun StoredPalette?.restored(variant: ThemeVariant): CustomThemeColors =
    if (this == null) {
        CustomThemeColors.default(variant)
    } else {
        CustomThemeColors(
            values.entries
                .mapNotNull { (name, argb) ->
                    runCatching { CustomColorRole.valueOf(name) }.getOrNull()?.let { it to argb }
                }.toMap(),
        )
    }
