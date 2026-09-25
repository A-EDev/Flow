package io.github.aedev.flow.ui.components.equalizer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlinx.coroutines.delay

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 70L
private val RowSpacing = 8.dp

/**
 * A value you can type exactly, nudge with − and +, or sweep with the [slider] underneath (#810).
 * Typed text applies on Done or when focus leaves; text outside [range] shows [rangeText] as an error
 * and keeps the last good value. Holding a stepper repeats it.
 */
@Composable
internal fun EqValueField(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    rangeText: String,
    toText: (Double) -> String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    slider: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable(value) { mutableStateOf(toText(value)) }
    val parsed = parseTypedNumber(text)
    val isError = parsed == null || parsed !in range

    fun commit() {
        if (!isError && parsed != null && parsed != value) onValueChange(parsed) else text = toText(value)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(rangeText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RowSpacing), verticalAlignment = Alignment.CenterVertically) {
            RepeatingStepButton(
                onStep = { onValueChange((value - step).coerceIn(range)) },
                enabled = value > range.start,
                contentDescription = stringResource(R.string.eq_decrease, label),
                increase = false,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = isError,
                suffix = suffix?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        commit()
                        focusManager.clearFocus()
                    }),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) commit() },
            )
            RepeatingStepButton(
                onStep = { onValueChange((value + step).coerceIn(range)) },
                enabled = value < range.endInclusive,
                contentDescription = stringResource(R.string.eq_increase, label),
                increase = true,
            )
        }
        slider?.invoke()
    }
}

@Composable
private fun RepeatingStepButton(
    onStep: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    increase: Boolean,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(REPEAT_DELAY_MS)
        repeated = true
        while (true) {
            step()
            delay(REPEAT_INTERVAL_MS)
        }
    }

    FilledTonalIconButton(
        onClick = { if (repeated) repeated = false else step() },
        enabled = enabled,
        interactionSource = interactions,
    ) {
        Icon(if (increase) Icons.Filled.Add else Icons.Filled.Remove, contentDescription = contentDescription)
    }
}
