package io.github.aedev.flow.ui.components.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.EqBand
import io.github.aedev.flow.data.audio.eq.EqFilterMath
import io.github.aedev.flow.data.audio.eq.EqFilterType
import io.github.aedev.flow.data.audio.eq.EqLimits
import io.github.aedev.flow.data.audio.eq.roundToDecimals
import io.github.aedev.flow.ui.components.shared.connectedButtonShapes
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val SectionSpacing = 16.dp
private val MiniGraphHeight = 120.dp
private const val FREQUENCY_STEP = 1.0
private const val GAIN_STEP = 0.1
private const val Q_STEP = 0.01

/**
 * Everything about one band: where it sits on the curve, its filter type, and frequency, gain and
 * Q as typed fields with steppers and sliders. Frequency and Q slide on log scales, as they are heard.
 */
@Composable
internal fun EqBandEditor(
    index: Int,
    bands: List<EqBand>,
    onChange: (EqBand) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    bassBoost: Double = 0.0,
    showGraph: Boolean = true,
) {
    val band = bands.getOrNull(index) ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqBandBadge(number = index + 1, highlighted = true)
            Text(
                text = stringResource(R.string.eq_band_title, index + 1),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.eq_remove_band))
            }
        }
        if (showGraph) {
            EqResponseGraph(
                bands = bands,
                bassBoost = bassBoost,
                selectedIndex = index,
                showLabels = false,
                modifier = Modifier.fillMaxWidth().height(MiniGraphHeight),
            )
        }
        EqFilterTypeGroup(
            selected = band.type,
            onSelected = { type ->
                onChange(band.copy(type = type, q = if (type == band.type) band.q else EqLimits.defaultQ(type)))
            },
        )
        EqValueField(
            label = stringResource(R.string.eq_frequency),
            value = band.frequency,
            range = EqLimits.MIN_FREQUENCY..EqLimits.MAX_FREQUENCY,
            step = FREQUENCY_STEP,
            rangeText = stringResource(R.string.eq_frequency_range),
            toText = { it.roundToInt().toString() },
            suffix = stringResource(R.string.eq_unit_hz),
            onValueChange = { onChange(band.copy(frequency = it.roundToInt().toDouble())) },
            slider = {
                Slider(
                    value = EqFilterMath.fractionOf(band.frequency).toFloat(),
                    onValueChange = { onChange(band.copy(frequency = EqFilterMath.frequencyAt(it.toDouble()).roundToInt().toDouble())) },
                )
            },
        )
        if (band.type.hasGain) {
            EqValueField(
                label = stringResource(R.string.eq_gain),
                value = band.gain,
                range = EqLimits.MIN_GAIN..EqLimits.MAX_GAIN,
                step = GAIN_STEP,
                rangeText = stringResource(R.string.eq_gain_range),
                toText = { String.format(Locale.getDefault(), "%.1f", it) },
                suffix = stringResource(R.string.eq_unit_db),
                onValueChange = { onChange(band.copy(gain = it.roundToDecimals(1))) },
                slider = {
                    Slider(
                        value = band.gain.toFloat(),
                        onValueChange = { onChange(band.copy(gain = it.toDouble().roundToDecimals(1))) },
                        valueRange = EqLimits.MIN_GAIN.toFloat()..EqLimits.MAX_GAIN.toFloat(),
                    )
                },
            )
        }
        EqValueField(
            label = stringResource(R.string.eq_q),
            value = band.q,
            range = EqLimits.MIN_Q..EqLimits.MAX_Q,
            step = Q_STEP,
            rangeText = stringResource(R.string.eq_q_range),
            toText = { formatQ(it) },
            onValueChange = { onChange(band.copy(q = it.roundToDecimals(2))) },
            slider = {
                Slider(
                    value = qToFraction(band.q),
                    onValueChange = { onChange(band.copy(q = fractionToQ(it).roundToDecimals(2))) },
                )
            },
        )
    }
}

/** The five filter shapes as one connected group of icon toggles; the chosen type is named below. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EqFilterTypeGroup(
    selected: EqFilterType,
    onSelected: (EqFilterType) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val types = EqFilterType.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            types.forEachIndexed { index, type ->
                val name = stringResource(type.labelRes)
                ToggleButton(
                    checked = type == selected,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelected(type)
                    },
                    shapes = connectedButtonShapes(index = index, count = types.size),
                    modifier = Modifier.weight(1f).semantics { contentDescription = name },
                ) {
                    Icon(painter = painterResource(type.iconRes), contentDescription = null)
                }
            }
        }
        Text(
            text = stringResource(selected.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val Q_LOG_SPAN = ln(EqLimits.MAX_Q / EqLimits.MIN_Q)

private fun qToFraction(q: Double): Float = (ln(q.coerceIn(EqLimits.MIN_Q, EqLimits.MAX_Q) / EqLimits.MIN_Q) / Q_LOG_SPAN).toFloat()

private fun fractionToQ(fraction: Float): Double = EqLimits.MIN_Q * (EqLimits.MAX_Q / EqLimits.MIN_Q).pow(fraction.toDouble())
