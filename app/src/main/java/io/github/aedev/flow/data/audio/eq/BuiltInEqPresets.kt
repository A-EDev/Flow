package io.github.aedev.flow.data.audio.eq

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.EqFilterType.HIGH_SHELF
import io.github.aedev.flow.data.audio.eq.EqFilterType.LOW_SHELF
import io.github.aedev.flow.data.audio.eq.EqFilterType.PEAK

data class BuiltInEqPreset(
    val id: String,
    @StringRes val nameRes: Int,
    val legacyName: String,
    val curve: EqCurve,
)

/**
 * The presets Flow ships, with stable ids. [BuiltInEqPreset.legacyName] is the English name older
 * versions stored as the selection, read once by the migration. Shelves use the Butterworth Q the old
 * processor applied, so each preset sounds as it did.
 */
object BuiltInEqPresets {
    const val FLAT_ID = "builtin:flat"

    private const val SHELF_Q = EqLimits.DEFAULT_SHELF_Q

    val all: List<BuiltInEqPreset> =
        listOf(
            BuiltInEqPreset(FLAT_ID, R.string.eq_preset_flat, "Flat", EqCurve()),
            preset(
                "acoustic",
                R.string.eq_preset_acoustic,
                "Acoustic",
                0.0,
                EqBand(100.0, 2.0, 0.7, PEAK),
                EqBand(400.0, -1.0, 1.0, PEAK),
                EqBand(2_000.0, 1.5, 1.0, PEAK),
                EqBand(6_000.0, 3.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "bass_boost",
                R.string.eq_preset_bass_boost,
                "Bass Boost",
                0.0,
                EqBand(60.0, 9.0, SHELF_Q, LOW_SHELF),
                EqBand(200.0, 3.0, 0.7, PEAK),
            ),
            preset(
                "classical",
                R.string.eq_preset_classical,
                "Classical",
                0.0,
                EqBand(100.0, 3.0, SHELF_Q, LOW_SHELF),
                EqBand(500.0, -2.0, 1.0, PEAK),
                EqBand(2_500.0, 2.0, 1.0, PEAK),
                EqBand(10_000.0, 3.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "electronic",
                R.string.eq_preset_electronic,
                "Electronic",
                -1.0,
                EqBand(50.0, 7.0, SHELF_Q, LOW_SHELF),
                EqBand(400.0, -1.0, 1.0, PEAK),
                EqBand(2_000.0, 2.5, 1.0, PEAK),
                EqBand(8_000.0, 5.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "heavy_metal",
                R.string.eq_preset_heavy_metal,
                "Heavy Metal",
                -1.0,
                EqBand(60.0, 6.0, SHELF_Q, LOW_SHELF),
                EqBand(250.0, 2.0, 1.0, PEAK),
                EqBand(700.0, -3.0, 1.0, PEAK),
                EqBand(2_000.0, 1.0, 1.0, PEAK),
                EqBand(4_000.0, 4.0, 1.0, PEAK),
                EqBand(10_000.0, 5.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "hip_hop",
                R.string.eq_preset_hip_hop,
                "Hip-Hop",
                -1.0,
                EqBand(55.0, 8.0, SHELF_Q, LOW_SHELF),
                EqBand(250.0, 2.0, 1.0, PEAK),
                EqBand(1_000.0, -2.0, 1.0, PEAK),
                EqBand(5_000.0, 3.0, 1.0, PEAK),
            ),
            preset(
                "jazz",
                R.string.eq_preset_jazz,
                "Jazz",
                0.0,
                EqBand(100.0, 2.0, SHELF_Q, LOW_SHELF),
                EqBand(500.0, -1.0, 1.0, PEAK),
                EqBand(2_500.0, 1.5, 1.0, PEAK),
                EqBand(10_000.0, 2.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "metal",
                R.string.eq_preset_metal,
                "Metal",
                0.0,
                EqBand(60.0, 4.0, SHELF_Q, LOW_SHELF),
                EqBand(300.0, 3.0, 1.41, PEAK),
                EqBand(1_500.0, 2.0, 1.0, PEAK),
                EqBand(4_000.0, 4.0, 1.0, PEAK),
            ),
            preset(
                "pop",
                R.string.eq_preset_pop,
                "Pop",
                0.0,
                EqBand(100.0, 4.0, 0.7, PEAK),
                EqBand(2_500.0, 2.5, 1.0, PEAK),
                EqBand(12_000.0, 3.5, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "rnb",
                R.string.eq_preset_rnb,
                "R&B",
                0.0,
                EqBand(70.0, 6.0, SHELF_Q, LOW_SHELF),
                EqBand(300.0, -2.0, 1.0, PEAK),
                EqBand(1_200.0, -1.5, 1.0, PEAK),
                EqBand(4_000.0, 2.0, 1.0, PEAK),
            ),
            preset(
                "rock",
                R.string.eq_preset_rock,
                "Rock",
                0.0,
                EqBand(60.0, 5.0, SHELF_Q, LOW_SHELF),
                EqBand(230.0, 3.0, 1.0, PEAK),
                EqBand(910.0, -2.0, 1.0, PEAK),
                EqBand(3_600.0, 3.5, 1.0, PEAK),
                EqBand(14_000.0, 5.0, SHELF_Q, HIGH_SHELF),
            ),
            preset(
                "vocal",
                R.string.eq_preset_vocal,
                "Vocal / Podcast",
                0.0,
                EqBand(100.0, -2.0, SHELF_Q, LOW_SHELF),
                EqBand(1_000.0, 3.0, 2.0, PEAK),
                EqBand(5_000.0, 2.0, 1.0, PEAK),
            ),
        )

    private val byId = all.associateBy { it.id }
    private val byLegacyName = all.associateBy { it.legacyName }

    fun byId(id: String?): BuiltInEqPreset? = id?.let(byId::get)

    fun byLegacyName(name: String): BuiltInEqPreset? = byLegacyName[name]

    fun isBuiltIn(id: String?): Boolean = id != null && byId.containsKey(id)

    private fun preset(
        key: String,
        @StringRes nameRes: Int,
        legacyName: String,
        preamp: Double,
        vararg bands: EqBand,
    ) = BuiltInEqPreset("builtin:$key", nameRes, legacyName, EqCurve(preamp, bands.toList()))
}
