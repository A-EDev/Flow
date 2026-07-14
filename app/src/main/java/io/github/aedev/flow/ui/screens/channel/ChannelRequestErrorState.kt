package io.github.aedev.flow.ui.screens.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

@Composable
internal fun ChannelRequestErrorState(
    message: String,
    errorLog: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.retry))
            }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val copied = runCatching {
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.channel_error_log_clip_label),
                                errorLog,
                            )
                        ) ?: error("Clipboard service unavailable")
                    }.isSuccess
                    Toast.makeText(
                        context,
                        context.getString(
                            if (copied) R.string.logs_copied else R.string.logs_copy_failed
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.copy_logs))
            }
        }
    }
}
