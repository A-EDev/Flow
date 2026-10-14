package io.github.aedev.flow.ui.screens.settings.about

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NOTICES_ASSET = "licenses/notices.txt"
private val ContentMaxHeight = 440.dp
private val LoadingHeight = 160.dp

/** The licences of the word lists and code Flow ships from other projects. */
@Composable
internal fun NoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState<String?>(null) {
        value =
            withContext(Dispatchers.IO) {
                context.assets
                    .open(NOTICES_ASSET)
                    .bufferedReader()
                    .use { it.readText() }
            }
    }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_notices)) },
        text = {
            val loaded = notices
            if (loaded == null) {
                Box(Modifier.fillMaxWidth().height(LoadingHeight), contentAlignment = Alignment.Center) { FlowLoadingIndicator() }
            } else {
                SelectionContainer(Modifier.heightIn(max = ContentMaxHeight).verticalScroll(rememberScrollState())) {
                    Text(text = loaded, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
