package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object LocalMediaIndex {
    private val page = SettingsDestination.LOCAL_MEDIA

    private fun entry(
        key: String,
        title: Int,
        summary: Int? = null,
    ) = SettingEntry(
        key = "local_media.$key",
        title = title,
        summary = summary,
        section = R.string.settings_local_media_title,
        keywords = R.string.settings_local_media_keywords,
        destination = page,
    )

    val hideAppAudio = entry("hide_app_audio", R.string.local_setting_hide_app_audio, R.string.local_setting_hide_app_audio_desc)
    val minAudio = entry("min_audio", R.string.local_setting_min_audio)
    val hiddenFolders = entry("hidden_folders", R.string.local_setting_hidden_folders)

    val all = listOf(hideAppAudio, minAudio, hiddenFolders)
}
