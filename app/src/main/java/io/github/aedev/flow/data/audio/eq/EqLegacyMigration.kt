package io.github.aedev.flow.data.audio.eq

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Turns the settings the first equalizer stored (a profile name, a bass boost level, the live
 * "Custom" curve and a map of named presets) into an [EqState].
 *
 * The old processor ignored shelf Q and ran low and high pass as peaks, so migrated bands are
 * rewritten to what was actually heard: shelves at the Butterworth Q, pass filters as peaks.
 */
object EqLegacyMigration {
    const val LEGACY_CUSTOM_PROFILE = "Custom"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LegacyBand(
        val frequency: Double,
        val gain: Double,
        val q: Double = EqLimits.DEFAULT_PEAK_Q,
        val filterType: String = "PK",
        val enabled: Boolean = true,
    )

    @Serializable
    private data class LegacyCurve(
        val preamp: Double = 0.0,
        val bands: List<LegacyBand> = emptyList(),
    )

    fun migrate(
        profileName: String?,
        bassBoost: Float?,
        customCurveJson: String?,
        customPresetsJson: String?,
        myCurveName: String,
        newId: () -> String,
    ): EqState {
        val presets =
            decodePresets(customPresetsJson)
                .filterKeys { it.isNotBlank() && it != LEGACY_CUSTOM_PROFILE && BuiltInEqPresets.byLegacyName(it) == null }
                .map { (name, curve) -> EqPreset(id = newId(), name = normalizedPresetName(name), curve = curve) }
                .toMutableList()
        val customCurve = decodeCurve(customCurveJson)

        val selectedId: String? =
            when {
                profileName == null -> {
                    BuiltInEqPresets.FLAT_ID
                }

                profileName == LEGACY_CUSTOM_PROFILE -> {
                    if (customCurve != null && customCurve.bands.isNotEmpty()) {
                        val name = uniqueName(myCurveName, presets)
                        EqPreset(id = newId(), name = name, curve = customCurve).also { presets += it }.id
                    } else {
                        BuiltInEqPresets.FLAT_ID
                    }
                }

                else -> {
                    BuiltInEqPresets.byLegacyName(profileName)?.id
                        ?: presets.firstOrNull { it.name == profileName }?.id
                        ?: BuiltInEqPresets.FLAT_ID
                }
            }

        val base = EqState(userPresets = presets, bassBoost = (bassBoost ?: 0f).toDouble())
        return base.selectPreset(selectedId ?: BuiltInEqPresets.FLAT_ID).sanitized()
    }

    private fun decodePresets(raw: String?): Map<String, EqCurve> =
        raw
            ?.let {
                runCatching {
                    json.decodeFromString(MapSerializer(String.serializer(), LegacyCurve.serializer()), it)
                }.getOrNull()
            }?.mapValues { (_, curve) -> curve.toCurve() }
            .orEmpty()

    private fun decodeCurve(raw: String?): EqCurve? =
        raw?.let { runCatching { json.decodeFromString(LegacyCurve.serializer(), it) }.getOrNull() }?.toCurve()

    private fun LegacyCurve.toCurve(): EqCurve = EqCurve(preamp = preamp, bands = bands.map { it.toBand() }).sanitized()

    private fun LegacyBand.toBand(): EqBand =
        when (filterType) {
            "LSC" -> EqBand(frequency, gain, EqLimits.DEFAULT_SHELF_Q, EqFilterType.LOW_SHELF, enabled)
            "HSC" -> EqBand(frequency, gain, EqLimits.DEFAULT_SHELF_Q, EqFilterType.HIGH_SHELF, enabled)
            else -> EqBand(frequency, gain, q, EqFilterType.PEAK, enabled)
        }

    private fun uniqueName(
        wanted: String,
        presets: List<EqPreset>,
    ): String {
        if (presets.none { it.name.equals(wanted, ignoreCase = true) }) return wanted
        var suffix = 2
        while (presets.any { it.name.equals("$wanted $suffix", ignoreCase = true) }) suffix++
        return "$wanted $suffix"
    }
}
