package io.github.aedev.flow.ui.screens.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.utils.DeviceDetails
import io.github.aedev.flow.utils.DeviceFactGroup
import io.github.aedev.flow.utils.copyPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ContentMaxHeight = 440.dp
private val LoadingHeight = 160.dp
private val GroupSpacing = 20.dp
private val FactSpacing = 6.dp
private val LabelValueSpacing = 16.dp

/** The device, system, display, decoders and app build, grouped and copyable as text. */
@Composable
internal fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.about_device_info)
    val groups by produceState<List<DeviceFactGroup>?>(null) {
        value = withContext(Dispatchers.IO) { DeviceDetails.collect(context) }
    }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val loaded = groups
            if (loaded == null) {
                Box(Modifier.fillMaxWidth().height(LoadingHeight), contentAlignment = Alignment.Center) { FlowLoadingIndicator() }
            } else {
                SelectionContainer(Modifier.heightIn(max = ContentMaxHeight).verticalScroll(rememberScrollState())) {
                    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
                        loaded.forEach { group -> FactGroup(group) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(
                enabled = groups != null,
                onClick = {
                    val loaded = groups ?: return@TextButton
                    scope.launch { clipboard.copyPlainText(title, DeviceDetails.asText(context, loaded)) }
                },
            ) { Text(stringResource(R.string.btn_copy)) }
        },
    )
}

@Composable
private fun FactGroup(group: DeviceFactGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(FactSpacing)) {
        Text(
            text = stringResource(group.title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        group.facts.forEach { fact ->
            Row(horizontalArrangement = Arrangement.spacedBy(LabelValueSpacing)) {
                Text(
                    text = fact.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = fact.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
