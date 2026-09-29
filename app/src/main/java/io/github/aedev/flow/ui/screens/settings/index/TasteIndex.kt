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
    val recap =
        SettingEntry(
            key = "taste.recap",
            title = R.string.recap_title,
            summary = R.string.recap_summary,
            keywords = R.string.recap_keywords,
            destination = page,
        )
    val engineInteractions = entry("engine.interactions", R.string.metric_interactions, R.string.diagnostics_engine_header)
    val engineTopics = entry("engine.topics", R.string.metric_topics, R.string.diagnostics_engine_header)
    val engineChannels = entry("engine.channels", R.string.metric_channels, R.string.diagnostics_engine_header)
    val engineHistory = entry("engine.history", R.string.metric_history, R.string.diagnostics_engine_header)
    val engineFeedMemory = entry("engine.feed_memory", R.string.metric_feed_memory, R.string.diagnostics_engine_header)
    val engineSuppressed = entry("engine.suppressed", R.string.metric_suppressed, R.string.diagnostics_engine_header)
    val engineShortsSeen = entry("engine.shorts_seen", R.string.discovery_shorts_seen, R.string.diagnostics_engine_header)
    val engineQueries = entry("engine.queries", R.string.discovery_query_memory, R.string.diagnostics_engine_header)
    val exportVideo = entry("export_video", R.string.taste_export_video, R.string.taste_data_header)
    val importVideo = entry("import_video", R.string.taste_import_video, R.string.taste_data_header)
    val resetVideo = entry("reset_video", R.string.taste_reset_video, R.string.taste_data_header)
    val exportMusic = entry("export_music", R.string.taste_export_music, R.string.taste_data_header)
    val importMusic = entry("import_music", R.string.taste_import_music, R.string.taste_data_header)
    val resetMusic = entry("reset_music", R.string.taste_reset_music, R.string.taste_data_header)

    val all =
        listOf(
            recap,
            shape,
            interests,
            channels,
            music,
            appetite,
            exportVideo,
            importVideo,
            resetVideo,
            exportMusic,
            importMusic,
            resetMusic,
            engineInteractions,
            engineTopics,
            engineChannels,
            engineHistory,
            engineFeedMemory,
            engineSuppressed,
            engineShortsSeen,
            engineQueries,
        )
}
