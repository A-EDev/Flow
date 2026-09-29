package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.FlowDiagnostics
import io.github.aedev.flow.utils.copyPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many channel names are spelled out before the rest are summarised as a count. */
private const val MAX_NAMED_CHANNELS = 3

/**
 * Reports channels the last refresh could not reach.
 *
 * Without this a dead or region-blocked channel just quietly contributes nothing, and the feed
 * looks complete while it is not.
 */
@Composable
internal fun SubscriptionFeedErrorCard(
    failedChannelNames: List<String>,
    failedChannelIds: Set<String>,
    failedChannelReasons: Map<String, String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (failedChannelNames.isEmpty()) return

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val named = failedChannelNames.take(MAX_NAMED_CHANNELS).joinToString(", ")
    val remaining = failedChannelNames.size - MAX_NAMED_CHANNELS
    val channelSummary =
        if (remaining > 0) {
            stringResource(R.string.subscriptions_failed_channels_more, named, remaining)
        } else {
            named
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text =
                        pluralStringResource(
                            id = R.plurals.subscriptions_failed_channels_title,
                            count = failedChannelNames.size,
                            failedChannelNames.size,
                        ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.subscriptions_failed_channels_body, channelSummary),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 32.dp, end = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val report =
                                withContext(Dispatchers.IO) {
                                    buildSubscriptionFailureReport(
                                        deviceInfo = FlowDiagnostics.buildDeviceInfo(context),
                                        failedChannelNames = failedChannelNames,
                                        failedChannelIds = failedChannelIds,
                                        failedChannelReasons = failedChannelReasons,
                                        sessionLogs =
                                            FlowDiagnostics.readSessionLogs(SUBSCRIPTION_FAILURE_LOG_LINES),
                                    )
                                }
                            clipboard.copyPlainText(
                                label = context.getString(R.string.subscriptions_failed_channels_copy_logs),
                                text = report,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.subscriptions_failed_channels_copy_logs))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
