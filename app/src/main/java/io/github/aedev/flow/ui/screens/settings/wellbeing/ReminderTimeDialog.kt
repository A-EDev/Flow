package io.github.aedev.flow.ui.screens.settings.wellbeing

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import java.time.LocalTime

/** The Material time picker for a reminder, with its dial and keyboard modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(
    title: String,
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val mode = if (keyboard) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker

    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(onDisplayModeChange = { keyboard = !keyboard }, displayMode = mode)
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        if (keyboard) TimeInput(state = state) else TimePicker(state = state)
    }
}
