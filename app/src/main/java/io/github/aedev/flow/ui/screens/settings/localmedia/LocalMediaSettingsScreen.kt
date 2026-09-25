package io.github.aedev.flow.ui.screens.settings.localmedia

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaSettings
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.choice
import io.github.aedev.flow.ui.components.settings.info
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.shared.FlowChoice
import io.github.aedev.flow.ui.components.shared.FlowChoiceDialog
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.screens.settings.index.LocalMediaIndex

/** What Local media leaves out: voice notes and recordings, short audio, and folders the viewer hid. */
@Composable
internal fun LocalMediaSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: LocalMediaSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenFolders.collectAsStateWithLifecycle()
    var pickingMinimum by rememberSaveable { mutableStateOf(false) }
    val noHiddenFolders = stringResource(R.string.local_setting_no_hidden_folders)

    SettingsPage(
        title = stringResource(R.string.settings_local_media_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = "local_media.rules", header = R.string.settings_local_media_title) {
            switch(LocalMediaIndex.hideAppAudio, settings.hideAppAudio, viewModel::setHideAppAudio, icon = Icons.Outlined.MicOff)
            choice(LocalMediaIndex.minAudio, onClick = { pickingMinimum = true }, icon = Icons.Outlined.Timer) {
                minimumLabel(settings.minAudioSeconds)
            }
        }
        group(key = "local_media.folders", header = R.string.local_setting_hidden_folders) {
            if (hidden.isEmpty()) {
                info(LocalMediaIndex.hiddenFolders, value = noHiddenFolders)
            } else {
                hidden.forEach { folder ->
                    row("local_media.folder.${folder.id}") { shape ->
                        FlowNavRow(
                            title = folder.name,
                            onClick = { viewModel.showFolder(folder.id) },
                            leadingIcon = Icons.Outlined.FolderOff,
                            shape = shape,
                            trailingContent = {
                                TextButton(
                                    onClick = { viewModel.showFolder(folder.id) },
                                ) { Text(stringResource(R.string.local_setting_show_folder)) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (pickingMinimum) {
        FlowChoiceDialog(
            title = stringResource(R.string.local_setting_min_audio),
            options = LocalMediaSettings.MIN_AUDIO_CHOICES.map { FlowChoice(it, minimumLabel(it)) },
            selected = settings.minAudioSeconds,
            onSelect = viewModel::setMinAudioSeconds,
            onDismiss = { pickingMinimum = false },
        )
    }
}

@Composable
private fun minimumLabel(seconds: Int): String =
    if (seconds <= 0) {
        stringResource(R.string.local_setting_min_audio_off)
    } else {
        pluralStringResource(R.plurals.local_setting_seconds, seconds, seconds)
    }
