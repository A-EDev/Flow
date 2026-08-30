package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/** How many channel names are spelled out before the rest are summarised as a count. */
private const val MAX_NAMED_CHANNELS = 3

/**
 * Reports channels the last refresh could not reach.
 *
 * Without this a dead or region-blocked channel just quietly contributes nothing, and the feed
 * looks complete while it is not.
 */
@Composable
fun SubscriptionFeedErrorCard(
    failedChannelNames: List<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberFlowReduceMotion()
    var displayedChannelNames by remember { mutableStateOf(failedChannelNames) }

    LaunchedEffect(failedChannelNames) {
        if (failedChannelNames.isNotEmpty()) {
            displayedChannelNames = failedChannelNames
        }
    }

    AnimatedVisibility(
        visible = failedChannelNames.isNotEmpty(),
        modifier = modifier,
        enter =
            fadeIn(
                tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion),
                    easing = FlowMotion.EnterEasing,
                ),
            ) +
                expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec =
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.CONTENT_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.EnterEasing,
                        ),
                ),
        exit =
            fadeOut(
                tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                    easing = FlowMotion.ExitEasing,
                ),
            ) +
                shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec =
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.ExitEasing,
                        ),
                ),
        label = "subscriptionFeedError",
    ) {
        val names = if (failedChannelNames.isNotEmpty()) failedChannelNames else displayedChannelNames
        val named = names.take(MAX_NAMED_CHANNELS).joinToString(", ")
        val remaining = names.size - MAX_NAMED_CHANNELS
        val channelSummary =
            if (remaining > 0) {
                stringResource(R.string.subscriptions_failed_channels_more, named, remaining)
            } else {
                named
            }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
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
                                count = names.size,
                                names.size,
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
}
