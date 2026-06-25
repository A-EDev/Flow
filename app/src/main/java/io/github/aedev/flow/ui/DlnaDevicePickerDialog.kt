package io.github.aedev.flow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.player.dlna.DlnaDevice

/** DLNA / UPnP device-picker dialog shown when the cast button is pressed. */
@Composable
internal fun DlnaDevicePickerDialog(
    devices: List<DlnaDevice>,
    isDiscovering: Boolean,
    isCasting: Boolean,
    videoTitle: String,
    onDeviceSelected: (DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isCasting) "Casting to TV" else "Cast to Device")
                if (isDiscovering) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        },
        text = {
            Column {
                if (!isCasting && devices.isEmpty() && !isDiscovering) {
                    Text(
                        text = "No DLNA/UPnP renderers found on this network.\n\n" +
                            "Make sure your TV or media player (VLC, Kodi, etc.) is on the " +
                            "same Wi-Fi network and has media renderer mode enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (!isCasting && devices.isEmpty()) {
                    Text(
                        text = "Searching for DLNA devices…",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (isCasting) {
                    Text(
                        text = "Now casting: $videoTitle",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn {
                        items(devices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(device) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = device.friendlyName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCasting) {
                TextButton(onClick = onStopCasting) { Text("Stop Casting") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
