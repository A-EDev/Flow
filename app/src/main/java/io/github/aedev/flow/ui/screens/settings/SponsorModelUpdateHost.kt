package io.github.aedev.flow.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorModelConfig
import io.github.aedev.flow.data.sponsordetection.SponsorModelState

@Composable
internal fun SponsorModelUpdateHost(
    visible: Boolean,
    viewModel: SponsorModelViewModel = hiltViewModel(),
) {
    val state by viewModel.modelState.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable(SponsorModelConfig.MODEL_VERSION) { mutableStateOf(false) }
    if (visible && !dismissed && state == SponsorModelState.UpdateAvailable) {
        SponsorModelUpdateDialog(
            onDownload = {
                dismissed = true
                viewModel.download()
            },
            onDismiss = { dismissed = true },
        )
    }
}

@Composable
internal fun SponsorModelUpdateDialog(
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sponsor_model_update_title)) },
        text = {
            Text(stringResource(R.string.sponsor_model_update_body, Formatter.formatFileSize(context, SponsorModelConfig.TOTAL_BYTES)))
        },
        confirmButton = {
            TextButton(onClick = onDownload) { Text(stringResource(R.string.sponsor_model_update_download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sponsor_model_update_later)) }
        },
    )
}
