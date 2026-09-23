package io.github.aedev.flow.data.local

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.FlowPalettes
import io.github.aedev.flow.ui.theme.PaletteColors
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.mixColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Flow Desktop's `ThemeColors`, field for field. Colours are `#rrggbb`, or a `color-mix(in srgb, …)`
 * expression, which is how desktop stores the derived surface steps of a theme cloned from a preset.
 */
@Serializable
data class DesktopThemeColors(
    val primary: String,
    val onPrimary: String,
    val secondary: String,
    val background: String,
    val surface: String,
    val surfaceContainerLow: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val surfaceContainerHighest: String,
    val outline: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val error: String,
)

/** Flow Desktop's `CustomThemeDefinition`: the shape both apps store, export and import. */
@Serializable
data class DesktopCustomTheme(
    val id: String,
    val name: String,
    val custom: Boolean = false,
    val variants: Map<String, DesktopThemeColors> = emptyMap(),
)

/**
 * Reads and writes custom themes in Flow Desktop's format, so a theme exported from either app
 * imports into the other. Validation matches desktop's `parseCustomThemes`: an id starting with
 * `custom-`, a name of 1 to 48 characters, `custom: true`, and every role in all three styles.
 */
object CustomThemeCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    private val variantKeys = mapOf(ThemeVariant.LIGHT to "light", ThemeVariant.DARK to "dark", ThemeVariant.AMOLED to "amoled")

    fun encodeList(themes: List<CustomTheme>): String =
        json.encodeToString(JsonArray.serializer(), JsonArray(themes.map { json.encodeToJsonElement(it.toDesktop()) }))

    fun encodeOne(theme: CustomTheme): String = json.encodeToString(DesktopCustomTheme.serializer(), theme.toDesktop())

    /** The valid themes in [raw], which may hold one theme or a list of them; at most [CustomTheme.MAX_COUNT]. */
    fun decode(raw: String?): List<CustomTheme> {
        if (raw.isNullOrBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val candidates: List<JsonElement> =
            when (element) {
                is JsonArray -> element
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
        return candidates
            .mapNotNull { candidate ->
                runCatching { json.decodeFromJsonElement<DesktopCustomTheme>(candidate) }.getOrNull()?.toCustomTheme()
            }.distinctBy { it.id }
            .take(CustomTheme.MAX_COUNT)
    }

    private fun CustomTheme.toDesktop(): DesktopCustomTheme =
        DesktopCustomTheme(
            id = id,
            name = name,
            custom = true,
            variants = variantKeys.entries.associate { (variant, key) -> key to colorsFor(variant).toDesktop() },
        )

    private fun DesktopCustomTheme.toCustomTheme(): CustomTheme? {
        val trimmed = name.trim()
        if (!id.startsWith(CustomTheme.ID_PREFIX) || !custom) return null
        if (trimmed.isEmpty() || name.length > CustomTheme.MAX_NAME_LENGTH) return null
        val light = variants[variantKeys.getValue(ThemeVariant.LIGHT)]?.toPalette() ?: return null
        val dark = variants[variantKeys.getValue(ThemeVariant.DARK)]?.toPalette() ?: return null
        val amoled = variants[variantKeys.getValue(ThemeVariant.AMOLED)]?.toPalette() ?: return null
        return CustomTheme(id = id, name = trimmed, light = light, dark = dark, amoled = amoled)
    }

    private fun PaletteColors.toDesktop(): DesktopThemeColors =
        DesktopThemeColors(
            primary = primary.toHex(),
            onPrimary = onPrimary.toHex(),
            secondary = secondary.toHex(),
            background = background.toHex(),
            surface = surface.toHex(),
            surfaceContainerLow = surfaceContainerLow.toHex(),
            surfaceContainer = surfaceContainer.toHex(),
            surfaceContainerHigh = surfaceContainerHigh.toHex(),
            surfaceContainerHighest = surfaceContainerHighest.toHex(),
            outline = outline.toHex(),
            onSurface = onSurface.toHex(),
            onSurfaceVariant = onSurfaceVariant.toHex(),
            error = error.toHex(),
        )

    private fun DesktopThemeColors.toPalette(): PaletteColors? =
        PaletteColors(
            primary = parseThemeColor(primary) ?: return null,
            onPrimary = parseThemeColor(onPrimary) ?: return null,
            secondary = parseThemeColor(secondary) ?: return null,
            background = parseThemeColor(background) ?: return null,
            surface = parseThemeColor(surface) ?: return null,
            surfaceContainerLow = parseThemeColor(surfaceContainerLow) ?: return null,
            surfaceContainer = parseThemeColor(surfaceContainer) ?: return null,
            surfaceContainerHigh = parseThemeColor(surfaceContainerHigh) ?: return null,
            surfaceContainerHighest = parseThemeColor(surfaceContainerHighest) ?: return null,
            outline = parseThemeColor(outline) ?: return null,
            onSurface = parseThemeColor(onSurface) ?: return null,
            onSurfaceVariant = parseThemeColor(onSurfaceVariant) ?: return null,
            error = parseThemeColor(error) ?: return null,
        )
}

private val HexColor = Regex("""^#([0-9a-fA-F]{6})$""")
private val ColorMix = Regex("""^color-mix\(\s*in\s+srgb\s*,\s*(.+?)\s*,\s*(.+?)\s*\)$""", RegexOption.IGNORE_CASE)
private val MixStop = Regex("""^(.+?)(?:\s+(\d+(?:\.\d+)?)%)?$""")

/**
 * A colour as Flow Desktop writes one: `#rrggbb`, or `color-mix(in srgb, A p%, B)` with the
 * percentage on either side, as CSS allows. Anything else is rejected, as desktop rejects it.
 */
internal fun parseThemeColor(value: String): Color? {
    val text = value.trim()
    HexColor.matchEntire(text)?.let { return Color(0xFF000000 or it.groupValues[1].toLong(16)) }
    val mix = ColorMix.matchEntire(text) ?: return null
    val first = MixStop.matchEntire(mix.groupValues[1]) ?: return null
    val second = MixStop.matchEntire(mix.groupValues[2]) ?: return null
    val firstColor = parseThemeColor(first.groupValues[1]) ?: return null
    val secondColor = parseThemeColor(second.groupValues[1]) ?: return null
    val firstShare = first.groupValues[2].toFloatOrNull()
    val secondShare = second.groupValues[2].toFloatOrNull()
    val amount =
        when {
            firstShare != null -> firstShare / PERCENT
            secondShare != null -> 1f - secondShare / PERCENT
            else -> HALF
        }
    return mixColors(firstColor, secondColor, amount)
}

private const val PERCENT = 100f
private const val HALF = 0.5f

/** `#rrggbb`, the only form Flow Desktop's validator accepts. */
internal fun Color.toHex(): String = "#%06x".format(toArgb() and 0xFFFFFF)

/**
 * The single custom palette older versions kept per style, keyed by Material role name, turned into
 * a named theme. The thirteen roles both apps share carry over; the divider colour older versions
 * called OUTLINE_VARIANT is what desktop calls outline. Anything missing falls back to Flow Default.
 */
internal fun legacyCustomTheme(
    stored: Map<ThemeVariant, Map<String, Long>>,
    id: String,
    name: String,
): CustomTheme {
    fun palette(variant: ThemeVariant): PaletteColors {
        val fallback = FlowPalettes.default.colorsFor(variant)
        val roles = stored[variant].orEmpty()

        fun role(
            key: String,
            default: Color,
        ): Color = roles[key]?.let { Color(it) } ?: default
        return PaletteColors(
            primary = role("PRIMARY", fallback.primary),
            onPrimary = role("ON_PRIMARY", fallback.onPrimary),
            secondary = role("SECONDARY", fallback.secondary),
            background = role("BACKGROUND", fallback.background),
            surface = role("SURFACE", fallback.surface),
            surfaceContainerLow = role("SURFACE_CONTAINER_LOW", fallback.surfaceContainerLow),
            surfaceContainer = role("SURFACE_CONTAINER", fallback.surfaceContainer),
            surfaceContainerHigh = role("SURFACE_CONTAINER_HIGH", fallback.surfaceContainerHigh),
            surfaceContainerHighest = role("SURFACE_CONTAINER_HIGHEST", fallback.surfaceContainerHighest),
            outline = role("OUTLINE_VARIANT", fallback.outline),
            onSurface = role("ON_SURFACE", fallback.onSurface),
            onSurfaceVariant = role("ON_SURFACE_VARIANT", fallback.onSurfaceVariant),
            error = role("ERROR", fallback.error),
        )
    }
    return CustomTheme(
        id = id,
        name = name,
        light = palette(ThemeVariant.LIGHT),
        dark = palette(ThemeVariant.DARK),
        amoled = palette(ThemeVariant.AMOLED),
    )
}

@Serializable
private data class LegacyStoredPalettes(
    val light: LegacyStoredPalette? = null,
    val dark: LegacyStoredPalette? = null,
    val amoled: LegacyStoredPalette? = null,
)

@Serializable
private data class LegacyStoredPalette(
    val values: Map<String, Long> = emptyMap(),
)

/** Roles in the comma-separated form the first custom theme editor wrote, dark style only. */
private val LegacyCsvRoles =
    listOf(
        "PRIMARY",
        "ON_PRIMARY",
        "SECONDARY",
        "ON_SECONDARY",
        "TERTIARY",
        "ON_TERTIARY",
        "BACKGROUND",
        "ON_BACKGROUND",
        "SURFACE",
        "ON_SURFACE",
        "SURFACE_VARIANT",
        "ON_SURFACE_VARIANT",
        "ERROR",
        "ON_ERROR",
        "OUTLINE",
        "SCRIM",
    )

private val legacyJson = Json { ignoreUnknownKeys = true }

/**
 * The roles an older version stored for its one custom palette: the per-style JSON of
 * `custom_theme_palettes_v2`, or the older comma-separated `custom_theme_colors`. Null when neither
 * holds anything, so a user who never made a custom theme gets none.
 */
internal fun decodeLegacyCustomPalettes(
    raw: String?,
    legacyCsv: String?,
): Map<ThemeVariant, Map<String, Long>>? {
    if (!raw.isNullOrBlank()) {
        runCatching { legacyJson.decodeFromString(LegacyStoredPalettes.serializer(), raw) }.getOrNull()?.let { stored ->
            return mapOf(
                ThemeVariant.LIGHT to stored.light?.values.orEmpty(),
                ThemeVariant.DARK to stored.dark?.values.orEmpty(),
                ThemeVariant.AMOLED to stored.amoled?.values.orEmpty(),
            )
        }
    }
    val values = legacyCsv?.split(',')?.mapNotNull(String::toLongOrNull)?.takeIf { it.size == LegacyCsvRoles.size } ?: return null
    return mapOf(ThemeVariant.DARK to LegacyCsvRoles.zip(values).toMap())
}
