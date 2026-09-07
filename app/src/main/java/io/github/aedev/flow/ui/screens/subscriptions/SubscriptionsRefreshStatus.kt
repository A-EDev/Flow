package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val StatusHorizontalPadding = 12.dp
private val ProgressVerticalPadding = 4.dp
private val TextVerticalPadding = 2.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubscriptionsRefreshStatus(
    processedChannels: Int,
    totalChannels: Int,
    lastRefreshText: String?,
    lastRefreshVideoCount: Int,
    showLastRefreshVideoCount: Boolean,
    modifier: Modifier = Modifier,
) {
    if (totalChannels > 0) {
        val progress = processedChannels.toFloat() / totalChannels.toFloat().coerceAtLeast(1f)
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = StatusHorizontalPadding, vertical = ProgressVerticalPadding),
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.subscriptions_refresh_progress_template,
                    totalChannels,
                    processedChannels,
                    totalChannels,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = StatusHorizontalPadding, vertical = TextVerticalPadding),
        )
    } else if (lastRefreshText != null) {
        Text(
            text =
                if (showLastRefreshVideoCount) {
                    pluralStringResource(
                        R.plurals.subscriptions_last_refreshed_template,
                        lastRefreshVideoCount,
                        lastRefreshText,
                        lastRefreshVideoCount,
                    )
                } else {
                    stringResource(R.string.subscriptions_last_refreshed_time_only_template, lastRefreshText)
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = StatusHorizontalPadding, vertical = TextVerticalPadding),
        )
    }
}
