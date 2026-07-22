package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.platform.AppUiMode

@Composable
fun InterfaceModeDialog(
    selected: AppUiMode,
    onSelected: (AppUiMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interface_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppUiMode.entries.forEach { mode ->
                    val title = when (mode) {
                        AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic)
                        AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile)
                        AppUiMode.TV -> stringResource(R.string.interface_mode_tv)
                    }
                    val summary = when (mode) {
                        AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic_summary)
                        AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile_summary)
                        AppUiMode.TV -> stringResource(R.string.interface_mode_tv_summary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected == mode, onClick = null)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
