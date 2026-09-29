package io.github.aedev.flow.ui.components.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.flowActionShape

private val CardPadding = 16.dp
private val CardSpacing = 16.dp
private val BadgeSize = 48.dp
private val BadgeIcon = 24.dp

/**
 * The way into Flow Recap. [readyLabel] names a month that just closed with a recap waiting, which
 * turns the card into an invitation with its own open and dismiss actions.
 */
@Composable
fun RecapEntryCard(
    readyLabel: String?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = readyLabel != null
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(CardSpacing / 2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(BadgeSize), shape = flowActionShape(), color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Insights, contentDescription = null, modifier = Modifier.size(BadgeIcon))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = readyLabel?.let { stringResource(R.string.recap_ready_title, it) } ?: stringResource(R.string.recap_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(if (ready) R.string.recap_ready_body else R.string.recap_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!ready) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
            if (ready) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CardSpacing / 2, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.recap_ready_dismiss)) }
                    Button(onClick = onOpen) { Text(stringResource(R.string.recap_ready_open)) }
                }
            }
        }
    }
}
