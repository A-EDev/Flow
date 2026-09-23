package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object TasteIndex {
    private val page = SettingsDestination.TASTE

    private fun entry(
        key: String,
        title: Int,
        section: Int? = null,
    ) = SettingEntry(key = "taste.$key", title = title, destination = page, keywords = R.string.taste_keywords, section = section)

    val shape = entry("shape", R.string.taste_shape_header)
    val interests = entry("interests", R.string.taste_interests_header)
    val channels = entry("channels", R.string.taste_channels_header)
    val music = entry("music", R.string.taste_music_header)
    val appetite = entry("appetite", R.string.music_discovery_appetite, R.string.taste_music_header)
    val hidden = entry("hidden", R.string.taste_hidden_title)
    val exportVideo = entry("export_video", R.string.taste_export_video, R.string.taste_data_header)
    val importVideo = entry("import_video", R.string.taste_import_video, R.string.taste_data_header)
    val resetVideo = entry("reset_video", R.string.taste_reset_video, R.string.taste_data_header)
    val exportMusic = entry("export_music", R.string.taste_export_music, R.string.taste_data_header)
    val importMusic = entry("import_music", R.string.taste_import_music, R.string.taste_data_header)
    val resetMusic = entry("reset_music", R.string.taste_reset_music, R.string.taste_data_header)

    val all =
        listOf(shape, interests, channels, music, appetite, exportVideo, importVideo, resetVideo, exportMusic, importMusic, resetMusic)
}
