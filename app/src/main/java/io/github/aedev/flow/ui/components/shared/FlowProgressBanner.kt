package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val BannerPadding = 16.dp
private val BannerSpacing = 8.dp

/**
 * Progress of long work that keeps running away from this screen, such as an import. A known
 * [total] shows a determinate bar and an "x / y" count; zero shows an indeterminate bar.
 */
@Composable
fun FlowProgressBanner(
    headline: String,
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(BannerPadding), verticalArrangement = Arrangement.spacedBy(BannerSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (total > 0) {
                    Text(
                        text = stringResource(R.string.sync_progress_fraction, current, total),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (total > 0) {
                LinearProgressIndicator(progress = { current.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
