package io.github.aedev.flow.ui.screens.settings.integrations

import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorModelConfig
import io.github.aedev.flow.data.sponsordetection.SponsorModelState
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.screens.settings.index.IntegrationsIndex

internal fun SettingsListScope.sponsorModelSection(
    viewModel: IntegrationsViewModel,
    modelState: SponsorModelState,
    onDelete: () -> Unit,
) {
    group(key = "integrations.sponsorblock.on_device.group", header = R.string.sponsor_model_header) {
        switch(IntegrationsIndex.onDevice, viewModel.onDeviceDetection, viewModel::setOnDeviceDetection)
        row("integrations.sponsorblock.on_device.model") { shape ->
            val context = LocalContext.current
            when (modelState) {
                is SponsorModelState.Installed -> {
                    FlowNavRow(
                        title =
                            stringResource(
                                R.string.sponsor_model_status_installed,
                                Formatter.formatFileSize(context, modelState.sizeBytes),
                            ),
                        supportingText = stringResource(R.string.sponsor_model_delete),
                        onClick = onDelete,
                        showChevron = false,
                        shape = shape,
                    )
                }

                is SponsorModelState.Downloading -> {
                    FlowNavRow(
                        title =
                            stringResource(
                                R.string.sponsor_model_downloading,
                                (modelState.downloadedBytes * PERCENT / modelState.totalBytes.coerceAtLeast(1L)).toInt(),
                            ),
                        onClick = {},
                        enabled = false,
                        showChevron = false,
                        shape = shape,
                    )
                }

                SponsorModelState.NotInstalled -> {
                    FlowNavRow(
                        title =
                            stringResource(
                                R.string.sponsor_model_status_missing,
                                Formatter.formatFileSize(context, SponsorModelConfig.TOTAL_BYTES),
                            ),
                        supportingText = stringResource(R.string.sponsor_model_download),
                        onClick = viewModel::downloadSponsorModel,
                        showChevron = false,
                        shape = shape,
                    )
                }

                SponsorModelState.Failed -> {
                    FlowNavRow(
                        title = stringResource(R.string.sponsor_model_failed),
                        supportingText = stringResource(R.string.sponsor_model_retry),
                        onClick = viewModel::downloadSponsorModel,
                        showChevron = false,
                        destructive = true,
                        shape = shape,
                    )
                }
            }
        }
        row("integrations.sponsorblock.on_device.source") { shape ->
            val uriHandler = LocalUriHandler.current
            FlowNavRow(
                title = stringResource(R.string.sponsor_model_source),
                onClick = { runCatching { uriHandler.openUri(SponsorModelConfig.MODEL_PAGE_URL) } },
                shape = shape,
            )
        }
    }
}

private const val PERCENT = 100L
