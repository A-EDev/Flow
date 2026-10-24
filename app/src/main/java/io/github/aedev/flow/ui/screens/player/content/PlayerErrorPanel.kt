package io.github.aedev.flow.ui.screens.player.content

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.openInYouTube

private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

/** [onResetSession] is null unless a fresh YouTube session may get past the error. */
@Composable
internal fun PlayerErrorPanel(
    errorHint: String?,
    videoId: String,
    context: Context,
    onRetryClick: () -> Unit,
    onResetSession: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!errorHint.isNullOrBlank()) {
                Text(
                    text = errorHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
            if (onResetSession != null) {
                Button(
                    onClick = onResetSession,
                    colors = errorButtonColors(),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = ButtonPadding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PanelButtonContent(Icons.Rounded.RestartAlt, stringResource(R.string.diagnostics_reset_session), FontWeight.SemiBold)
                }
            }
            // Row 1: Retry + Copy Logs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onResetSession == null) {
                    Button(
                        onClick = onRetryClick,
                        colors = errorButtonColors(),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = ButtonPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        PanelButtonContent(Icons.Rounded.Refresh, stringResource(R.string.retry), FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRetryClick,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = ButtonPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        PanelButtonContent(Icons.Rounded.Refresh, stringResource(R.string.retry))
                    }
                }
                OutlinedButton(
                    onClick = {
                        val ok = PlayerDiagnostics.copyToClipboard(context)
                        Toast
                            .makeText(
                                context,
                                if (ok) context.getString(R.string.logs_copied) else context.getString(R.string.logs_copy_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = ButtonPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    PanelButtonContent(Icons.Rounded.ContentCopy, stringResource(R.string.copy_logs))
                }
            }
            // Row 2: Open in YouTube (full width)
            OutlinedButton(
                onClick = { openInYouTube(context, videoId) },
                shape = MaterialTheme.shapes.small,
                contentPadding = ButtonPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PanelButtonContent(Icons.Rounded.OpenInBrowser, stringResource(R.string.ui_open_in_youtube))
            }
        }
    }
}

@Composable
private fun errorButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

@Composable
private fun RowScope.PanelButtonContent(
    icon: ImageVector,
    label: String,
    fontWeight: FontWeight? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = fontWeight)
}
