package io.github.aedev.flow.ui.components.equalizer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.BuiltInEqPresets
import io.github.aedev.flow.data.audio.eq.EqFilterType
import io.github.aedev.flow.data.audio.eq.EqMode
import io.github.aedev.flow.data.audio.eq.EqState
import io.github.aedev.flow.data.audio.eq.GraphicEq
import io.github.aedev.flow.data.audio.eq.userPreset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MINUS = '−'
private const val KILO = 1_000.0

@get:StringRes
internal val EqFilterType.labelRes: Int
    get() =
        when (this) {
            EqFilterType.PEAK -> R.string.eq_type_peak
            EqFilterType.LOW_SHELF -> R.string.eq_type_low_shelf
            EqFilterType.HIGH_SHELF -> R.string.eq_type_high_shelf
            EqFilterType.LOW_PASS -> R.string.eq_type_low_pass
            EqFilterType.HIGH_PASS -> R.string.eq_type_high_pass
        }

@get:DrawableRes
internal val EqFilterType.iconRes: Int
    get() =
        when (this) {
            EqFilterType.PEAK -> R.drawable.ic_eq_peak
            EqFilterType.LOW_SHELF -> R.drawable.ic_eq_low_shelf
            EqFilterType.HIGH_SHELF -> R.drawable.ic_eq_high_shelf
            EqFilterType.LOW_PASS -> R.drawable.ic_eq_low_pass
            EqFilterType.HIGH_PASS -> R.drawable.ic_eq_high_pass
        }

/** 910 Hz, 3 kHz, 3.6 kHz. */
@Composable
internal fun formatFrequency(hz: Double): String =
    if (hz >= KILO) {
        val khz = hz / KILO
        val text = if (abs(khz - khz.roundToInt()) < 0.05) khz.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", khz)
        "$text ${stringResource(R.string.eq_unit_khz)}"
    } else {
        "${hz.roundToInt()} ${stringResource(R.string.eq_unit_hz)}"
    }

/** Graph axis labels: 100, 1k, 10k. */
internal fun formatFrequencyShort(hz: Double): String = if (hz >= KILO) "${(hz / KILO).roundToInt()}k" else hz.roundToInt().toString()

/** +9.0 dB, −2.5 dB, with a true minus sign. */
@Composable
internal fun formatGain(db: Double): String = "${signedNumber(db, 1)} ${stringResource(R.string.eq_unit_db)}"

internal fun signedNumber(
    value: Double,
    decimals: Int,
): String {
    val magnitude = String.format(Locale.getDefault(), "%.${decimals}f", abs(value))
    return when {
        magnitude.all { it == '0' || !it.isDigit() } -> magnitude
        value > 0 -> "+$magnitude"
        else -> "$MINUS$magnitude"
    }
}

internal fun formatQ(q: Double): String = String.format(Locale.getDefault(), "%.2f", q)

/** Parses what a person typed: a comma or a point as the decimal mark, a true or hyphen minus. */
internal fun parseTypedNumber(text: String): Double? =
    text
        .trim()
        .replace(MINUS, '-')
        .removePrefix("+")
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }

/** The name the header shows for whatever the active mode is playing. */
@Composable
internal fun activePresetName(state: EqState): String {
    val id = state.active.presetId
    BuiltInEqPresets.byId(id)?.let { return stringResource(it.nameRes) }
    state.userPreset(id)?.let { return it.name }
    val flat =
        if (state.mode == EqMode.PARAMETRIC) {
            state.active.curve.bands
                .isEmpty()
        } else {
            state.active.curve == GraphicEq.flatCurve()
        }
    return stringResource(if (flat) R.string.eq_preset_flat else R.string.eq_not_saved)
}
